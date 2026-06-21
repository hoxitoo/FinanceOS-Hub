package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class BehavioralAnalyzer @Inject constructor() {

    private val zone = ZoneId.systemDefault()

    // ─── Heatmap ─────────────────────────────────────────────────────────────

    /**
     * Returns a 7×24 matrix of expense sums.
     * Index: [dayOfWeek 1..7 (Mon=1)] [hour 0..23]
     */
    fun computeHeatmap(transactions: List<TransactionEntity>): HeatmapData {
        val grid = Array(7) { LongArray(24) }
        transactions
            .filter { it.type == TransactionType.EXPENSE }
            .forEach { tx ->
                val ldt = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDateTime()
                val day  = ldt.dayOfWeek.value - 1   // 0=Mon … 6=Sun
                val hour = ldt.hour
                grid[day][hour] += abs(tx.amountKopecks)
            }
        val maxVal = grid.flatMap { it.toList() }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        return HeatmapData(grid = grid, maxKopecks = maxVal)
    }

    // ─── Payday Effect ────────────────────────────────────────────────────────

    /**
     * Finds income events > half the max income, then compares spending
     * in the 3 days after vs the 3-day windows one and two weeks before.
     * Returns null if fewer than 2 income events found.
     */
    fun detectPaydayEffect(transactions: List<TransactionEntity>): PaydayEffect? {
        val incomes = transactions
            .filter { it.type == TransactionType.INCOME }
            .sortedBy { it.timestamp }
        if (incomes.size < 2) return null

        val medianIncome = incomes.map { it.amountKopecks }.sorted()
            .let { it[it.size / 2] }

        val paydays = incomes.filter { it.amountKopecks >= medianIncome * 0.5 }
        if (paydays.isEmpty()) return null

        val windowMs = 3L * 24 * 60 * 60 * 1000   // 3 days in ms
        val weekMs   = 7L * 24 * 60 * 60 * 1000

        var totalPostKopecks  = 0L
        var totalBaseKopecks  = 0L
        var count             = 0

        paydays.forEach { payday ->
            val postStart = payday.timestamp
            val postEnd   = payday.timestamp + windowMs
            val base1Start= payday.timestamp - weekMs
            val base1End  = payday.timestamp - weekMs + windowMs
            val base2Start= payday.timestamp - 2 * weekMs
            val base2End  = payday.timestamp - 2 * weekMs + windowMs

            val post  = transactions.filter { it.type == TransactionType.EXPENSE
                && it.timestamp in postStart..postEnd }.sumOf { abs(it.amountKopecks) }
            val base1 = transactions.filter { it.type == TransactionType.EXPENSE
                && it.timestamp in base1Start..base1End }.sumOf { abs(it.amountKopecks) }
            val base2 = transactions.filter { it.type == TransactionType.EXPENSE
                && it.timestamp in base2Start..base2End }.sumOf { abs(it.amountKopecks) }
            val baseline = (base1 + base2) / 2.0

            if (baseline > 0) {
                totalPostKopecks  += post
                totalBaseKopecks  += baseline.toLong()
                count++
            }
        }
        if (count == 0) return null

        val avgPost     = totalPostKopecks / count
        val avgBaseline = totalBaseKopecks / count
        val ratio       = if (avgBaseline > 0) avgPost.toFloat() / avgBaseline else 1f
        return PaydayEffect(
            avgBaselineKopecks = avgBaseline,
            avgPostPaydayKopecks = avgPost,
            ratio = ratio,
        )
    }

    // ─── Budget Fatigue Curve ─────────────────────────────────────────────────

    /**
     * Returns average daily spending by day-of-month (1..31),
     * averaged across all months present in the transaction list.
     */
    fun computeFatigueCurve(transactions: List<TransactionEntity>): FatigueCurve {
        // group by (yearMonth, dayOfMonth)
        val byYearMonthDay = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx ->
                val ld = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                Triple(ld.year, ld.monthValue, ld.dayOfMonth)
            }

        val sumByDay     = mutableMapOf<Int, Long>()  // dayOfMonth → sum
        val countByDay   = mutableMapOf<Int, Int>()   // dayOfMonth → month count

        val monthsWithData = byYearMonthDay.keys.map { (y, m, _) -> y * 100 + m }.toSet()

        byYearMonthDay.forEach { (key, txList) ->
            val day = key.third
            sumByDay[day]   = (sumByDay[day]   ?: 0L) + txList.sumOf { abs(it.amountKopecks) }
            countByDay[day] = (countByDay[day] ?: 0) + 1
        }

        val averages = (1..31).map { day ->
            val sum   = sumByDay[day] ?: 0L
            val count = countByDay[day]?.coerceAtLeast(1) ?: 1
            day to sum / count
        }
        return FatigueCurve(dailyAverages = averages, monthCount = monthsWithData.size)
    }

    // ─── Impulse Classification ───────────────────────────────────────────────

    /**
     * Heuristic rules:
     *   Impulse  — amount < 2 000 ₽ AND hour in [21..23, 0..5]
     *   Planned  — amount > 5 000 ₽ AND hour in [8..12] AND weekday (Mon–Fri)
     *   Neutral  — everything else
     */
    fun classifyTransaction(tx: TransactionEntity): SpendingClass {
        if (tx.type != TransactionType.EXPENSE) return SpendingClass.NEUTRAL
        val ldt    = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDateTime()
        val hour   = ldt.hour
        val amount = abs(tx.amountKopecks)
        val isNight    = hour in 21..23 || hour in 0..5
        val isMorning  = hour in 8..12
        val isWeekday  = ldt.dayOfWeek.value in 1..5
        return when {
            amount < 200_000L && isNight               -> SpendingClass.IMPULSE
            amount > 500_000L && isMorning && isWeekday -> SpendingClass.PLANNED
            else                                        -> SpendingClass.NEUTRAL
        }
    }

    fun computeImpulseStats(transactions: List<TransactionEntity>): ImpulseStats {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val impulse  = expenses.filter { classifyTransaction(it) == SpendingClass.IMPULSE }
        val planned  = expenses.filter { classifyTransaction(it) == SpendingClass.PLANNED }
        val total    = expenses.size
        return ImpulseStats(
            impulseCount   = impulse.size,
            plannedCount   = planned.size,
            neutralCount   = (total - impulse.size - planned.size).coerceAtLeast(0),
            impulsePercent = if (total > 0) impulse.size.toFloat() / total else 0f,
            impulseKopecks = impulse.sumOf { abs(it.amountKopecks) },
            totalKopecks   = expenses.sumOf { abs(it.amountKopecks) },
        )
    }

    // ─── Category Anomalies ───────────────────────────────────────────────────

    /**
     * Compares current-month expense per category vs rolling 3-month avg.
     * Returns categories where current > avg * threshold.
     */
    fun detectCategoryAnomalies(
        currentMonth : Map<String, Long>,   // categoryId → kopecks
        history      : List<Map<String, Long>>,  // previous months, newest first
        threshold    : Float = 1.30f,
    ): List<CategoryAnomaly> {
        if (history.isEmpty()) return emptyList()
        val anomalies = mutableListOf<CategoryAnomaly>()

        currentMonth.forEach { (catId, current) ->
            val historicalVals = history.mapNotNull { it[catId] }.filter { it > 0 }
            if (historicalVals.isEmpty()) return@forEach
            val avg    = historicalVals.average().toLong()
            // Square in Double space — (it - avg)² in Long can overflow for large kopeck sums.
            val stdDev = historicalVals.map { val d = (it - avg).toDouble(); d * d }
                .average().let { sqrt(it).toLong() }
            if (current > avg * threshold) {
                anomalies += CategoryAnomaly(
                    categoryId    = catId,
                    currentKopecks= current,
                    avgKopecks    = avg,
                    deltaRatio    = current.toFloat() / avg.coerceAtLeast(1),
                    stdDevKopecks = stdDev,
                )
            }
        }
        return anomalies.sortedByDescending { it.deltaRatio }
    }

    /**
     * Finds categories with regular monthly activity that went silent this month.
     * "Regular" = present in at least 3 of last 4 months.
     */
    fun detectSubscriptionGaps(
        currentMonth: Map<String, Long>,
        history     : List<Map<String, Long>>,
    ): List<String> {
        if (history.size < 3) return emptyList()
        return history.take(4)
            .flatMap { it.keys }
            .groupingBy { it }
            .eachCount()
            .filter { (catId, count) -> count >= 3 && !currentMonth.containsKey(catId) }
            .keys
            .toList()
    }

    // ─── Fixed vs Variable ────────────────────────────────────────────────────

    /**
     * A category is "fixed" if its monthly spend varies < 15% over 3+ months.
     */
    fun classifyFixedVariable(history: List<Map<String, Long>>): FixedVariableResult {
        if (history.size < 3) return FixedVariableResult(emptySet(), emptySet())
        val allCats = history.flatMap { it.keys }.toSet()
        val fixed   = mutableSetOf<String>()
        val variable= mutableSetOf<String>()

        allCats.forEach { catId ->
            val vals = history.mapNotNull { it[catId] }.filter { it > 0 }
            if (vals.size < 3) { variable += catId; return@forEach }
            val avg = vals.average()
            val cv  = if (avg > 0) sqrt(vals.map { (it - avg) * (it - avg) }.average()) / avg else 1.0
            if (cv <= 0.15) fixed += catId else variable += catId
        }
        return FixedVariableResult(fixed = fixed, variable = variable)
    }
}

