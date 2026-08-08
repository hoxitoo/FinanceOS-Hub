package com.financeos.hub.core.credit

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.ParsedTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure credit-card arithmetic: debt, free limit, statement/due dates, minimum payment, interest.
 *
 * The interest figures are ESTIMATES and every caller must present them as such. A bank accrues
 * daily under its own tariff — what enters the interest-free period at all, how a cash withdrawal
 * voids it, how a payment is split between interest, fees and principal — and none of that is
 * derivable from what this app can see. The single exact figure is the zero: paid in full inside
 * the interest-free period, the card really costs nothing.
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

/**
 * Reclassifies money ARRIVING on a credit card as a repayment rather than income.
 *
 * You do not earn money onto a credit card — an incoming amount is almost always you paying the
 * card off. Left as INCOME, a 50 000 ₽ repayment would be counted as 50 000 ₽ earned, inflating
 * the income chart and the savings-rate pillar of the health score, while the matching outflow was
 * already booked on the debit card. That is the double-count this whole area is prone to.
 *
 * The signed amount is UNCHANGED: [ParsedTransaction.signedKopecks] returns `+amount` for INCOME
 * and, for an incoming TRANSFER, `+amount` too. So the balance maths is untouched — only the
 * classification moves, and it moves into the machinery that already knows how to pair the two
 * legs of an internal transfer.
 *
 * The exceptions — cashback and purchase refunds also land on a credit card — are deliberately
 * swept in. Both are small and neither is income either; misfiling a 200 ₽ cashback as a transfer
 * costs far less than misfiling a 50 000 ₽ repayment as income, and the user can retype any row.
 */
fun asRepaymentIfCredit(parsed: ParsedTransaction, accountKind: AccountKind?): ParsedTransaction =
    if (accountKind == AccountKind.CREDIT && parsed.type == TransactionType.INCOME) {
        parsed.copy(type = TransactionType.TRANSFER, outgoing = false)
    } else {
        parsed
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
    // Walk back to the last statement that has already closed, then forward past any whose
    // deadline is also behind us.
    //
    // That second step is the whole point. Anchoring purely on the last CLOSED statement means
    // that for the stretch between a deadline and the next close — nine days a month with the
    // common 30th/20-day terms — the card is permanently "overdue", in red, accruing invented
    // interest, for a user who paid on time. The app never sees the payment confirmation, so it
    // cannot tell a settled bill from a missed one; the only honest reading of a passed deadline
    // is that this window is over and the next bill is the one to watch.
    //
    // A genuinely missed payment is not lost: the bank's own reminder push carries a real past
    // deadline, and [duePayment] prefers it over anything inferred here. The bank knows; we don't.
    var statement = closeIn(thisMonth).let { if (it.isAfter(today)) closeIn(thisMonth.minusMonths(1)) else it }
    if (statement.plusDays(days.toLong()).isBefore(today)) {
        statement = closeIn(YearMonth.from(statement).plusMonths(1))
    }

    val due = statement.plusDays(days.toLong())
    return CreditCycle(
        statementDate     = statement,
        dueDate           = due,
        nextStatementDate = closeIn(YearMonth.from(statement).plusMonths(1)),
        daysUntilDue      = ChronoUnit.DAYS.between(today, due).toInt(),
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

// ── Interest ──────────────────────────────────────────────────────────────────

/**
 * ESTIMATED interest, never a statement of fact.
 *
 * Every figure below is a simple daily/monthly accrual on the outstanding balance. A real bank
 * applies its own tariff: which purchases enter the interest-free period at all, how a cash
 * withdrawal voids it for the whole cycle, how a payment is split between interest, fees and
 * principal, whether the interest-free period restarts. None of that is derivable from what this
 * app can see, so these numbers will not match the bank to the kopeck and the UI must say so.
 *
 * What IS exact is the zero case: while the card is inside its interest-free period and gets paid
 * in full by the deadline, the overpayment really is nothing.
 */

/** Cap on the simulation below — a guard against a non-amortising loop, not a real horizon. */
private const val MAX_PLAN_MONTHS = 600

/**
 * Floor under the minimum payment, kopecks (300 ₽ — Сбер's figure for its credit cards).
 *
 * Not cosmetic: a minimum that is ONLY a percentage of the balance never reaches zero, because
 * each payment shrinks with the balance it is computed from. Without a floor the simulation just
 * decays forever and hits [MAX_PLAN_MONTHS], reporting "never pays off" for a card that plainly
 * does. Real cards all carry such a floor; this one is an assumption, and the UI calls the whole
 * figure an estimate for exactly reasons like this.
 */
private const val MIN_PAYMENT_FLOOR_KOPECKS = 300_00L

/**
 * Interest accrued on [debtKopecks] over [days] at [aprBp], on a simple 365-day-year basis.
 * Null when the rate was never entered; 0 when nothing is owed or no time has passed.
 */
fun accruedInterest(debtKopecks: Long, aprBp: Int?, days: Int): Long? {
    val apr = aprBp?.takeIf { it > 0 } ?: return null
    if (debtKopecks <= 0L || days <= 0) return 0L
    val daily = apr / 10_000.0 / 365.0
    return Math.round(debtKopecks * daily * days)
}

/** What paying only the minimum every month leads to. */
sealed interface MinimumPaymentOutlook {
    /** The debt clears after [months], having cost [totalInterestKopecks] in interest. */
    data class PaysOff(val months: Int, val totalInterestKopecks: Long) : MinimumPaymentOutlook

    /**
     * The minimum payment does not even cover the monthly interest, so the debt never shrinks.
     * There is no total to quote — that is the point, and the UI says it in words.
     */
    object NeverPaysOff : MinimumPaymentOutlook
}

/**
 * Simulates clearing [debtKopecks] by paying ONLY the minimum, month after month, and nothing more
 * onto the card. Returns null when the rate or the minimum percentage was never entered.
 *
 * This is the number that makes a credit card's real cost visible: at 29,8% with a 5% minimum, a
 * debt takes years and costs a large fraction of itself. Deliberately pessimistic in one respect —
 * it assumes no further spending — and optimistic in another: it ignores fees and insurance.
 */
fun minimumPaymentOutlook(debtKopecks: Long, aprBp: Int?, minPaymentBp: Int?): MinimumPaymentOutlook? {
    val apr   = aprBp?.takeIf { it > 0 } ?: return null
    val minBp = minPaymentBp?.takeIf { it > 0 } ?: return null
    if (debtKopecks <= 0L) return MinimumPaymentOutlook.PaysOff(months = 0, totalInterestKopecks = 0L)

    val monthlyRate = apr / 10_000.0 / 12.0
    var balance       = debtKopecks.toDouble()
    var totalInterest = 0.0
    var months        = 0

    while (balance > 0.5 && months < MAX_PLAN_MONTHS) {
        val interest = balance * monthlyRate
        // A share of the balance, but never below the floor — and the final month settles whatever
        // is left rather than leaving a sliver that never rounds away.
        val payment = minOf(
            maxOf(balance * minBp / 10_000.0, MIN_PAYMENT_FLOOR_KOPECKS.toDouble()),
            balance + interest,
        )
        if (payment <= interest) return MinimumPaymentOutlook.NeverPaysOff
        balance       = balance + interest - payment
        totalInterest += interest
        months++
    }
    if (months >= MAX_PLAN_MONTHS) return MinimumPaymentOutlook.NeverPaysOff
    return MinimumPaymentOutlook.PaysOff(months, Math.round(totalInterest))
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
