package com.financeos.hub.core.finance

import com.financeos.hub.core.finance.SavingsMath.Compounding
import com.financeos.hub.core.finance.SavingsMath.Plan
import com.financeos.hub.core.finance.SavingsMath.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

/**
 * Эти числа пользователь принимает за факт и планирует по ним годы жизни, поэтому проверяются не
 * «примерно похоже», а точные значения там, где их можно посчитать на бумаге, и строгие
 * неравенства там, где важно направление (капитализация чаще → больше; инфляция → меньше).
 */
class SavingsMathTest {

    private fun plan(
        initial   : Long = 0L,
        monthly   : Long = 0L,
        months    : Int  = 12,
        rateBp    : Int  = 0,
        comp      : Compounding = Compounding.Monthly,
        timing    : Timing = Timing.End,
        growthBp  : Int  = 0,
        inflBp    : Int  = 0,
        taxBp     : Int  = 0,
    ) = Plan(
        initialKopecks       = initial,
        monthlyKopecks       = monthly,
        months               = months,
        annualRateBp         = rateBp,
        compounding          = comp,
        timing               = timing,
        contributionGrowthBp = growthBp,
        inflationBp          = inflBp,
        taxBp                = taxBp,
    )

    // ── Копилка без процентов ────────────────────────────────────────────────────

    @Test
    fun `zero rate is plain addition`() {
        val r = SavingsMath.project(plan(monthly = 1_000_00L, months = 12))
        assertEquals(12_000_00L, r.finalKopecks)
        assertEquals(12_000_00L, r.contributedKopecks)
        assertEquals(0L, r.interestKopecks)
    }

    @Test
    fun `initial deposit counts as contributed, not as income`() {
        val r = SavingsMath.project(plan(initial = 50_000_00L, monthly = 0L, months = 12))
        assertEquals(50_000_00L, r.finalKopecks)
        assertEquals(50_000_00L, r.contributedKopecks)
        assertEquals(0L, r.interestKopecks)
    }

    // ── Капитализация ────────────────────────────────────────────────────────────

    @Test
    fun `monthly compounding matches the textbook figure`() {
        // 100 000 ₽ под 12 % с ежемесячной капитализацией за год: 100000 × 1,01^12 = 112 682,50 ₽
        val r = SavingsMath.project(plan(initial = 100_000_00L, months = 12, rateBp = 1200))
        assertEquals(112_682_50L, r.finalKopecks)
        assertEquals(100_000_00L, r.contributedKopecks)
        assertEquals(12_682_50L, r.interestKopecks)
    }

    @Test
    fun `without compounding interest is simple`() {
        // Проценты не присоединяются к телу, значит ровно 1 % × 12 месяцев от неизменных 100 000 ₽.
        val r = SavingsMath.project(
            plan(initial = 100_000_00L, months = 12, rateBp = 1200, comp = Compounding.None)
        )
        assertEquals(112_000_00L, r.finalKopecks)
    }

    @Test
    fun `more frequent compounding earns more`() {
        val base = plan(initial = 100_000_00L, months = 36, rateBp = 1500)
        val monthly   = SavingsMath.project(base.copy(compounding = Compounding.Monthly)).finalKopecks
        val quarterly = SavingsMath.project(base.copy(compounding = Compounding.Quarterly)).finalKopecks
        val annually  = SavingsMath.project(base.copy(compounding = Compounding.Annually)).finalKopecks
        val none      = SavingsMath.project(base.copy(compounding = Compounding.None)).finalKopecks
        assertTrue(monthly > quarterly)
        assertTrue(quarterly > annually)
        assertTrue(annually > none)
    }

    @Test
    fun `effective rate exceeds nominal when interest compounds`() {
        assertEquals(12.6825, SavingsMath.effectiveAnnualRatePercent(1200, Compounding.Monthly), 0.001)
        // Без капитализации эффективная ставка равна номинальной — нечему нарастать.
        assertEquals(12.0, SavingsMath.effectiveAnnualRatePercent(1200, Compounding.None), 0.0001)
        assertEquals(0.0, SavingsMath.effectiveAnnualRatePercent(0, Compounding.Monthly), 0.0001)
    }

    // ── Момент взноса ────────────────────────────────────────────────────────────

    @Test
    fun `contribution at the start of the month earns one more month of interest`() {
        val base = plan(monthly = 10_000_00L, months = 24, rateBp = 1200)
        val atStart = SavingsMath.project(base.copy(timing = Timing.Start)).finalKopecks
        val atEnd   = SavingsMath.project(base.copy(timing = Timing.End)).finalKopecks
        assertTrue(atStart > atEnd)
        // Вложено при этом одинаково — разница только в процентах.
        assertEquals(
            SavingsMath.project(base.copy(timing = Timing.Start)).contributedKopecks,
            SavingsMath.project(base.copy(timing = Timing.End)).contributedKopecks,
        )
    }

    // ── Индексация взноса ────────────────────────────────────────────────────────

    @Test
    fun `contribution indexation raises the total and is applied yearly, not monthly`() {
        val flat    = SavingsMath.project(plan(monthly = 10_000_00L, months = 24))
        val indexed = SavingsMath.project(plan(monthly = 10_000_00L, months = 24, growthBp = 1000))
        // Первые 12 месяцев по 10 000, следующие 12 — по 11 000.
        assertEquals(120_000_00L + 132_000_00L, indexed.contributedKopecks)
        assertTrue(indexed.finalKopecks > flat.finalKopecks)
    }

    // ── Инфляция и налог ─────────────────────────────────────────────────────────

