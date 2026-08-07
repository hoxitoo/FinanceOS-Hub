package com.financeos.hub.core.credit

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.ParsedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers the credit cycle boundaries and the derived amounts. These are the numbers a user reads
 * as a deadline, so an off-by-one month here shows up as a payment date that is wrong by weeks.
 */
class CreditMathTest {

    private fun card(
        balance     : Long = -5_000_00L,   // 5 000 ₽ of debt
        limit       : Long? = 450_000_00L,
        statementDay: Int? = 30,
        dueDays     : Int? = 20,
        minBp       : Int? = 500,
        aprBp       : Int? = 2980,
    ) = AccountEntity(
        id = "c1", name = "Кредитка", bank = "Сбербанк", cardMask = "9526",
        balanceKopecks = balance, kind = AccountKind.CREDIT,
        creditLimitKopecks = limit, aprBp = aprBp,
        statementDay = statementDay, dueDays = dueDays, minPaymentBp = minBp,
    )

    // ── Derived amounts ───────────────────────────────────────────────────────

    @Test
    fun `debt is the magnitude of a negative balance`() {
        assertEquals(5_000_00L, card(balance = -5_000_00L).debtKopecks)
    }

    @Test
    fun `a repaid card owes nothing, and an overpaid one does not owe a negative amount`() {
        assertEquals(0L, card(balance = 0L).debtKopecks)
        assertEquals(0L, card(balance = 1_000_00L).debtKopecks)
    }

    @Test
    fun `a cash account never reports credit debt even when overdrawn`() {
        val overdrawn = card(balance = -3_000_00L).copy(kind = AccountKind.CASH)
        assertEquals(0L, overdrawn.debtKopecks)
        assertNull(overdrawn.freeLimitKopecks)
    }

    @Test
    fun `free limit is limit minus debt`() {
        assertEquals(445_000_00L, card(balance = -5_000_00L).freeLimitKopecks)
    }

    @Test
    fun `free limit floors at zero when the card is over its limit`() {
        assertEquals(0L, card(balance = -500_000_00L, limit = 450_000_00L).freeLimitKopecks)
    }

    @Test
    fun `free limit and utilisation are unknown without a configured limit`() {
        assertNull(card(limit = null).freeLimitKopecks)
        assertNull(card(limit = null).creditUtilization)
    }

    @Test
    fun `utilisation is the used share of the limit`() {
        val c = card(balance = -45_000_00L, limit = 450_000_00L)
        assertEquals(0.10f, c.creditUtilization!!, 0.0001f)
    }

    @Test
    fun `apr is stored in basis points and read back as a percentage`() {
        assertEquals(29.8, card(aprBp = 2980).aprPercent!!, 0.0001)
        assertNull(card(aprBp = null).aprPercent)
        assertNull(card(aprBp = 0).aprPercent)
    }

    // ── Reading the bank's reported figure ────────────────────────────────────

    @Test
    fun `a credit card's reported figure is its free limit, not a balance`() {
        // The real Сбер push: "Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽" on a 430 000 ₽ card.
        // Stored naively, 411 301 would read as money owned; converted, it is 18 699 of debt.
        val c = card(balance = 0L, limit = 430_000_00L)
        assertEquals(-18_699_00L, balanceFromReportedFigure(c, 411_301_00L))
    }

    @Test
    fun `a fully repaid card reports the whole limit as free`() {
        assertEquals(0L, balanceFromReportedFigure(card(limit = 430_000_00L), 430_000_00L))
    }

    @Test
    fun `without a limit the figure is uninterpretable and must be refused`() {
        // Refusing means the caller falls back to the transaction delta, which is never ambiguous.
        assertNull(balanceFromReportedFigure(card(limit = null), 411_301_00L))
    }

    @Test
    fun `a figure larger than the limit means the limit is wrong, so refuse`() {
        // Applying it would compute a NEGATIVE debt — i.e. book the card as money owned.
        assertNull(balanceFromReportedFigure(card(limit = 100_000_00L), 411_301_00L))
    }

