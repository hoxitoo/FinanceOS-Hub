package com.financeos.hub.core.analytics

import com.financeos.hub.core.parser.MerchantNames
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Поиск настоящих подписок — по ПРОДАВЦУ, а не по категории.
 *
 * Прошлая версия считала подпиской любую категорию, встретившуюся в 3 месяцах из 6, и складывала
 * её целиком. На экране это выглядело так: «Продукты ~7 050 ₽ в месяц», «Букмекер ~24 433 ₽ в
 * месяц», «Другое ~14 955 ₽ в месяц». Формально верно — продукты действительно покупают каждый
 * месяц. По смыслу бесполезно: подписка это не «категория, в которой есть траты», а КОНКРЕТНОЕ
 * списание, которое повторяется само, примерно в тот же день и примерно на ту же сумму.
 *
 * Поэтому здесь два независимых признака, и оба про одного продавца:
 *  - **регулярность** — три и больше списаний, ровный интервал (неделя / месяц / квартал / год) и
 *    стабильная сумма;
 *  - **ваша пометка** — операция лежит в категории «Подписки». Это прямое утверждение человека, и
 *    оно сильнее любой эвристики: одного списания достаточно, чтобы показать строку.
 *
 * Валюты НИКОГДА не смешиваются. Раньше 19,99 $ складывались с рублями как 19,99 ₽ — подписка на
 * ChatGPT уменьшала итог вместо того, чтобы его увеличивать.
 */
object SubscriptionDetector {

    /** Одно списание. Узкий тип вместо `TransactionEntity` — так эту логику можно проверить. */
    data class Charge(
        val timestamp    : Long,
        val amountKopecks: Long,      // модуль, уже без знака
        val currency     : String,
        val merchant     : String?,
        val description  : String?,
        val categoryId   : String?,
    )

    /** Как часто списывают. Раз в год — тоже подписка, просто платят её редко и больно. */
    enum class Period(val days: Int, val perYear: Int, val label: String) {
        Weekly   (7,   52, "раз в неделю"),
        Monthly  (30,  12, "раз в месяц"),
        Quarterly(91,   4, "раз в квартал"),
        Yearly   (365,  1, "раз в год"),
    }

    /** Почему строка попала в список — пользователь имеет право это знать. */
    enum class Evidence {
        /** Само повторяется: ровный интервал и стабильная сумма. */
        Regular,
        /** Вы отнесли операцию к категории «Подписки». */
        Labelled,
    }

    data class Subscription(
        val key             : String,
        val title           : String,
        val currency        : String,
        /** Сколько списывают за раз. */
        val typicalKopecks  : Long,
        /** Та же сумма, приведённая к месяцу: годовую делим на 12, недельную умножаем. */
        val monthlyKopecks  : Long,
        /** null — период неизвестен (списание пока одно, но вы пометили его подпиской). */
        val period          : Period?,
        val chargeCount     : Int,
        val lastChargeAt    : Long,
        /** Когда ждать следующего списания. null, если период неизвестен. */
        val nextExpectedAt  : Long?,
        /** Списание просрочено больше чем на половину периода — возможно, подписку отменили. */
        val isMissed        : Boolean,
        val evidence        : Evidence,
        val categoryId      : String?,
    )

    const val SUBSCRIPTION_CATEGORY = "cat_subscription"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Минимум списаний, чтобы говорить о регулярности: по двум точкам интервал не проверить. */
    private const val MIN_CHARGES_FOR_RHYTHM = 3

    /** Насколько сумма может гулять и всё ещё считаться той же подпиской. Цены поднимают. */
    private const val AMOUNT_TOLERANCE = 0.25

    /** Насколько интервал может гулять: месяц это и 28, и 31 день, плюс выходные банка. */
    private const val PERIOD_TOLERANCE = 0.30

    /**
     * @param charges только РАСХОДЫ за интересующий период, суммы по модулю.
     * @param now точка отсчёта для «просрочено» и «когда ждать следующего».
     */
    fun detect(charges: List<Charge>, now: Long): List<Subscription> {
        if (charges.isEmpty()) return emptyList()

        return charges
            .mapNotNull { charge -> groupKey(charge)?.let { it to charge } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, group) -> analyse(key, group, now) }
            .sortedWith(compareByDescending<Subscription> { it.evidence == Evidence.Labelled }
                .thenByDescending { it.monthlyKopecks })
    }

    /**
     * Ключ группировки: продавец + валюта. Разные валюты — разные строки даже у одного сервиса:
     * складывать их нельзя, а показывать «19,99» без указания чего именно — врать.
     */
    private fun groupKey(charge: Charge): Pair<String, String>? {
        val name = MerchantNames.groupKey(charge.merchant ?: charge.description) ?: return null
        return name to charge.currency
    }

    private fun analyse(
        key  : Pair<String, String>,
        group: List<Charge>,
        now  : Long,
    ): Subscription? {
        val (name, currency) = key
        val sorted  = group.sortedBy { it.timestamp }
        val amounts = sorted.map { it.amountKopecks }
        val typical = median(amounts)
        if (typical <= 0L) return null

        val labelled = group.any { it.categoryId == SUBSCRIPTION_CATEGORY }
        val stable   = amounts.all { abs(it - typical).toDouble() <= typical * AMOUNT_TOLERANCE }
        val period   = if (sorted.size >= MIN_CHARGES_FOR_RHYTHM && stable) detectPeriod(sorted) else null

        // Регулярность нашлась — это подписка независимо от категории. Категория «Подписки» —
        // самостоятельный повод показать строку, даже когда списание пока одно: так сказал человек.
        if (period == null && !labelled) return null

        val last = sorted.last().timestamp
        // Без известного периода считаем помесячно: у подписки это подавляющий случай, а экран
        // рядом пишет, что период ещё не установлен.
        val effective = period ?: Period.Monthly
        val monthly   = (typical.toDouble() * effective.perYear / 12.0).roundToLong()
        val nextAt    = if (period != null) last + period.days * DAY_MS else null

        return Subscription(
            key            = "$name|$currency",
            title          = displayTitle(group),
            currency       = currency,
            typicalKopecks = typical,
            monthlyKopecks = monthly,
            period         = period,
            chargeCount    = sorted.size,
            lastChargeAt   = last,
            nextExpectedAt = nextAt,
            // Просрочка объявляется только когда период известен: иначе это гадание.
            isMissed       = period != null && now - last > period.days * DAY_MS * 1.5,
            evidence       = if (period != null) Evidence.Regular else Evidence.Labelled,
            // Из самого свежего списания, а не из произвольного элемента неотсортированной
            // группы: по этой категории строится переход в список операций.
            categoryId     = sorted.lastOrNull { it.categoryId != null }?.categoryId,
        )
    }

    /** Показываем название так, как его прислал банк в последний раз — оно узнаваемо. */
    private fun displayTitle(group: List<Charge>): String =
        group.maxByOrNull { it.timestamp }
            ?.let { MerchantNames.display(it.merchant ?: it.description) }
            ?: "Без названия"

    /**
     * Ритм по медиане промежутков. Медиана, а не среднее: одно пропущенное списание сдвигает
     * среднее вдвое и превращает месячную подписку в квартальную, а медиану — не трогает.
     */
    private fun detectPeriod(sorted: List<Charge>): Period? {
        val gapsDays = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp) / DAY_MS }
            .filter { it > 0 }
        if (gapsDays.isEmpty()) return null
        val medianGap = median(gapsDays)

        return Period.entries.firstOrNull { p ->
            abs(medianGap - p.days).toDouble() <= p.days * PERIOD_TOLERANCE
        }
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
