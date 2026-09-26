package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.MerchantNames
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * «Всего потрачено, всего заработано» — за годы, а не за месяц.
 *
 * Отдельно от [AnalyticsEngine], и не из педантизма: тот отвечает на вопросы этого месяца, и все его
 * окна — дни и недели. Здесь вопрос другой — куда ушли деньги за десять лет, — и шаг другой: месяц,
 * полгода, год. Смешать их значило бы тащить в месячную аналитику всю историю, а в годовую — дневную
 * дробность, которую на таком масштабе глаз не различает.
 *
 * Правила, общие для всего экрана:
 * - **Переводы не считаются** ни тратой, ни заработком. Перекладывание между своими счетами денег не
 *   тратит и не приносит; посчитай их — «заработано» выросло бы на каждое пополнение копилки.
 * - **Валюты не складываются** (курса у офлайн-приложения нет). Итоги — по каждой валюте отдельно;
 *   графики, бары, доли и источники — в ОСНОВНОЙ валюте ([primaryCurrency]): рубль, если он есть,
 *   иначе валюта с наибольшим оборотом. Жёсткий рубль оставил бы человеку с одними сомами (МБанк)
 *   плитку «0 ₽ / 0 ₽» и экран без единого графика. Прочие валюты не пропадают — они в итогах.
 * - **Трата без категории — это «Другое»** (`cat_other`), как на вкладке «Категории». Иначе одни и
 *   те же деньги на двух экранах лежали бы в разных долях.
 * - Всё считается из одного прохода по операциям, поэтому новая операция сразу меняет все цифры
 *   экрана — «постоянное суммирование», а не пересчёт раз в сутки.
 */
object LifetimeStats {

    const val BASE_CURRENCY = "RUB"

    /** Насколько назад смотреть. Скользящее окно от сегодняшнего дня, а не календарные годы. */
    enum class Horizon(val years: Long?, val label: String) {
        YEAR(1, "Год"),
        TWO_YEARS(2, "2 года"),
        TEN_YEARS(10, "10 лет"),
        TWENTY_YEARS(20, "20 лет"),
        ALL(null, "Всё время"),
    }

    /** Шаг «ползущего» графика. Корзины выровнены по календарю — полугодие это январь и июль. */
    enum class Step(val months: Int, val label: String) {
        MONTH(1, "Месяц"),
        HALF_YEAR(6, "Полгода"),
        YEAR(12, "Год"),
        TWO_YEARS(24, "2 года"),
    }

    /** Одна операция в том виде, который нужен расчёту. */
    data class Entry(
        val timestamp    : Long,
        /** Знаковая сумма, как в базе. Знак здесь не используется — направление берётся из [type]. */
        val amountKopecks: Long,
        val type         : TransactionType,
        val currency     : String,
        val categoryId   : String?,
        /** Продавец или отправитель, как прислал банк; нормализуется через [MerchantNames.display]. */
        val party        : String?,
    )

    data class CurrencyTotal(val currency: String, val spent: Long, val earned: Long) {
        val net: Long get() = earned - spent
    }

    /** Нарастающий итог НА КОНЕЦ корзины, начинающейся с [periodStart]. */
    data class CurvePoint(val periodStart: LocalDate, val spent: Long, val earned: Long)

    data class CategoryShare(val categoryId: String?, val kopecks: Long)

    data class YearBar(val year: Int, val total: Long, val segments: List<CategoryShare>)

    /**
     * Кому ушли или от кого пришли деньги. [label] `null` — продавец не назван (зарплата
     * «Зачисление», перевод без имени), и тогда источником служит категория [categoryId].
     */
    data class SourceTotal(
        val label     : String?,
        val categoryId: String?,
        val kopecks   : Long,
        val count     : Int,
        val lastAt    : Long,
    )

    data class Result(
        /** Валюта графиков, баров, долей и источников. */
        val currency        : String,
        val totals          : List<CurrencyTotal>,
        val curve           : List<CurvePoint>,
        val spentByYear     : List<YearBar>,
        val earnedByYear    : List<YearBar>,
        val spentByCategory : List<CategoryShare>,
        val earnedByCategory: List<CategoryShare>,
        val spentSources    : List<SourceTotal>,
        val earnedSources   : List<SourceTotal>,
        /** С какой даты есть данные в основной валюте; `null` — операций нет. */
        val firstDate       : LocalDate?,
    ) {
        val isEmpty: Boolean get() = totals.isEmpty()
    }

