package com.financeos.hub.core.calendar

import com.financeos.hub.core.database.entities.PaymentDirection
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Какая операция закрыла обязательство.
 *
 * Это самое опасное место всей затеи, и опасность несимметрична:
 *
 *  - **слишком жадное** сопоставление — случайная покупка «закрывает» аренду, «Свободно»
 *    подскакивает на 35 000, человек тратит деньги, которых нет;
 *  - **слишком строгое** — аренда оплачена, а приложение продолжает её вычитать, и «Свободно»
 *    занижено.
 *
 * Первая ошибка дороже второй: заниженная сумма делает человека осторожнее, завышенная — беднее.
 * Поэтому правила намеренно консервативные, и при сомнении обязательство остаётся открытым.
 *
 * Транзакция при сопоставлении НЕ меняется. Связь живёт на стороне обязательства
 * ([PlannedPaymentEntity.lastMatchedTxId]) — односторонне, обратимо и без риска испортить историю.
 */
object ObligationMatcher {

    /** Насколько фактическая сумма может отличаться от ожидаемой. Тарифы и курсы меняются. */
    private const val AMOUNT_TOLERANCE = 0.15

    /** Списывают обычно чуть раньше или чуть позже; окно намеренно несимметричное. */
    private const val DAYS_BEFORE = 5L
    private const val DAYS_AFTER  = 7L

    /**
     * Сколько операций максимум могут сложиться в один платёж.
     *
     * Счёт за связь платят двумя-тремя переводами (телефон отдельно, интернет отдельно), но не
     * десятью: чем больше слагаемых, тем легче случайному набору покупок сложиться в нужную сумму.
     */
    private const val MAX_PARTS = 3

    /**
     * Минимальная доля одной части от суммы обязательства.
     *
     * Без неё копеечная покупка «добивала» бы сумму до допуска: 1 900 + 100 = 2 000. Часть платежа
     * — это заметная часть, а не остаток от округления.
     */
    private const val MIN_PART_SHARE = 0.20

    data class Match(
        val payment     : PlannedPaymentEntity,
        /**
         * Операции, закрывшие обязательство. Обычно одна; несколько — когда счёт оплачен по
         * частям.
         */
        val transactions: List<TransactionEntity>,
        val dueDate     : LocalDate,
    ) {
        /** Самая крупная часть — она представляет платёж там, где нужна одна операция. */
        val primary: TransactionEntity get() = transactions.maxByOrNull { abs(it.amountKopecks) }!!
    }

    /**
     * Сопоставляет обязательства с операциями.
     *
     * Одна операция закрывает не больше одного обязательства, и одно обязательство закрывается не
     * больше чем одной операцией за период: иначе один платёж по аренде мог бы закрыть три месяца
     * сразу и «Свободно» ушло бы в фантазию.
     *
     * Операция, которую человек отверг кнопкой «Отвязать», больше не рассматривается для этого
     * обязательства: иначе отвязывание было бы кнопкой, после которой всё возвращается назад.
     *
     * @param dueDates какие даты обязательства нас интересуют (обычно — ближайшая незакрытая).
     */
    fun match(
        payments    : List<PlannedPaymentEntity>,
        dueDates    : Map<String, LocalDate>,
        transactions: List<TransactionEntity>,
        zone        : ZoneId = ZoneId.systemDefault(),
    ): List<Match> {
        val used = HashSet<String>()
        val out  = ArrayList<Match>()

        // Сначала те, у кого указан счёт: у них условие строже, и отдавать им операцию первыми
        // честнее, чем позволить более общему обязательству перехватить её.
        val ordered = payments.sortedByDescending { it.accountId != null }

        for (payment in ordered) {
            val due = dueDates[payment.id] ?: continue
            val free = transactions.filter {
                it.id !in used && it.id != payment.rejectedTxId && fitsApartFromAmount(payment, due, it, zone)
            }

            // БЛИЖАЙШАЯ к сроку, а не первая подходящая. Список приходит по убыванию времени, и
            // «первая» означала бы «самая свежая»: у недельного обязательства платёж следующей
            // недели закрывал бы предыдущую, а свой собственный период после этого не закрылся бы
            // уже никогда.
            val single = free
                .filter { amountFits(payment, abs(it.amountKopecks)) }
                .minByOrNull { abs(ChronoUnit.DAYS.between(due, dateOf(it, zone))) }

            // Одиночное совпадение всегда важнее составного: если счёт закрывается одной
            // операцией, складывать соседние незачем.
            val parts = single?.let { listOf(it) } ?: splitPayment(payment, free, zone)
            if (parts.isEmpty()) continue

            parts.forEach { used += it.id }
            out += Match(payment, parts, due)
        }
        return out
    }