    @Test
    fun `a debit account's figure passes through untouched`() {
        val debit = card(limit = null).copy(kind = AccountKind.CASH)
        assertEquals(411_301_00L, balanceFromReportedFigure(debit, 411_301_00L))
    }

    // ── Repayment classification ──────────────────────────────────────────────

    private fun income(amount: Long) = ParsedTransaction(
        type = TransactionType.INCOME, amountKopecks = amount, merchant = null,
        cardMask = "6703", balanceKopecks = null, timestamp = 0L,
        bankId = "sberbank", rawSms = "", smsId = "s1",
    )

    @Test
    fun `money arriving on a credit card is a repayment, not income`() {
        // Left as INCOME this is 50 000 ₽ "earned", inflating the income chart and the savings
        // pillar — while the matching outflow was already booked on the debit card.
        val r = asRepaymentIfCredit(income(50_000_00L), AccountKind.CREDIT)
        assertEquals(TransactionType.TRANSFER, r.type)
        assertFalse("the money is arriving, not leaving", r.outgoing)
    }

    @Test
    fun `reclassifying a repayment does not change the amount that hits the balance`() {
        // The whole point: only the classification moves. INCOME and an incoming TRANSFER both
        // sign to +amount, so the debt shrinks by exactly the same figure as before.
        val original = income(50_000_00L)
        val r = asRepaymentIfCredit(original, AccountKind.CREDIT)
        assertEquals(original.signedKopecks(), r.signedKopecks())
        assertEquals(50_000_00L, r.signedKopecks())
    }

    @Test
    fun `income on a normal account is left alone`() {
        assertEquals(TransactionType.INCOME, asRepaymentIfCredit(income(80_000_00L), AccountKind.CASH).type)
        assertEquals(TransactionType.INCOME, asRepaymentIfCredit(income(80_000_00L), null).type)
    }

    @Test
    fun `spending on a credit card stays an expense`() {
        val purchase = income(18_699_00L).copy(type = TransactionType.EXPENSE)
        assertEquals(TransactionType.EXPENSE, asRepaymentIfCredit(purchase, AccountKind.CREDIT).type)
    }

    // ── Which payment to show ─────────────────────────────────────────────────

    @Test
    fun `the bank's own demand outranks our inference`() {
        val cycle = creditCycle(30, 20, LocalDate.of(2026, 8, 20))
        val due = duePayment(
            reportedAmountKopecks = 373_98L,
            reportedDueDate       = LocalDate.of(2026, 8, 31),
            cycle                 = cycle,
            statementDebtKopecks  = 18_699_00L,
            today                 = LocalDate.of(2026, 8, 20),
        )!!
        assertEquals(PaymentSource.BANK, due.source)
        assertEquals(373_98L, due.amountKopecks)
        assertEquals(LocalDate.of(2026, 8, 31), due.dueDate)
        assertEquals(11, due.daysUntilDue)
    }

    @Test
    fun `without a reminder we fall back to the computed cycle`() {
        val today = LocalDate.of(2026, 7, 5)
        val due = duePayment(null, null, creditCycle(30, 20, today), 18_699_00L, today)!!
        assertEquals(PaymentSource.INFERRED, due.source)
        assertEquals(18_699_00L, due.amountKopecks)
        assertEquals(LocalDate.of(2026, 7, 20), due.dueDate)
    }

    @Test
    fun `a long-settled reminder stops speaking for the card`() {
        // The app never sees the payment confirmation, so a months-old demand would otherwise
        // leave a permanent false "просрочено" on the card.
        val today = LocalDate.of(2026, 12, 1)
        val due = duePayment(
            reportedAmountKopecks = 373_98L,
            reportedDueDate       = LocalDate.of(2026, 8, 31),
            cycle                 = creditCycle(30, 20, today),
            statementDebtKopecks  = 5_000_00L,
            today                 = today,
        )!!
        assertEquals(PaymentSource.INFERRED, due.source)
    }