    /**
     * Всё, что не зависит от шага графика. Считается один раз на окно: смена шага «месяц / год»
     * пересчитывает только кривую, а не группировку всей истории по продавцам.
     */
    class Base internal constructor(
        val result : Result,
        internal val primaryRows: List<Entry>,
        internal val today: LocalDate,
        internal val zone : ZoneId,
    )

    fun entriesOf(transactions: List<TransactionEntity>): List<Entry> = transactions
        .filter { !it.isDeleted }
        .map {
            val category = it.categoryId
                ?: if (it.type == TransactionType.EXPENSE) OTHER_CATEGORY else null
            Entry(it.timestamp, it.amountKopecks, it.type, it.currency, category, it.merchant)
        }

    /** Куда вкладка «Категории» кладёт трату без категории — туда же и здесь. */
    const val OTHER_CATEGORY = "cat_other"

    /** Рубль, если он есть; иначе валюта с наибольшим оборотом. */
    fun primaryCurrency(totals: List<CurrencyTotal>): String =
        totals.firstOrNull { it.currency == BASE_CURRENCY }?.currency
            ?: totals.maxByOrNull { it.spent + it.earned }?.currency
            ?: BASE_CURRENCY

    /**
     * Итоги за всё время для плитки — тем же фильтром, что и экран «Всё время»: без переводов и без
     * будущих дат. Плитка и экран, который она открывает, обязаны показывать одну цифру.
     */
    fun lifetimeTotals(entries: List<Entry>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CurrencyTotal> =
        totals(entries.filter { !Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().isAfter(today) })

    /** Итоги по валютам — для плитки на экране аналитики, без всего остального расчёта. */
    fun totals(entries: List<Entry>): List<CurrencyTotal> = entries
        .filter { it.type != TransactionType.TRANSFER }
        .groupBy { it.currency }
        .map { (currency, list) ->
            CurrencyTotal(
                currency = currency,
                spent    = list.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) },
                earned   = list.filter { it.type == TransactionType.INCOME }.sumOf { abs(it.amountKopecks) },
            )
        }
        // Рубль первым, остальные — по обороту: главная цифра не должна прыгать от того, в какой
        // валюте была последняя подписка.
        .sortedWith(compareByDescending<CurrencyTotal> { it.currency == BASE_CURRENCY }
            .thenByDescending { it.spent + it.earned })

    fun compute(
        entries: List<Entry>,
        horizon: Horizon,
        step   : Step,
        today  : LocalDate,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): Result {
        val base = computeBase(entries, horizon, today, zone)
        return base.result.copy(curve = curveOf(base, step))
    }

    /** Всё, кроме кривой; см. [Base]. */
    fun computeBase(
        entries: List<Entry>,
        horizon: Horizon,
        today  : LocalDate,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): Base {
        val from = horizon.years?.let { today.minusYears(it) }
        fun dateOf(e: Entry) = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()

        val inWindow = entries.filter { e ->
            e.type != TransactionType.TRANSFER &&
                // «Год» — это последние двенадцать месяцев, включая сегодняшний день, и не дальше
                // него: операция с будущей датой — ошибка ввода, а не прогноз.
                dateOf(e).let { d -> (from == null || d.isAfter(from)) && !d.isAfter(today) }
        }
        val totals   = totals(inWindow)
        val currency = primaryCurrency(totals)
        val primary  = inWindow.filter { it.currency == currency }
        val spent    = primary.filter { it.type == TransactionType.EXPENSE }
        val earned   = primary.filter { it.type == TransactionType.INCOME }

        return Base(
            result = Result(
                currency         = currency,
                totals           = totals,
                curve            = emptyList(),
                spentByYear      = byYear(spent, ::dateOf),
                earnedByYear     = byYear(earned, ::dateOf),
                spentByCategory  = byCategory(spent),
                earnedByCategory = byCategory(earned),
                spentSources     = sources(spent),
                earnedSources    = sources(earned),
                firstDate        = primary.minOfOrNull { dateOf(it) },
            ),
            primaryRows = primary,
            today       = today,
            zone        = zone,
        )
    }

    /** Кривая под шаг — единственное, что пересчитывается при смене шага. */
    fun curveOf(base: Base, step: Step): List<CurvePoint> =
        curve(base.primaryRows, step, base.today) { e ->
            Instant.ofEpochMilli(e.timestamp).atZone(base.zone).toLocalDate()
        }

    /**
     * Нарастающие итоги по корзинам. Пустые корзины НЕ пропускаются: месяц без трат на графике —
     * горизонтальный участок, и выбросить его значило бы сжать время и показать рост круче, чем он был.
     */
    private fun curve(
        base  : List<Entry>,
        step  : Step,
        today : LocalDate,
        dateOf: (Entry) -> LocalDate,
    ): List<CurvePoint> {
        if (base.isEmpty()) return emptyList()
        val bucketOf = { d: LocalDate -> bucketStart(d, step) }
        val byBucket = base.groupBy { bucketOf(dateOf(it)) }

        val out = ArrayList<CurvePoint>()
        var cumSpent  = 0L
        var cumEarned = 0L
        var cursor = bucketOf(base.minOf { dateOf(it) })
        val last   = bucketOf(today)
        while (!cursor.isAfter(last)) {
            byBucket[cursor].orEmpty().forEach { e ->
                when (e.type) {
                    TransactionType.EXPENSE  -> cumSpent  += abs(e.amountKopecks)
                    TransactionType.INCOME   -> cumEarned += abs(e.amountKopecks)
                    TransactionType.TRANSFER -> Unit
                }
            }
            out += CurvePoint(cursor, cumSpent, cumEarned)
            cursor = cursor.plusMonths(step.months.toLong())
        }
        return out
    }

    /** Начало корзины: январь/июль для полугодия, чётный год для двухлетки. */
    fun bucketStart(d: LocalDate, step: Step): LocalDate {
        val monthIndex = d.year * 12 + (d.monthValue - 1)
        val aligned    = monthIndex - Math.floorMod(monthIndex, step.months)
        return LocalDate.of(aligned / 12, aligned % 12 + 1, 1)
    }

    private fun byYear(list: List<Entry>, dateOf: (Entry) -> LocalDate): List<YearBar> = list
        .groupBy { dateOf(it).year }
        .map { (year, rows) ->
            val segments = byCategory(rows)
            YearBar(year = year, total = segments.sumOf { it.kopecks }, segments = segments)
        }
        .sortedBy { it.year }

    private fun byCategory(list: List<Entry>): List<CategoryShare> = list
        .groupBy { it.categoryId }
        .map { (cat, rows) -> CategoryShare(cat, rows.sumOf { abs(it.amountKopecks) }) }
        .filter { it.kopecks > 0L }
        .sortedByDescending { it.kopecks }

    /**
     * Источники: продавцы и отправители. Группировка — по ОТОБРАЖАЕМОМУ имени, а не по сырой строке
     * банка: «RECR GOOGLE *ChatGPT, 855-…» и «ChatGPT» — один получатель, и в списке «кому ушли
     * деньги за десять лет» он обязан быть одной строкой, а не двумя с половиной суммы каждая.
     */
    private fun sources(list: List<Entry>): List<SourceTotal> {
        // Нормализация имени — регулярки и чистка строки. На десятках тысяч операций это заметно,
        // а различных строк у банка на порядок меньше, чем операций: считаем каждую один раз.
        val names = HashMap<String?, String?>()
        fun display(raw: String?) = names.getOrPut(raw) { MerchantNames.display(raw) }
        return list
        .groupBy { e ->
            display(e.party)?.let { "n:${it.lowercase()}" } ?: "c:${e.categoryId}"
        }
        .map { (_, rows) ->
            val latest = rows.maxBy { it.timestamp }
            SourceTotal(
                // Имя — как в последний раз прислал банк: оно узнаваемо.
                label      = display(latest.party),
                categoryId = latest.categoryId,
                kopecks    = rows.sumOf { abs(it.amountKopecks) },
                count      = rows.size,
                lastAt     = latest.timestamp,
            )
        }
        .sortedByDescending { it.kopecks }
    }
}