// ─── Data classes ─────────────────────────────────────────────────────────────

data class HeatmapData(
    val grid       : Array<LongArray>,  // [7][24] — dayOfWeek × hour, expense kopecks
    val maxKopecks : Long,
) {
    fun intensity(day: Int, hour: Int): Float =
        if (maxKopecks > 0) grid[day][hour].toFloat() / maxKopecks else 0f
}

data class PaydayEffect(
    val avgBaselineKopecks   : Long,
    val avgPostPaydayKopecks : Long,
    val ratio                : Float,   // 1.4 = 40% more spending post-payday
) {
    val deltaPercent: Int get() = ((ratio - 1f) * 100).toInt()
    val isSignificant: Boolean get() = ratio >= 1.20f
}

data class FatigueCurve(
    val dailyAverages : List<Pair<Int, Long>>,  // dayOfMonth → avgKopecks
    val monthCount    : Int,
)

enum class SpendingClass { IMPULSE, PLANNED, NEUTRAL }

data class ImpulseStats(
    val impulseCount   : Int,
    val plannedCount   : Int,
    val neutralCount   : Int,
    val impulsePercent : Float,
    val impulseKopecks : Long,
    val totalKopecks   : Long,
) {
    val impulseShareOfSpend: Float
        get() = if (totalKopecks > 0) impulseKopecks.toFloat() / totalKopecks else 0f
}

data class CategoryAnomaly(
    val categoryId    : String,
    val currentKopecks: Long,
    val avgKopecks    : Long,
    val deltaRatio    : Float,
    val stdDevKopecks : Long,
) {
    val deltaPercent: Int get() = ((deltaRatio - 1f) * 100).toInt()
}

data class FixedVariableResult(
    val fixed   : Set<String>,
    val variable: Set<String>,
)