    @Test
    fun `a recently missed payment is still the bank's demand`() {
        val today = LocalDate.of(2026, 9, 5)
        val due = duePayment(373_98L, LocalDate.of(2026, 8, 31), null, 0L, today)!!
        assertEquals(PaymentSource.BANK, due.source)
        assertEquals(-5, due.daysUntilDue)
    }

    @Test
    fun `with neither a reminder nor terms there is nothing to show`() {
        assertNull(duePayment(null, null, null, 5_000_00L, LocalDate.of(2026, 7, 5)))
    }

    // ── Cycle boundaries ──────────────────────────────────────────────────────

    @Test
    fun `after this month's close the window belongs to this month's statement`() {
        // Closes on the 30th, 20 days to pay. On 5 July the last close was 30 June.
        val cycle = creditCycle(30, 20, LocalDate.of(2025, 7, 5))!!
        assertEquals(LocalDate.of(2025, 6, 30), cycle.statementDate)
        assertEquals(LocalDate.of(2025, 7, 20), cycle.dueDate)
        assertEquals(15, cycle.daysUntilDue)
        assertFalse(cycle.isOverdue)
    }

    @Test
    fun `before this month's close we are still inside last month's window`() {
        // On 25 June the 30 June close has not happened yet — the live bill is 30 May's.
        val cycle = creditCycle(30, 20, LocalDate.of(2025, 6, 25))!!
        assertEquals(LocalDate.of(2025, 5, 30), cycle.statementDate)
        assertEquals(LocalDate.of(2025, 6, 19), cycle.dueDate)
        assertEquals(-6, cycle.daysUntilDue)
        assertTrue(cycle.isOverdue)
    }

    @Test
    fun `the close day is clamped to short months`() {
        // "Closes on the 31st" in February resolves to the 28th rather than throwing.
        val cycle = creditCycle(31, 20, LocalDate.of(2025, 3, 1))!!
        assertEquals(LocalDate.of(2025, 2, 28), cycle.statementDate)
        assertEquals(LocalDate.of(2025, 3, 20), cycle.dueDate)
    }

    @Test
    fun `closing on the statement day itself counts as already closed`() {
        val cycle = creditCycle(30, 20, LocalDate.of(2025, 6, 30))!!
        assertEquals(LocalDate.of(2025, 6, 30), cycle.statementDate)
        assertEquals(20, cycle.daysUntilDue)
    }

    @Test
    fun `the next close is one month after the current one`() {
        val cycle = creditCycle(30, 20, LocalDate.of(2025, 7, 5))!!
        assertEquals(LocalDate.of(2025, 7, 30), cycle.nextStatementDate)
    }

    @Test
    fun `a January close rolls the year over`() {
        val cycle = creditCycle(15, 20, LocalDate.of(2026, 1, 10))!!
        assertEquals(LocalDate.of(2025, 12, 15), cycle.statementDate)
        assertEquals(LocalDate.of(2026, 1, 4), cycle.dueDate)
        assertEquals(LocalDate.of(2026, 1, 15), cycle.nextStatementDate)
    }

    @Test
    fun `no cycle without both terms`() {
        assertNull(creditCycle(null, 20, LocalDate.of(2025, 7, 5)))
        assertNull(creditCycle(30, null, LocalDate.of(2025, 7, 5)))
        assertNull(creditCycle(0, 20, LocalDate.of(2025, 7, 5)))
        assertNull(creditCycle(32, 20, LocalDate.of(2025, 7, 5)))
        assertNull(creditCycle(30, 0, LocalDate.of(2025, 7, 5)))
    }

    @Test
    fun `window progress runs from the close to the due date`() {
        val fresh = creditCycle(30, 20, LocalDate.of(2025, 6, 30))!!
        assertEquals(0f, fresh.windowProgress, 0.001f)
        val half = creditCycle(30, 20, LocalDate.of(2025, 7, 10))!!
        assertEquals(0.5f, half.windowProgress, 0.001f)
        val dueToday = creditCycle(30, 20, LocalDate.of(2025, 7, 20))!!
        assertEquals(1f, dueToday.windowProgress, 0.001f)
    }

    // ── Interest ──────────────────────────────────────────────────────────────