    /**
     * Счёт, оплаченный НЕСКОЛЬКИМИ операциями: телефон отдельно, интернет отдельно.
     *
     * Складывать произвольные траты, пока не сойдётся сумма, — прямой путь к той самой жадности,
     * от которой защищает весь остальной файл: две случайные покупки недели сложатся в нужные
     * 2 000 ₽ без труда. Поэтому части обязаны выглядеть как ОДИН платёж, разбитый на несколько:
     *
     *  - все в ОДИН календарный день. Счёт оплачивают за один присест, а не размазывают по неделе;
     *  - их не больше [MAX_PARTS];
     *  - каждая не меньше [MIN_PART_SHARE] от суммы — иначе мелочь «добивала» бы сумму до допуска;
     *  - категории не противоречат друг другу: две известные разные категории — это две разные
     *    траты, а не один счёт. Если у обязательства категория указана, части обязаны быть в ней.
     *
     * Из подходящих наборов берётся самый точный по сумме, при равенстве — с наименьшим числом
     * частей: чем меньше слагаемых, тем меньше шанс, что сумма сошлась случайно.
     */
    private fun splitPayment(
        payment: PlannedPaymentEntity,
        free   : List<TransactionEntity>,
        zone   : ZoneId,
    ): List<TransactionEntity> {
        val target = payment.amountKopecks
        if (target <= 0L) return emptyList()
        val floor = (target * MIN_PART_SHARE).toLong()

        val byDay = free
            .filter { abs(it.amountKopecks) >= floor }
            .groupBy { dateOf(it, zone) }

        var best: List<TransactionEntity>? = null
        var bestGap = Long.MAX_VALUE

        for ((_, sameDay) in byDay) {
            // День с десятком подходящих трат — это не разбитый счёт, а обычный день. Перебирать
            // его сочетания и дорого, и незачем.
            if (sameDay.size < 2 || sameDay.size > 8) continue
            for (combo in combinations(sameDay, 2, MAX_PARTS)) {
                if (!categoriesAgree(payment, combo)) continue
                val sum = combo.sumOf { abs(it.amountKopecks) }
                if (!amountFits(payment, sum)) continue
                val gap = abs(sum - target)
                if (gap < bestGap || (gap == bestGap && combo.size < (best?.size ?: Int.MAX_VALUE))) {
                    best = combo
                    bestGap = gap
                }
            }
        }
        return best.orEmpty()
    }

    /** Все сочетания размером от [min] до [max]. Список короткий — перебор дешевле хитростей. */
    private fun <T> combinations(items: List<T>, min: Int, max: Int): List<List<T>> {
        val out = ArrayList<List<T>>()
        fun walk(start: Int, acc: MutableList<T>) {
            if (acc.size in min..max) out += acc.toList()
            if (acc.size == max) return
            for (i in start until items.size) {
                acc += items[i]
                walk(i + 1, acc)
                acc.removeAt(acc.lastIndex)
            }
        }
        walk(0, ArrayList())
        return out
    }

