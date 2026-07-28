package com.financeos.hub.ui.components

import com.financeos.hub.ui.theme.FosFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How the raw per-day totals were grouped so the chart stays readable. */
enum class SpendBucketSize { DAY, WEEK, MONTH }

/** One column of the timeline: a calendar range and what was spent inside it. */
data class SpendBucket(
    val startMillis: Long,
    val endMillis  : Long,
    val kopecks    : Long,
    /** Short label for the x-axis, e.g. "12 июля" / "июль 2026". */
    val axisLabel  : String,
    /** Full label for the readout, e.g. "12–18 июля". */
    val rangeLabel : String,
)

data class SpendSeries(
    val buckets: List<SpendBucket>,
    val size   : SpendBucketSize,
) {
    val total  : Long get() = buckets.sumOf { it.kopecks }
    /** Mean over EVERY bucket, including the empty ones — that is the honest daily average. */
    val average: Long get() = if (buckets.isEmpty()) 0L else total / buckets.size
    val peak   : SpendBucket? get() = buckets.maxByOrNull { it.kopecks }?.takeIf { it.kopecks > 0 }
}

/** Above this many columns the bars get too thin to read on a phone, so we group coarser. */
private const val MAX_COLUMNS = 45

/**
 * Turns the raw "days that had expenses" list into evenly-spaced calendar columns.
 *
 * Two things this fixes over plotting the raw list directly:
 *  1. **Gaps become zeros.** The source is `groupBy { day }`, so days without a single
 *     expense are ABSENT, not zero. Plotting it as a continuous series put 24 June and
 *     3 July side by side as if they were consecutive — the x-axis was "index of a day
 *     that happened to have spending", not time, which silently distorted every shape.
 *  2. **Long periods are grouped.** «Год» / «Всё время» is 365–1000+ points squeezed into
 *     ~330dp; at that density individual columns are sub-pixel noise. Above [MAX_COLUMNS]
 *     days we aggregate into whole weeks, then whole calendar months.
 */
fun buildSpendSeries(
    daily: List<Pair<Long, Long>>,
    zone : ZoneId = ZoneId.systemDefault(),
): SpendSeries {
    if (daily.isEmpty()) return SpendSeries(emptyList(), SpendBucketSize.DAY)

    val byDate = sortedMapOf<LocalDate, Long>()
    daily.forEach { (millis, kopecks) ->
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        byDate[date] = (byDate[date] ?: 0L) + kopecks
    }
    val first = byDate.firstKey()
    val last  = byDate.lastKey()
    val spanDays = ChronoUnit.DAYS.between(first, last) + 1

    val size = when {
        spanDays <= MAX_COLUMNS          -> SpendBucketSize.DAY
        spanDays <= MAX_COLUMNS * 7L     -> SpendBucketSize.WEEK
        else                             -> SpendBucketSize.MONTH
    }

    fun millisOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMillisOf(d: LocalDate) =
        d.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    fun sumBetween(from: LocalDate, to: LocalDate) =
        byDate.subMap(from, to.plusDays(1)).values.sum()

    val buckets = when (size) {
        SpendBucketSize.DAY -> (0 until spanDays).map { i ->
            val d = first.plusDays(i)
            SpendBucket(
                startMillis = millisOf(d),
                endMillis   = endMillisOf(d),
                kopecks     = byDate[d] ?: 0L,
                axisLabel   = FosFormatter.dayLabel(millisOf(d)),
                rangeLabel  = FosFormatter.dayLabel(millisOf(d)),
            )
        }
        // Anchored to Monday so every column is a real calendar week; the first and last
        // are clipped to the data range so the chart never implies data it doesn't have.
        SpendBucketSize.WEEK -> {
            val out = mutableListOf<SpendBucket>()
            var weekStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
            while (!weekStart.isAfter(last)) {
                val from = maxOf(weekStart, first)
                val to   = minOf(weekStart.plusDays(6), last)
                out += SpendBucket(
                    startMillis = millisOf(from),
                    endMillis   = endMillisOf(to),
                    kopecks     = sumBetween(from, to),
                    axisLabel   = FosFormatter.dayLabel(millisOf(from)),
                    rangeLabel  = "${FosFormatter.dayLabel(millisOf(from))} — " +
                                  FosFormatter.dayLabel(millisOf(to)),
                )
                weekStart = weekStart.plusWeeks(1)
            }
            out
        }
        SpendBucketSize.MONTH -> {
            val out = mutableListOf<SpendBucket>()
            var month = YearMonth.from(first)
            val lastMonth = YearMonth.from(last)
            while (!month.isAfter(lastMonth)) {
                val from = maxOf(month.atDay(1), first)
                val to   = minOf(month.atEndOfMonth(), last)
                out += SpendBucket(
                    startMillis = millisOf(from),
                    endMillis   = endMillisOf(to),
                    kopecks     = sumBetween(from, to),
                    axisLabel   = FosFormatter.monthYear(millisOf(month.atDay(1))),
                    rangeLabel  = FosFormatter.monthYear(millisOf(month.atDay(1))),
                )
                month = month.plusMonths(1)
            }
            out
        }
    }
    return SpendSeries(buckets, size)
}