    @Test
    fun `no interest accrues while the deadline has not passed`() {
        // The exact case: inside the interest-free period the cost really is nothing.
        assertEquals(0L, accruedInterest(50_000_00L, 2980, days = 0))
    }

    @Test
    fun `interest accrues daily once the deadline has passed`() {
        // 50 000 ₽ at 29,8% for 30 days ≈ 50000 × 0.298 / 365 × 30 = 1224,66 ₽
        assertEquals(1_224_66L, accruedInterest(50_000_00L, 2980, days = 30))
    }

    @Test
    fun `nothing owed costs nothing, and no rate means no figure at all`() {
        assertEquals(0L, accruedInterest(0L, 2980, days = 90))
        assertNull(accruedInterest(50_000_00L, null, days = 30))
        assertNull(accruedInterest(50_000_00L, 0, days = 30))
    }

    @Test
    fun `paying only the minimum clears the debt slowly and expensively`() {
        // 50 000 ₽ at 29,8% with a 5% minimum: 112 months and 45 813,06 ₽ of interest — the card
        // nearly doubles. Pinned exactly so any change to the model has to be deliberate.
        assertEquals(
            MinimumPaymentOutlook.PaysOff(months = 112, totalInterestKopecks = 4_581_306L),
            minimumPaymentOutlook(50_000_00L, aprBp = 2980, minPaymentBp = 500),
        )
    }

    @Test
    fun `a percentage-only minimum would never reach zero, so a floor is applied`() {
        // Each payment shrinks with the balance it is computed from, so without the floor the
        // balance decays forever and a plainly-repayable card reports "never pays off".
        val outlook = minimumPaymentOutlook(50_000_00L, aprBp = 2980, minPaymentBp = 500)
                as MinimumPaymentOutlook.PaysOff
        assertTrue("must terminate well inside the guard cap", outlook.months < 600)
    }

    @Test
    fun `a minimum below the interest never clears the debt`() {
        // 1% a month against ~2.5% of monthly interest: the balance grows every month.
        assertEquals(
            MinimumPaymentOutlook.NeverPaysOff,
            minimumPaymentOutlook(50_000_00L, aprBp = 2980, minPaymentBp = 100),
        )
    }

    @Test
    fun `an already repaid card has nothing to plan`() {
        assertEquals(
            MinimumPaymentOutlook.PaysOff(months = 0, totalInterestKopecks = 0L),
            minimumPaymentOutlook(0L, aprBp = 2980, minPaymentBp = 500),
        )
    }

    @Test
    fun `no outlook without both the rate and the minimum percentage`() {
        assertNull(minimumPaymentOutlook(50_000_00L, aprBp = null, minPaymentBp = 500))
        assertNull(minimumPaymentOutlook(50_000_00L, aprBp = 2980, minPaymentBp = null))
    }

    @Test
    fun `a zero-rate card costs nothing to carry`() {
        val outlook = minimumPaymentOutlook(50_000_00L, aprBp = 1, minPaymentBp = 5000)
        val paysOff = outlook as MinimumPaymentOutlook.PaysOff
        assertTrue("a near-zero rate should be nearly free", paysOff.totalInterestKopecks < 100_00L)
    }

    // ── Minimum payment ───────────────────────────────────────────────────────

    @Test
    fun `minimum payment is a share of the debt, rounded up`() {
        // 5% of 50 000 ₽ = 2 500 ₽
        assertEquals(2_500_00L, minPaymentKopecks(50_000_00L, 500))
        // 5% of 1 000,01 ₽ = 50,0005 ₽ → rounds UP to 50,01 ₽; never quote less than the bank asks.
        assertEquals(50_01L, minPaymentKopecks(1_000_01L, 500))
    }

    @Test
    fun `nothing owed means nothing to pay, and an unset percentage means unknown`() {
        assertEquals(0L, minPaymentKopecks(0L, 500))
        assertNull(minPaymentKopecks(50_000_00L, null))
        assertNull(minPaymentKopecks(50_000_00L, 0))
    }
}