    /**
     * Не противоречат ли категории частей друг другу и обязательству.
     *
     * Неизвестная категория (`null`) не мешает: банк её не присылает, и требовать её значило бы
     * отключить составные платежи у большинства операций. А вот ДВЕ РАЗНЫЕ известные категории —
     * это две разные траты, и складывать их в один счёт нельзя.
     */
    private fun categoriesAgree(payment: PlannedPaymentEntity, parts: List<TransactionEntity>): Boolean {
        val known = parts.mapNotNull { it.categoryId }.toSet()
        if (known.size > 1) return false
        val declared = payment.categoryId
        return declared == null || known.isEmpty() || declared in known
    }

    /**
     * Какую дату каждого обязательства сейчас имеет смысл закрывать.
     *
     * Берётся САМАЯ РАННЯЯ ещё не закрытая — не ближайшая будущая. Иначе неоплаченный прошлый месяц
     * молча пропускался бы: отметка [PlannedPaymentEntity.matchedThrough] перепрыгнула бы через
     * него, и «Свободно» перестало бы вычитать долг, который никто не платил.
     *
     * Верхняя граница — `today + DAYS_BEFORE`: списать раньше срока можно, но не более чем на то же
     * окно, в котором [fits] вообще согласится сопоставить.
     *
     * @param lookbackDays насколько далеко назад искать незакрытые даты у обязательства, которое
     *        никогда не сопоставлялось. Без ограничения годовая подписка тянула бы за собой всю
     *        историю с момента якоря.
     */
    fun openDueDates(
        payments    : List<PlannedPaymentEntity>,
        today       : LocalDate,
        zone        : ZoneId = ZoneId.systemDefault(),
        lookbackDays: Long = 60L,
    ): Map<String, LocalDate> {
        val floor = today.minusDays(lookbackDays)
        val to    = today.plusDays(DAYS_BEFORE)
        return payments.mapNotNull { p ->
            val settledThrough = p.matchedThrough
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val from = maxOf(floor, settledThrough?.plusDays(1) ?: floor)
            PaymentDates.occurrencesIn(p, from, to, zone)
                .firstOrNull()
                ?.let { p.id to it }
        }.toMap()
    }

    private fun dateOf(tx: TransactionEntity, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()

    /** Подходит ли операция под обязательство ЦЕЛИКОМ. Все условия обязательны. */
    fun fits(
        payment: PlannedPaymentEntity,
        dueDate: LocalDate,
        tx     : TransactionEntity,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): Boolean =
        fitsApartFromAmount(payment, dueDate, tx, zone) && amountFits(payment, abs(tx.amountKopecks))

    /**
     * Всё, кроме суммы: валюта, направление, счёт, окно дат.
     *
     * Вынесено отдельно, потому что у составного платежа сумма проверяется у НАБОРА, а не у каждой
     * части: 550 ₽ сами по себе не похожи на счёт в 2 000 ₽, и одиночная проверка отсекла бы их
     * раньше, чем дело дошло бы до сложения.
     */
    private fun fitsApartFromAmount(
        payment: PlannedPaymentEntity,
        dueDate: LocalDate,
        tx     : TransactionEntity,
        zone   : ZoneId,
    ): Boolean {
        if (tx.isDeleted) return false
        if (tx.currency != payment.currency) return false

        // Направление. Перевод между своими счетами исключён намеренно: он не тратит деньги, а
        // перекладывает, и закрывать им обязательство нельзя.
        val expected = if (payment.direction == PaymentDirection.OUT) TransactionType.EXPENSE
                       else TransactionType.INCOME
        if (tx.type != expected) return false

        if (payment.accountId != null && tx.accountId != payment.accountId) return false
        if (payment.amountKopecks <= 0L) return false

        val delta = ChronoUnit.DAYS.between(dueDate, dateOf(tx, zone))
        return delta in -DAYS_BEFORE..DAYS_AFTER
    }

    /** Укладывается ли [amount] в допуск вокруг суммы обязательства. */
    private fun amountFits(payment: PlannedPaymentEntity, amount: Long): Boolean {
        val target = payment.amountKopecks
        if (target <= 0L) return false
        return abs(amount - target).toDouble() <= target * AMOUNT_TOLERANCE
    }
}