    @Test
    fun `inflation only deflates the headline, never the contributions`() {
        val p = plan(initial = 100_000_00L, months = 120, rateBp = 1000, inflBp = 700)
        val r = SavingsMath.project(p)
        val without = SavingsMath.project(p.copy(inflationBp = 0))
        assertEquals(without.finalKopecks, r.finalKopecks)          // номинал не трогаем
        assertTrue(r.realFinalKopecks < r.finalKopecks)             // покупательная способность ниже
    }

    @Test
    fun `tax is charged on interest only`() {
        val r = SavingsMath.project(plan(initial = 100_000_00L, months = 12, rateBp = 1200, taxBp = 1300))
        assertEquals((r.interestKopecks * 1300 / 10_000.0).roundToLong(), r.taxKopecks)
        assertEquals(r.interestKopecks - r.taxKopecks, r.netInterestKopecks)
    }

    @Test
    fun `no interest means no tax`() {
        val r = SavingsMath.project(plan(monthly = 5_000_00L, months = 12, taxBp = 1300))
        assertEquals(0L, r.taxKopecks)
    }

    // ── Разбивка по годам ────────────────────────────────────────────────────────

    @Test
    fun `schedule has one point per year and its parts add up`() {
        val r = SavingsMath.project(plan(monthly = 10_000_00L, months = 36, rateBp = 1200))
        assertEquals(3, r.schedule.size)
        assertEquals(listOf(1, 2, 3), r.schedule.map { it.year })
        val last = r.schedule.last()
        assertEquals(r.finalKopecks, last.balanceKopecks)
        assertEquals(
            last.balanceKopecks,
            last.contributedTotalKopecks + last.interestTotalKopecks,
        )
    }

    @Test
    fun `a trailing partial year still gets a point`() {
        val r = SavingsMath.project(plan(monthly = 10_000_00L, months = 30, rateBp = 1200))
        assertEquals(3, r.schedule.size)
        assertEquals(3, r.schedule.last().year)
        assertEquals(r.finalKopecks, r.schedule.last().balanceKopecks)
    }

    @Test
    fun `crossover is the year interest first outpaces contributions`() {
        // Крупный старт под высокую ставку при скромном взносе — перелом наступает сразу.
        val r = SavingsMath.project(
            plan(initial = 5_000_000_00L, monthly = 1_000_00L, months = 60, rateBp = 1500)
        )
        assertEquals(1, r.crossoverYear)
        // А без ставки не наступает никогда.
        val flat = SavingsMath.project(plan(monthly = 10_000_00L, months = 240))
        assertNull(flat.crossoverYear)
    }

    // ── Обратная задача: сколько копить ─────────────────────────────────────────

    @Test
    fun `months to reach without a rate is plain division`() {
        val m = SavingsMath.monthsToReach(120_000_00L, plan(monthly = 10_000_00L, months = 0))
        assertEquals(12, m)
    }

    @Test
    fun `a rate shortens the wait`() {
        val target = 1_000_000_00L
        val flat = SavingsMath.monthsToReach(target, plan(monthly = 20_000_00L))!!
        val paid = SavingsMath.monthsToReach(target, plan(monthly = 20_000_00L, rateBp = 1500))!!
        assertTrue(paid < flat)
    }

    @Test
    fun `already enough means zero months, not one`() {
        assertEquals(0, SavingsMath.monthsToReach(50_000_00L, plan(initial = 60_000_00L, monthly = 1_00L)))
    }

    @Test
    fun `never reachable returns null rather than a huge number`() {
        // Ни взноса, ни ставки — сумма не растёт. Ответ «никогда», а не «через 600 месяцев».
        assertNull(SavingsMath.monthsToReach(100_000_00L, plan(initial = 1_000_00L)))
        // Взнос есть, но горизонта в 50 лет не хватает.
        assertNull(SavingsMath.monthsToReach(1_000_000_000_00L, plan(monthly = 100_00L)))
    }

    // ── Обратная задача: сколько откладывать ────────────────────────────────────

    @Test
    fun `required monthly without a rate is plain division`() {
        val need = SavingsMath.requiredMonthly(120_000_00L, plan(months = 12))
        assertEquals(10_000_00L, need)
    }

    @Test
    fun `required monthly actually reaches the target`() {
        val target = 3_000_000_00L
        val base   = plan(initial = 200_000_00L, months = 60, rateBp = 1400)
        val need   = SavingsMath.requiredMonthly(target, base)!!
        val got    = SavingsMath.project(base.copy(monthlyKopecks = need)).finalKopecks
        assertTrue("накопили $got, нужно $target", got >= target)
        // И не с запасом в целый взнос — округление вверх на копейки, не на тысячи.
        val short = SavingsMath.project(base.copy(monthlyKopecks = need - 1_00L)).finalKopecks
        assertTrue(short < target)
    }

    @Test
    fun `required monthly is zero when the initial deposit alone gets there`() {
        assertEquals(0L, SavingsMath.requiredMonthly(100_000_00L, plan(initial = 150_000_00L, months = 12)))
    }

    @Test
    fun `required monthly needs a horizon`() {
        assertNull(SavingsMath.requiredMonthly(100_000_00L, plan(months = 0)))
    }

    // ── Границы ─────────────────────────────────────────────────────────────────

    @Test
    fun `horizon is clamped instead of running away`() {
        val r = SavingsMath.project(plan(monthly = 1_000_00L, months = 10_000, rateBp = 1000))
        assertEquals(SavingsMath.MAX_MONTHS / 12, r.schedule.size)
        assertNotNull(r.crossoverYear)
    }

    @Test
    fun `zero months yields the initial deposit untouched`() {
        val r = SavingsMath.project(plan(initial = 77_000_00L, monthly = 5_000_00L, months = 0, rateBp = 1500))
        assertEquals(77_000_00L, r.finalKopecks)
        assertTrue(r.schedule.isEmpty())
    }
}
