package com.financeos.hub.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers [buildSpendSeries] — the part of the daily-spend chart that decides what each column
 * actually means. The Canvas drawing itself is not covered (no instrumented tests in this
 * project), but the bucketing is where the old chart was factually wrong.
 */
class SpendTimelineTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun day(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun rub(n: Long) = n * 100L

    @Test
    fun `empty input yields no buckets`() {
        val series = buildSpendSeries(emptyList(), zone)
        assertTrue(series.buckets.isEmpty())
        assertEquals(0L, series.total)
        assertEquals(0L, series.average)
        assertEquals(null, series.peak)
    }

    /**
     * The core defect of the old chart: the source list only contains days that HAD expenses,
     * so plotting it directly drew 1 Mar and 10 Mar as neighbours.
     */
    @Test
    fun `missing days are filled with zeros so the x axis is real time`() {
        val series = buildSpendSeries(
            listOf(
                day(2026, 3, 1)  to rub(1_000),
                day(2026, 3, 10) to rub(500),
            ),
            zone,
        )
        assertEquals(SpendBucketSize.DAY, series.size)
        assertEquals(10, series.buckets.size)                       // 1..10 March inclusive
        assertEquals(rub(1_000), series.buckets.first().kopecks)
        assertEquals(rub(500), series.buckets.last().kopecks)
        assertTrue(series.buckets.subList(1, 9).all { it.kopecks == 0L })
    }

    @Test
    fun `several rows on the same calendar day are summed`() {
        val series = buildSpendSeries(
            listOf(
                day(2026, 3, 1) to rub(300),
                day(2026, 3, 1) to rub(200),
                day(2026, 3, 2) to rub(100),
            ),
            zone,
        )
        assertEquals(2, series.buckets.size)
        assertEquals(rub(500), series.buckets[0].kopecks)
        assertEquals(rub(600), series.total)
    }

    @Test
    fun `a span of up to 45 days stays per-day`() {
        val series = buildSpendSeries(
            listOf(day(2026, 1, 1) to rub(10), day(2026, 2, 14) to rub(10)),  // 45 days
            zone,
        )
        assertEquals(SpendBucketSize.DAY, series.size)
        assertEquals(45, series.buckets.size)
    }

    @Test
    fun `a longer span groups into weeks`() {
        val series = buildSpendSeries(
            listOf(day(2026, 1, 1) to rub(10), day(2026, 3, 1) to rub(10)),   // 60 days
            zone,
        )
        assertEquals(SpendBucketSize.WEEK, series.size)
        assertTrue(series.buckets.size in 8..10)
        assertEquals(rub(20), series.total)                                   // nothing lost
    }

    @Test
    fun `a multi-year span groups into calendar months`() {
        val series = buildSpendSeries(
            listOf(day(2024, 1, 15) to rub(700), day(2026, 3, 20) to rub(300)),
            zone,
        )
        assertEquals(SpendBucketSize.MONTH, series.size)
        assertEquals(27, series.buckets.size)                                 // Jan 2024 → Mar 2026
        assertEquals(rub(1_000), series.total)
        assertEquals(rub(700), series.buckets.first().kopecks)
        assertEquals(rub(300), series.buckets.last().kopecks)
    }

    @Test
    fun `weekly buckets are anchored to Monday and clipped to the data range`() {
        // 2026-01-01 is a Thursday; its week runs Mon 29 Dec 2025 … Sun 4 Jan 2026.
        val series = buildSpendSeries(
            listOf(day(2026, 1, 1) to rub(10), day(2026, 3, 1) to rub(10)),
            zone,
        )
        val firstBucketStart = LocalDate.of(2026, 1, 1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        // Clipped to the first day that actually has data, not back to the Monday.
        assertEquals(firstBucketStart, series.buckets.first().startMillis)
    }

    @Test
    fun `a week bucket sums every day inside it`() {
        val series = buildSpendSeries(
            listOf(
                day(2026, 1, 5) to rub(100),   // Mon
                day(2026, 1, 7) to rub(200),   // Wed — same week
                day(2026, 3, 1) to rub(50),    // forces WEEK bucketing
            ),
            zone,
        )
        assertEquals(SpendBucketSize.WEEK, series.size)
        val jan5Week = series.buckets.first { it.kopecks == rub(300) }
        assertEquals(rub(300), jan5Week.kopecks)
    }

    @Test
    fun `average divides by every bucket including the empty ones`() {
        // 1 000 ₽ spread over a 10-day span → 100 ₽/day, not 500 ₽/day.
        val series = buildSpendSeries(
            listOf(day(2026, 3, 1) to rub(600), day(2026, 3, 10) to rub(400)),
            zone,
        )
        assertEquals(rub(100), series.average)
    }

    @Test
    fun `peak is the biggest bucket and is null when nothing was spent`() {
        val series = buildSpendSeries(
            listOf(
                day(2026, 3, 1) to rub(100),
                day(2026, 3, 2) to rub(900),
                day(2026, 3, 3) to rub(50),
            ),
            zone,
        )
        assertEquals(rub(900), series.peak?.kopecks)

        val allZero = buildSpendSeries(listOf(day(2026, 3, 1) to 0L), zone)
        assertEquals(null, allZero.peak)
    }

    @Test
    fun `a single day produces exactly one bucket`() {
        val series = buildSpendSeries(listOf(day(2026, 3, 1) to rub(42)), zone)
        assertEquals(1, series.buckets.size)
        assertEquals(rub(42), series.total)
        assertEquals(rub(42), series.average)
    }

    @Test
    fun `unsorted input is still ordered chronologically`() {
        val series = buildSpendSeries(
            listOf(
                day(2026, 3, 3) to rub(30),
                day(2026, 3, 1) to rub(10),
                day(2026, 3, 2) to rub(20),
            ),
            zone,
        )
        assertEquals(listOf(rub(10), rub(20), rub(30)), series.buckets.map { it.kopecks })
    }
}
