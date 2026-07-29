package com.financeos.hub.core.credit

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure credit-card arithmetic: debt, free limit, statement/due dates, minimum payment.
 *
 * Deliberately contains NO interest maths. A bank accrues daily on the carried balance under
 * per-tariff rules (what enters the grace period at all, how a cash withdrawal voids it, how a
 * minimum payment is allocated), and none of that is derivable from the data this app holds.
 * Anything resembling "переплата" is a later, explicitly-labelled estimate — not a fact we can
 * assert here.
 *
 * Everything is a pure function of its arguments (`today` is injected, never read from the clock)
 * so the cycle boundaries are unit-testable.
 */

// ── Derived amounts ───────────────────────────────────────────────────────────

/** Outstanding debt as a POSITIVE magnitude. 0 for a non-credit account or a fully repaid card. */
val AccountEntity.debtKopecks: Long
    get() = if (kind == AccountKind.CREDIT) (-balanceKopecks).coerceAtLeast(0L) else 0L

/**
 * Money still available to spend on the card, or null when the limit was never entered.
 * Floored at 0 so an over-limit card reads "0 свободно" rather than a negative "available".
 */
val AccountEntity.freeLimitKopecks: Long?
    get() = if (kind == AccountKind.CREDIT) {
        creditLimitKopecks?.let { (it + balanceKopecks).coerceAtLeast(0L) }
    } else null

/** Share of the limit currently used, 0f..1f. Null when there is no limit to divide by. */
val AccountEntity.creditUtilization: Float?
    get() = creditLimitKopecks
        ?.takeIf { it > 0L }
        ?.let { (debtKopecks.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }

/** Annual rate as a percentage — 2980 bp → 29.8. Null when not configured. */
val AccountEntity.aprPercent: Double?
    get() = aprBp?.takeIf { it > 0 }?.let { it / 100.0 }

// ── Billing cycle ─────────────────────────────────────────────────────────────

/**
 * One billing cycle positioned around [CreditCycle.statementDate] — the most recent statement
 * close on or before "today". The debt frozen at that date is what has to be paid by
 * [dueDate]; anything spent after it rolls into the next statement.
 */
data class CreditCycle(
    /** Most recent statement close (start of the payment window). */
    val statementDate    : LocalDate,
    /** Pay by this date to stay inside the interest-free period. */
    val dueDate          : LocalDate,
    /** When the current spending period closes and becomes the next statement. */
    val nextStatementDate: LocalDate,
    /** Days from today to [dueDate]. Negative = the payment is late. */
    val daysUntilDue     : Int,
) {
    val isOverdue: Boolean get() = daysUntilDue < 0

    /** How far through the payment window we are, 0f (just closed) .. 1f (due today). */
    val windowProgress: Float
        get() {
            val total = ChronoUnit.DAYS.between(statementDate, dueDate).toFloat()
            if (total <= 0f) return 1f
            val gone = (total - daysUntilDue.toFloat()).coerceIn(0f, total)
            return gone / total
        }
}

/**
 * Builds the cycle for a card whose statement closes on [statementDay] with [dueDays] to pay.
 * Returns null when either term is missing or out of range — the UI then simply omits the
 * timeline instead of showing a date it guessed.
 *
 * The statement day is clamped to the length of each month, so "closes on the 31st" resolves to
 * 28/29 February and 30 April rather than throwing.
 */
fun creditCycle(statementDay: Int?, dueDays: Int?, today: LocalDate): CreditCycle? {
    val day  = statementDay?.takeIf { it in 1..31 } ?: return null
    val days = dueDays?.takeIf { it in 1..90 } ?: return null

    fun closeIn(ym: YearMonth): LocalDate = ym.atDay(minOf(day, ym.lengthOfMonth()))

    val thisMonth = YearMonth.from(today)
    // The statement that governs the money owed right now is the last one that has already
    // closed. If this month's close is still ahead of us, we are inside the window opened by
    // last month's.
    val statement = closeIn(thisMonth).let { if (it.isAfter(today)) closeIn(thisMonth.minusMonths(1)) else it }

    return CreditCycle(
        statementDate     = statement,
        dueDate           = statement.plusDays(days.toLong()),
        nextStatementDate = closeIn(YearMonth.from(statement).plusMonths(1)),
        daysUntilDue      = ChronoUnit.DAYS.between(today, statement.plusDays(days.toLong())).toInt(),
    )
}

/**
 * Minimum payment for [debtKopecks] at [minPaymentBp] basis points, rounded UP to the kopeck —
 * understating a minimum payment is the one error that actually costs the user money.
 * Returns 0 when nothing is owed, and null when the percentage was never configured.
 */
fun minPaymentKopecks(debtKopecks: Long, minPaymentBp: Int?): Long? {
    if (debtKopecks <= 0L) return 0L
    val bp = minPaymentBp?.takeIf { it > 0 } ?: return null
    return (debtKopecks * bp + 9_999L) / 10_000L
}
