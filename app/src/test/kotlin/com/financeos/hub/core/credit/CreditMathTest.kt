package com.financeos.hub.core.credit

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
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
