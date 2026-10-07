package com.financeos.hub.features.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Карточка категории считает тот же период, что и строка, по которой нажали (#54). */
class AnalyticsWindowsTest {

    private val october = YearMonth.of(2026, 10)

    @Test
    fun `the year chip covers twelve whole months ending with this one`() {
        val w = AnalyticsWindows.current(AnalyticsPeriod.YEAR, october)
        assertEquals(LocalDate.of(2025, 11, 1), w.from)
        assertEquals(LocalDate.of(2026, 10, 31), w.to)
        // Сентябрь — внутри года, а не «прошлый месяц» вместо года.
        assertEquals(true, LocalDate.of(2026, 3, 15) in w.from!!..w.to)
    }

    @Test
    fun `the comparison is the equal stretch right before`() {
        val month = AnalyticsWindows.previous(AnalyticsPeriod.MONTH, october)!!
        assertEquals(LocalDate.of(2026, 9, 1), month.from)
        assertEquals(LocalDate.of(2026, 9, 30), month.to)
        val half = AnalyticsWindows.previous(AnalyticsPeriod.HALF_YEAR, october)!!
        assertEquals(LocalDate.of(2025, 11, 1), half.from)
        assertEquals(LocalDate.of(2026, 4, 30), half.to)
        val year = AnalyticsWindows.previous(AnalyticsPeriod.YEAR, october)!!
        assertEquals(LocalDate.of(2024, 11, 1), year.from)
        assertEquals(LocalDate.of(2025, 10, 31), year.to)
        // Окна не перекрываются и не оставляют дыры.
        assertEquals(AnalyticsWindows.current(AnalyticsPeriod.YEAR, october).from, year.to.plusDays(1))
    }

    @Test
    fun `all time has nothing to compare with`() {
        assertNull(AnalyticsWindows.current(AnalyticsPeriod.ALL, october).from)
        assertNull(AnalyticsWindows.previous(AnalyticsPeriod.ALL, october))
        assertNull(AnalyticsWindows.labels(AnalyticsPeriod.ALL).second)
    }
}
