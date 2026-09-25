package com.financeos.hub.core.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Расчёт по цели. «Сегодня» передаётся параметром, поэтому тесты не зависят от даты прогона —
 * иначе набор бы позеленел сегодня и покраснел первого числа следующего месяца.
 */
class GoalPlanTest {

    private val today = LocalDate.of(2026, 1, 15)

    private fun ms(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `required monthly splits what is left over the months that remain`() {
        // Нужно 120 000, отложено 20 000, до срока ровно 10 месяцев → 10 000 в месяц.
        val o = GoalPlan.outlook(
            savedKopecks  = 20_000_00L,
            targetKopecks = 120_000_00L,
            startedAt     = null,
            deadlineAt    = ms(2026, 11, 15),
            paceKopecks   = null,
            today         = today,
        )
        assertEquals(100_000_00L, o.remainingKopecks)
        assertEquals(10, o.monthsLeft)
        assertEquals(10_000_00L, o.requiredMonthly)
    }

    @Test
    fun `the required amount is rounded UP, never down`() {
        // 100 ₽ за 3 месяца — 33,33: округление вниз оставило бы цель недобранной ровно в срок.
        val o = GoalPlan.outlook(
            savedKopecks  = 0L,
            targetKopecks = 100_00L,
            startedAt     = null,
            deadlineAt    = ms(2026, 4, 15),
            paceKopecks   = null,
            today         = today,
        )
        assertEquals(3, o.monthsLeft)
        assertEquals(33_34L, o.requiredMonthly)
    }

    @Test
    fun `a deadline that has passed asks for the whole remainder at once`() {
        // Ноль месяцев — делить нельзя, а «0 ₽ в месяц» означало бы, что всё в порядке.
        val o = GoalPlan.outlook(
            savedKopecks  = 10_000_00L,
            targetKopecks = 50_000_00L,
            startedAt     = null,
            deadlineAt    = ms(2025, 12, 1),
            paceKopecks   = 5_000_00L,
            today         = today,
        )
        assertEquals(0, o.monthsLeft)
        assertEquals(40_000_00L, o.requiredMonthly)
        assertFalse("просроченный срок не может быть «в графике»", o.onTrack!!)
    }

    @Test
    fun `without a deadline there is no required monthly, but the pace still answers when`() {
        val o = GoalPlan.outlook(
            savedKopecks  = 0L,
            targetKopecks = 60_000_00L,
            startedAt     = null,
            deadlineAt    = null,
            paceKopecks   = 10_000_00L,
            today         = today,
        )
        assertNull(o.monthsLeft)
        assertNull(o.requiredMonthly)
        assertEquals(6, o.monthsAtCurrentPace)
        assertNull("сравнивать не с чем — срока нет", o.onTrack)
    }

    @Test
    fun `pace at or above the requirement is on track`() {
        val o = GoalPlan.outlook(
            savedKopecks  = 0L,
            targetKopecks = 100_000_00L,
            startedAt     = null,
            deadlineAt    = ms(2026, 11, 15),
            paceKopecks   = 10_000_00L,
            today         = today,
        )
        assertEquals(10_000_00L, o.requiredMonthly)
        assertTrue("темп ровно равен требуемому — это успевание, а не отставание", o.onTrack!!)
    }

    @Test
    fun `a reached goal plans nothing`() {
        val o = GoalPlan.outlook(
            savedKopecks  = 200_000_00L,
            targetKopecks = 150_000_00L,
            startedAt     = null,
            deadlineAt    = ms(2026, 6, 1),
            paceKopecks   = 1_000_00L,
            today         = today,
        )
        assertEquals(0L, o.remainingKopecks)
        assertNull("«откладывайте 0 ₽» — не совет", o.requiredMonthly)
        assertEquals(0, o.monthsAtCurrentPace)
        assertTrue(o.onTrack!!)
    }

    @Test
    fun `a zero or negative pace is not a pace`() {
        val o = GoalPlan.outlook(
            savedKopecks  = 0L,
            targetKopecks = 50_000_00L,
            startedAt     = null,
            deadlineAt    = null,
            paceKopecks   = 0L,
            today         = today,
        )
        assertNull(o.monthsAtCurrentPace)
        assertNull(o.onTrack)
    }

    @Test
    fun `elapsed share needs both ends and never leaves 0 - 1`() {
        // Начало 1 января, срок 31 декабря, сегодня 15 января — около 4 % срока.
        val o = GoalPlan.outlook(
            savedKopecks  = 0L,
            targetKopecks = 100_000_00L,
            startedAt     = ms(2026, 1, 1),
            deadlineAt    = ms(2026, 12, 31),
            paceKopecks   = null,
            today         = today,
        )
        val share = o.elapsedShare!!
        assertTrue("доля срока вышла за пределы: $share", share in 0f..1f)
        assertTrue("две недели из года — это меньше десятой части", share < 0.10f)

        // Без даты начала сравнивать нечего: приложение не знает, когда человек начал копить.
        val noStart = GoalPlan.outlook(
            savedKopecks = 0L, targetKopecks = 100_000_00L,
            startedAt = null, deadlineAt = ms(2026, 12, 31),
            paceKopecks = null, today = today,
        )
        assertNull(noStart.elapsedShare)
    }

    @Test
    fun `a pace that would take fifty years is not reported as a plan`() {
        val o = GoalPlan.outlook(
            savedKopecks  = 0L,
            targetKopecks = 10_000_000_00L,
            startedAt     = null,
            deadlineAt    = null,
            paceKopecks   = 100L,          // 1 ₽ в месяц
            today         = today,
        )
        assertNull("недостижимый темп возвращает null, а не 600 месяцев", o.monthsAtCurrentPace)
    }
}
