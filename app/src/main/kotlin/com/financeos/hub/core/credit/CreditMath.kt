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

/**
 * Translates a bank-reported balance figure into what belongs in `balance_kopecks` for [account],
 * or null when the figure cannot be trusted as a snapshot.
 *
 * On a debit account the figure is the balance and passes through unchanged.
 *
 * On a CREDIT card it is the FREE LIMIT. The confirmed Сбер push reads
 * «Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽» — and 18 699 + 411 301 = 430 000, the card's limit.
 * Note the label is the very same «Баланс» a debit card uses, so the text alone can never
 * disambiguate: only the account's kind can. Given the limit, the debt is exact —
 * stored balance = reported − limit, i.e. −18 699.
 *
 * Returns null (→ the caller falls back to moving by transaction delta) when the limit is unknown,
 * or when the reported figure exceeds it. That second case means the stored limit is stale or
 * mistyped, and applying it would invert the debt into money owned.
 */
fun balanceFromReportedFigure(account: AccountEntity, reportedKopecks: Long): Long? {
    if (account.kind != AccountKind.CREDIT) return reportedKopecks
    val limit = account.creditLimitKopecks ?: return null
    if (limit <= 0L || reportedKopecks > limit) return null
    return reportedKopecks - limit
}

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

// ── The next payment ──────────────────────────────────────────────────────────

/** Where a payment figure came from. Shown to the user — an inferred number must not pose as fact. */
enum class PaymentSource {
    /** Straight from a bank reminder push: the bank's own demand and deadline. */
    BANK,
    /** Derived from the statement day and days-to-pay the user entered. */
    INFERRED,
}

data class DuePayment(
    val amountKopecks: Long,
    val dueDate      : LocalDate,
    /** Negative when the deadline has passed. */
    val daysUntilDue : Int,
    val source       : PaymentSource,
)

/**
 * A bank reminder is trusted for this long after its deadline. Past that the demand was almost
 * certainly settled — the app never sees the payment confirmation, so age is the only signal —
 * and continuing to show it would leave a permanent false "просрочено" on the card.
 */
private const val NOTICE_STALE_AFTER_DAYS = 45L

/**
 * The next payment on a card, preferring what the BANK said over what we inferred.
 *
 * The bank's reminder wins whenever it is recent enough: it is the actual demand, whereas the
 * inferred figure rests on a statement day the user typed from memory and on a statement debt
 * rolled back from the transaction log. Falls back to the cycle when there is no reminder (or it
 * has gone stale), and returns null when neither source can say anything.
 */
fun duePayment(
    reportedAmountKopecks: Long?,
    reportedDueDate      : LocalDate?,
    cycle                : CreditCycle?,
    statementDebtKopecks : Long,
    today                : LocalDate,
): DuePayment? {
    if (reportedAmountKopecks != null && reportedDueDate != null &&
        !reportedDueDate.isBefore(today.minusDays(NOTICE_STALE_AFTER_DAYS))
    ) {
        return DuePayment(
            amountKopecks = reportedAmountKopecks,
            dueDate       = reportedDueDate,
            daysUntilDue  = ChronoUnit.DAYS.between(today, reportedDueDate).toInt(),
            source        = PaymentSource.BANK,
        )
    }
    if (cycle != null) {
        return DuePayment(
            amountKopecks = statementDebtKopecks,
            dueDate       = cycle.dueDate,
            daysUntilDue  = cycle.daysUntilDue,
            source        = PaymentSource.INFERRED,
        )
    }
    return null
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
