package com.financeos.hub.core.analytics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InsightGeneratorTest {

    private lateinit var generator: InsightGenerator

    @Before
    fun setUp() {
        generator = InsightGenerator()
    }

    private fun data(
        currentExpense    : Long                   = 0L,
        currentIncome     : Long                   = 0L,
        lastMonthExpense  : Long                   = 0L,
        avgMonthlyExpense : Long                   = 0L,
        totalBalance      : Long                   = 0L,
        topCategoryDelta  : Pair<String, Double>?  = null,
        budgetAlerts      : List<Pair<String, Float>> = emptyList(),
    ) = InsightData(
        currentExpense    = currentExpense,
        currentIncome     = currentIncome,
        lastMonthExpense  = lastMonthExpense,
        avgMonthlyExpense = avgMonthlyExpense,
        totalBalance      = totalBalance,
        topCategoryDelta  = topCategoryDelta,
        budgetAlerts      = budgetAlerts,
    )

    // ── Rule 1: overspending vs last month ───────────────────────────────────

    @Test
    fun `overspend 30pct or more is CRITICAL`() {
        val insights = generator.generate(data(currentExpense = 130_000L, lastMonthExpense = 100_000L))
        assertTrue(insights.any { it.id == "overspend_critical" && it.severity == InsightSeverity.CRITICAL })
    }

    @Test
    fun `overspend exactly 30pct triggers CRITICAL not WARNING`() {
        val insights = generator.generate(data(currentExpense = 130_000L, lastMonthExpense = 100_000L))
        assertFalse(insights.any { it.id == "overspend_warn" })
        assertTrue(insights.any { it.id == "overspend_critical" })
    }

    @Test
    fun `overspend 15 to 29pct is WARNING`() {
        val insights = generator.generate(data(currentExpense = 120_000L, lastMonthExpense = 100_000L))
        assertTrue(insights.any { it.id == "overspend_warn" && it.severity == InsightSeverity.WARNING })
    }

    @Test
    fun `underspend 10pct or more is INFO`() {
        val insights = generator.generate(data(currentExpense = 85_000L, lastMonthExpense = 100_000L))
        assertTrue(insights.any { it.id == "underspend_good" && it.severity == InsightSeverity.INFO })
    }

    @Test
    fun `overspend 5pct generates no overspend insight`() {
        val insights = generator.generate(data(currentExpense = 105_000L, lastMonthExpense = 100_000L))
        assertFalse(insights.any { it.id.startsWith("overspend") })
    }

    @Test
    fun `underspend 5pct generates no underspend insight`() {
        val insights = generator.generate(data(currentExpense = 95_000L, lastMonthExpense = 100_000L))
        assertFalse(insights.any { it.id == "underspend_good" })
    }

    @Test
    fun `zero lastMonthExpense suppresses overspend insight`() {
        val insights = generator.generate(data(currentExpense = 50_000L, lastMonthExpense = 0L))
        assertFalse(insights.any { it.id.startsWith("overspend") || it.id == "underspend_good" })
    }

    // ── Rule 2: top category spike ───────────────────────────────────────────

    @Test
    fun `category spike at 40pct or more is WARNING`() {
        val insights = generator.generate(data(topCategoryDelta = "Еда" to 0.50))
        assertTrue(insights.any { it.id == "cat_spike" && it.severity == InsightSeverity.WARNING })
    }

    @Test
    fun `category spike exactly 40pct triggers WARNING`() {
        val insights = generator.generate(data(topCategoryDelta = "Еда" to 0.40))
        assertTrue(insights.any { it.id == "cat_spike" })
    }

    @Test
    fun `category spike below 40pct generates no insight`() {
        val insights = generator.generate(data(topCategoryDelta = "Еда" to 0.30))
        assertFalse(insights.any { it.id == "cat_spike" })
    }

    @Test
    fun `null topCategoryDelta generates no spike insight`() {
        val insights = generator.generate(data(topCategoryDelta = null))
        assertFalse(insights.any { it.id == "cat_spike" })
    }

    // ── Rule 3: negative savings rate ────────────────────────────────────────

    @Test
    fun `expense exceeds income is CRITICAL`() {
        val insights = generator.generate(data(currentIncome = 80_000L, currentExpense = 100_000L))
        assertTrue(insights.any { it.id == "negative_savings" && it.severity == InsightSeverity.CRITICAL })
    }

    @Test
    fun `expense equals income does not trigger negative savings`() {
        val insights = generator.generate(data(currentIncome = 100_000L, currentExpense = 100_000L))
        assertFalse(insights.any { it.id == "negative_savings" })
    }

    @Test
    fun `zero income suppresses negative savings insight`() {
        val insights = generator.generate(data(currentIncome = 0L, currentExpense = 100_000L))
        assertFalse(insights.any { it.id == "negative_savings" })
    }

    // ── Rule 4: savings milestone ────────────────────────────────────────────

    @Test
    fun `savings rate 30pct or more is INFO`() {
        val insights = generator.generate(data(currentIncome = 100_000L, currentExpense = 65_000L))
        assertTrue(insights.any { it.id == "savings_high" && it.severity == InsightSeverity.INFO })
    }

    @Test
    fun `savings rate 10 to 20pct is WARNING`() {
        val insights = generator.generate(data(currentIncome = 100_000L, currentExpense = 85_000L))
        assertTrue(insights.any { it.id == "savings_low" && it.severity == InsightSeverity.WARNING })
    }

    @Test
    fun `savings rate 21 to 29pct generates no savings insight`() {
        val insights = generator.generate(data(currentIncome = 100_000L, currentExpense = 75_000L))
        assertFalse(insights.any { it.id == "savings_high" || it.id == "savings_low" })
    }

    @Test
    fun `zero income suppresses savings insight`() {
        val insights = generator.generate(data(currentIncome = 0L, currentExpense = 0L))
        assertFalse(insights.any { it.id.startsWith("savings") })
    }

    // ── Rule 5: budget alerts ────────────────────────────────────────────────

    @Test
    fun `budget at 90pct or more is CRITICAL`() {
        val insights = generator.generate(data(budgetAlerts = listOf("Еда" to 0.95f)))
        assertTrue(insights.any { it.id == "budget_critical_Еда" && it.severity == InsightSeverity.CRITICAL })
    }

    @Test
    fun `budget at 80 to 89pct is WARNING`() {
        val insights = generator.generate(data(budgetAlerts = listOf("Транспорт" to 0.85f)))
        assertTrue(insights.any { it.id == "budget_warn_Транспорт" && it.severity == InsightSeverity.WARNING })
    }

    @Test
    fun `budget below 80pct generates no budget insight`() {
        val insights = generator.generate(data(budgetAlerts = listOf("Еда" to 0.70f)))
        assertFalse(insights.any { it.id.startsWith("budget_") })
    }

    @Test
    fun `multiple budget alerts each produce an insight`() {
        val insights = generator.generate(data(
            budgetAlerts = listOf("Еда" to 0.95f, "Транспорт" to 0.85f),
        ))
        assertEquals(1, insights.count { it.id == "budget_critical_Еда" })
        assertEquals(1, insights.count { it.id == "budget_warn_Транспорт" })
    }

    // ── Rule 6: emergency cushion ────────────────────────────────────────────

    @Test
    fun `cushion less than 1 month is CRITICAL`() {
        val insights = generator.generate(data(avgMonthlyExpense = 100_000L, totalBalance = 80_000L))
        assertTrue(insights.any { it.id == "cushion_critical" && it.severity == InsightSeverity.CRITICAL })
    }

    @Test
    fun `cushion 1 to 3 months is WARNING`() {
        val insights = generator.generate(data(avgMonthlyExpense = 100_000L, totalBalance = 200_000L))
        assertTrue(insights.any { it.id == "cushion_warn" && it.severity == InsightSeverity.WARNING })
    }

    @Test
    fun `cushion 3 or more months generates no cushion insight`() {
        val insights = generator.generate(data(avgMonthlyExpense = 100_000L, totalBalance = 350_000L))
        assertFalse(insights.any { it.id.startsWith("cushion") })
    }

    @Test
    fun `zero avgMonthlyExpense suppresses cushion insight`() {
        val insights = generator.generate(data(avgMonthlyExpense = 0L, totalBalance = 0L))
        assertFalse(insights.any { it.id.startsWith("cushion") })
    }

    // ── Sorting ──────────────────────────────────────────────────────────────

    @Test
    fun `CRITICAL insights precede WARNING and INFO`() {
        val insights = generator.generate(data(
            currentExpense    = 160_000L,
            lastMonthExpense  = 100_000L,
            currentIncome     = 150_000L,
            avgMonthlyExpense = 100_000L,
            totalBalance      = 50_000L,
            topCategoryDelta  = "Еда" to 0.50,
        ))
        val firstWarning  = insights.indexOfFirst { it.severity == InsightSeverity.WARNING }
        val lastCritical  = insights.indexOfLast  { it.severity == InsightSeverity.CRITICAL }
        if (firstWarning != -1 && lastCritical != -1) {
            assertTrue("CRITICAL must precede WARNING", lastCritical < firstWarning)
        }
    }

    @Test
    fun `empty data produces no insights`() {
        assertTrue(generator.generate(data()).isEmpty())
    }
}
