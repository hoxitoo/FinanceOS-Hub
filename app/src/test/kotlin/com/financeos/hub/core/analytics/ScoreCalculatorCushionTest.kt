package com.financeos.hub.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the cushion pillar's treatment of credit-card debt.
 *
 * The pillar is what "how many months could I survive" reduces to, and credit accounts were newly
 * introduced into its inputs — so the case that matters most is the one where nothing changed:
 * a user without credit cards must score exactly what they scored before.
 */
class ScoreCalculatorCushionTest {

    private val calc = ScoreCalculator()

    private fun input(
        balance: Long,
        debt   : Long = 0L,
        avgExp : Long = 30_000_00L,
    ) = ScoreInput(
        monthlyIncome     = 100_000_00L,
        monthlyExpense    = 30_000_00L,
        mandatoryExpense  = 10_000_00L,
        avgMonthlyExpense = avgExp,
        totalBalance      = balance,
        last3MonthsIncome = listOf(100_000_00L, 100_000_00L, 100_000_00L),
        creditDebt        = debt,
    )

    @Test
    fun `no credit debt scores exactly as before the field existed`() {
        // 90 000 ₽ against a 30 000 ₽ month = 3 months of cushion = the full 25.
        assertEquals(25, calc.calculate(input(balance = 90_000_00L)).cushion)
        // 45 000 ₽ = 1.5 months → half.
        assertEquals(13, calc.calculate(input(balance = 45_000_00L)).cushion)
    }

    @Test
    fun `debt is subtracted from the cushion`() {
        // 90 000 ₽ cash but 45 000 ₽ owed leaves 1.5 months of real runway, not 3.
        assertEquals(13, calc.calculate(input(balance = 90_000_00L, debt = 45_000_00L)).cushion)
    }

    @Test
    fun `owing more than you hold scores zero rather than going negative`() {
        assertEquals(0, calc.calculate(input(balance = 30_000_00L, debt = 200_000_00L)).cushion)
    }

    @Test
    fun `no spending history means no cushion score to give`() {
        assertEquals(0, calc.calculate(input(balance = 90_000_00L, avgExp = 0L)).cushion)
    }

    @Test
    fun `the cushion never exceeds its 25 point cap`() {
        assertEquals(25, calc.calculate(input(balance = 5_000_000_00L)).cushion)
    }
}
