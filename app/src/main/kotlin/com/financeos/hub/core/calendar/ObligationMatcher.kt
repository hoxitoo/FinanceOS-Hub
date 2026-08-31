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

    data class Match(
        val payment    : PlannedPaymentEntity,
        val transaction: TransactionEntity,
        val dueDate    : LocalDate,
    )

    /**
     * Сопоставляет обязательства с операциями.
     *
     * Одна операция закрывает не больше одного обязательства, и одно обязательство закрывается не
     * больше чем одной операцией за период: иначе один платёж по аренде мог бы закрыть три месяца
     * сразу и «Свободно» ушло бы в фантазию.
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
            val hit = transactions.firstOrNull { tx ->
                tx.id !in used && fits(payment, due, tx, zone)
            } ?: continue
            used += hit.id
            out  += Match(payment, hit, due)
        }
        return out
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

    /** Подходит ли операция под обязательство. Все условия обязательны. */
    fun fits(
        payment: PlannedPaymentEntity,
        dueDate: LocalDate,
        tx     : TransactionEntity,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (tx.isDeleted) return false
        if (tx.currency != payment.currency) return false

        // Направление. Перевод между своими счетами исключён намеренно: он не тратит деньги, а
        // перекладывает, и закрывать им обязательство нельзя.
        val expected = if (payment.direction == PaymentDirection.OUT) TransactionType.EXPENSE
                       else TransactionType.INCOME
        if (tx.type != expected) return false

        if (payment.accountId != null && tx.accountId != payment.accountId) return false

        val amount = abs(tx.amountKopecks)
        val target = payment.amountKopecks
        if (target <= 0L) return false
        if (abs(amount - target).toDouble() > target * AMOUNT_TOLERANCE) return false

        val txDate = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
        val delta  = ChronoUnit.DAYS.between(dueDate, txDate)
        return delta in -DAYS_BEFORE..DAYS_AFTER
    }
}
