package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.ml.BehavioralCluster
import com.financeos.hub.core.ml.SpendingPredictor
import java.time.Instant
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AnalyticsEngine @Inject constructor(
    private val txDao             : TransactionDao,
    private val accountDao        : AccountDao,
    private val budgetDao         : BudgetDao,
    private val categoryDao       : CategoryDao,
    private val scoreCalculator   : ScoreCalculator,
    private val insightGenerator  : InsightGenerator,
    private val behavioralAnalyzer: BehavioralAnalyzer,
    private val narrativeEngine   : NarrativeEngine,
    private val behavioralCluster : BehavioralCluster,
    private val spendingPredictor : SpendingPredictor,
) {
    private val zone = ZoneId.systemDefault()

    // ── Score & Insights ─────────────────────────────────────────────────────

    suspend fun computeScore(): ScoreCalculator.ScoreBreakdown =
        scoreCalculator.calculate(buildScoreInput())

    suspend fun generateInsights(): List<Insight> =
        insightGenerator.generate(buildInsightData())

    // ── Sparkline & Forecast ─────────────────────────────────────────────────

    suspend fun sparkline30Days(): List<Float> {
        val now  = System.currentTimeMillis()
        val from = now - 30L * 24 * 60 * 60 * 1000
        val txs  = getTxSync(from, now).filter { it.type == TransactionType.EXPENSE }

        val dailyMap = mutableMapOf<Long, Long>()
        txs.forEach { tx ->
            val day = Instant.ofEpochMilli(tx.timestamp)
                .atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
            dailyMap[day] = (dailyMap[day] ?: 0L) + abs(tx.amountKopecks)
        }

        val result = mutableListOf<Float>()
        var cursor = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val today  = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        while (!cursor.isAfter(today)) {
            result += (dailyMap[cursor.atStartOfDay(zone).toInstant().toEpochMilli()] ?: 0L).toFloat()
            cursor = cursor.plusDays(1)
        }
        return result
    }

    suspend fun forecastMonthEnd(): Long {
        val month = YearMonth.now()
        val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now   = System.currentTimeMillis()

        val txs = getTxSync(from, now).filter { it.type == TransactionType.EXPENSE }
        val daily = txs
            .groupBy { tx -> Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli() }
            .map { (day, list) -> day to list.sumOf { abs(it.amountKopecks) } }
            .sortedBy { it.first }

        val daysPassed   = ((now - from) / (24 * 60 * 60 * 1000)).coerceAtLeast(1).toInt()
        val daysInMonth  = month.lengthOfMonth()
        val daysRemaining = (daysInMonth - daysPassed).coerceAtLeast(0)

        val spentSoFar   = txs.sumOf { abs(it.amountKopecks) }
        val mlForecast   = spendingPredictor.predict(daily, daysRemaining)
        return spentSoFar + mlForecast
    }

    suspend fun classifyBehavior(): BehavioralCluster.ClusterResult {
        val now  = System.currentTimeMillis()
        val from = now - 90L * 24 * 60 * 60 * 1000
        return behavioralCluster.classify(getTxSync(from, now))
    }

    // ── Behavioral Analytics ─────────────────────────────────────────────────

    suspend fun computeHeatmap(): HeatmapData {
        val now  = System.currentTimeMillis()
        val from = now - 90L * 24 * 60 * 60 * 1000   // 90 days for meaningful data
        return behavioralAnalyzer.computeHeatmap(getTxSync(from, now))
    }

    suspend fun detectPaydayEffect(): PaydayEffect? {
        val now  = System.currentTimeMillis()
        val from = now - 90L * 24 * 60 * 60 * 1000
        return behavioralAnalyzer.detectPaydayEffect(getTxSync(from, now))
    }

    suspend fun computeFatigueCurve(): FatigueCurve {
        val now  = System.currentTimeMillis()
        val from = now - 90L * 24 * 60 * 60 * 1000
        return behavioralAnalyzer.computeFatigueCurve(getTxSync(from, now))
    }

    suspend fun computeImpulseStats(): ImpulseStats {
        val month = YearMonth.now()
        val (from, to) = monthBounds(month)
        return behavioralAnalyzer.computeImpulseStats(getTxSync(from, to))
    }

    suspend fun detectCategoryAnomalies(): List<CategoryAnomaly> {
        val month = YearMonth.now()
        val catNames = categoryDao.getAll().associate { it.id to it.name }
        val current  = categoryExpenseMap(month)
        val history  = (1..3).map { offset -> categoryExpenseMap(month.minusMonths(offset.toLong())) }
        return behavioralAnalyzer.detectCategoryAnomalies(current, history)
    }

    suspend fun detectSubscriptionGaps(): List<String> {
        val month   = YearMonth.now()
        val current = categoryExpenseMap(month)
        val history = (1..4).map { offset -> categoryExpenseMap(month.minusMonths(offset.toLong())) }
        return behavioralAnalyzer.detectSubscriptionGaps(current, history)
    }

    suspend fun classifyFixedVariable(): FixedVariableResult {
        val month   = YearMonth.now()
        val history = (0..5).map { offset -> categoryExpenseMap(month.minusMonths(offset.toLong())) }
        return behavioralAnalyzer.classifyFixedVariable(history)
    }

    suspend fun currentMonthCategoryExpenses(): Map<String, Long> {
        val (from, to) = monthBounds(YearMonth.now())
        return expenseByCat(getTxSync(from, to))
    }

    suspend fun computeWaterfallBars(): List<WaterfallBar> {
        val month = YearMonth.now()
        val catNames  = categoryDao.getAll().associate { it.id to it.name }
        val (fromCur, toCur)   = monthBounds(month)
        val (fromPrev, toPrev) = monthBounds(month.minusMonths(1))

        val curTxs  = getTxSync(fromCur, toCur)
        val prevTxs = getTxSync(fromPrev, toPrev)

        val curIncome  = curTxs.filter  { it.type == TransactionType.INCOME  }.sumOf { it.amountKopecks }
        val prevIncome = prevTxs.filter { it.type == TransactionType.INCOME  }.sumOf { it.amountKopecks }
        val curCatMap  = expenseByCat(curTxs)
        val prevCatMap = expenseByCat(prevTxs)
        val allCats    = (curCatMap.keys + prevCatMap.keys).toSet()

        val bars = mutableListOf<WaterfallBar>()
        val incomeDelta = curIncome - prevIncome
        if (abs(incomeDelta) > 0)
            bars += WaterfallBar("Доходы", incomeDelta)

        allCats
            .sortedByDescending { abs((curCatMap[it] ?: 0L) - (prevCatMap[it] ?: 0L)) }
            .take(5)
            .forEach { catId ->
                val delta = (prevCatMap[catId] ?: 0L) - (curCatMap[catId] ?: 0L) // positive = spent less
                if (abs(delta) > 10_00L) { // threshold 10₽
                    bars += WaterfallBar(catNames[catId] ?: "Другое", delta)
                }
            }

        val totalDelta = (curIncome - curTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }) -
                         (prevIncome - prevTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) })
        bars += WaterfallBar("Итог", totalDelta, isTotal = true)
        return bars
    }

    suspend fun generateNarratives(): List<NarrativeInsight> {
        val now  = System.currentTimeMillis()
        val from = now - 90L * 24 * 60 * 60 * 1000
        val all  = getTxSync(from, now)

        val zone    = this.zone
        val month   = YearMonth.now()

        // Category avg per day over 90 days
        val catAvgPerDay = expenseByCat(all).mapValues { (_, total) -> total / 90L }

        // Savings rate history (last 3 months)
        val savingsHistory = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            val txs    = getTxSync(f, t)
            val inc    = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountKopecks }.toFloat()
            val exp    = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }.toFloat()
            if (inc > 0) (inc - exp) / inc else 0f
        }

        // Best month (highest savings rate, last 12 months)
        val bestMonth = (0..11).mapNotNull { offset ->
            val m      = month.minusMonths(offset.toLong())
            val (f, t) = monthBounds(m)
            val txs    = getTxSync(f, t)
            val inc    = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountKopecks }.toFloat()
            val exp    = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }.toFloat()
            if (inc > 0) {
                val rate  = (inc - exp) / inc
                Pair(m.toString(), rate) to offset
            } else null
        }.maxByOrNull { it.first.second }?.first

        // Weekend vs weekday ratio
        val expensesByDay = all.filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx -> Instant.ofEpochMilli(tx.timestamp).atZone(zone).dayOfWeek.value }
        val weekdayAvg = (1..5).mapNotNull { expensesByDay[it] }
            .flatten().sumOf { abs(it.amountKopecks) }
            .toFloat() / 5f.coerceAtLeast(1f)
        val weekendAvg = (6..7).mapNotNull { expensesByDay[it] }
            .flatten().sumOf { abs(it.amountKopecks) }
            .toFloat() / 2f.coerceAtLeast(1f)
        val weekendRatio = if (weekdayAvg > 0) weekendAvg / weekdayAvg else null

        // Top merchant
        val topMerchant = all.filter { it.type == TransactionType.EXPENSE && it.merchant != null }
            .groupBy { it.merchant!! }
            .mapValues { (_, list) -> list.sumOf { abs(it.amountKopecks) } }
            .maxByOrNull { it.value }
            ?.toPair()

        val input = NarrativeInput(
            allTransactions       = all,
            categoryAveragePerDay = catAvgPerDay,
            savingsRateHistory    = savingsHistory,
            bestMonth             = bestMonth,
            paydayEffect          = detectPaydayEffect(),
            impulseStats          = computeImpulseStats(),
            topMerchant           = topMerchant,
            weekendVsWeekday      = weekendRatio,
        )
        return narrativeEngine.generate(input)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun monthBounds(m: YearMonth): Pair<Long, Long> {
        val from = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to   = m.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return from to to
    }

    private suspend fun categoryExpenseMap(month: YearMonth): Map<String, Long> {
        val (from, to) = monthBounds(month)
        return expenseByCat(getTxSync(from, to))
    }

    private fun expenseByCat(txs: List<TransactionEntity>): Map<String, Long> =
        txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId ?: "cat_other" }
            .mapValues { (_, list) -> list.sumOf { abs(it.amountKopecks) } }

    private suspend fun buildScoreInput(): ScoreInput {
        val month = YearMonth.now()
        val (fromCur, toCur) = monthBounds(month)
        val curTxs    = getTxSync(fromCur, toCur)
        val curIncome = curTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountKopecks }
        val curExpense= curTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }

        val mandatoryCats = setOf("cat_housing", "cat_telecom", "cat_health")
        val mandatory = curTxs.filter {
            it.type == TransactionType.EXPENSE && it.categoryId in mandatoryCats
        }.sumOf { abs(it.amountKopecks) }

        val last3Income = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            getTxSync(f, t).filter { it.type == TransactionType.INCOME }.sumOf { it.amountKopecks }
        }
        val avg3Expense = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            getTxSync(f, t).filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }
        }.average().toLong()

        return ScoreInput(
            monthlyIncome     = curIncome,
            monthlyExpense    = curExpense,
            mandatoryExpense  = mandatory,
            avgMonthlyExpense = avg3Expense,
            totalBalance      = 0L,
            last3MonthsIncome = last3Income,
        )
    }

    private suspend fun buildInsightData(): InsightData {
        val month = YearMonth.now()
        val (fromCur, toCur)   = monthBounds(month)
        val (fromPrev, toPrev) = monthBounds(month.minusMonths(1))

        val curTxs  = getTxSync(fromCur, toCur)
        val prevTxs = getTxSync(fromPrev, toPrev)

        val curExpense  = curTxs.filter  { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }
        val curIncome   = curTxs.filter  { it.type == TransactionType.INCOME  }.sumOf { it.amountKopecks }
        val prevExpense = prevTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }

        val curCatMap  = expenseByCat(curTxs)
        val prevCatMap = expenseByCat(prevTxs)
        val catNames   = categoryDao.getAll().associate { it.id to it.name }

        val topDelta = curCatMap.mapNotNull { (catId, cur) ->
            val prev = prevCatMap[catId] ?: 0L
            if (prev > 0) { val d = (cur - prev).toDouble() / prev; Triple(catId, catNames[catId] ?: "Другое", d) }
            else null
        }.maxByOrNull { it.third }?.let { (_, name, d) -> name to d }

        val avg3Expense = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            getTxSync(f, t).filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }
        }.average().toLong()

        return InsightData(
            currentExpense    = curExpense,
            currentIncome     = curIncome,
            lastMonthExpense  = prevExpense,
            avgMonthlyExpense = avg3Expense,
            totalBalance      = 0L,
            topCategoryDelta  = topDelta,
            budgetAlerts      = emptyList(),
        )
    }

    private suspend fun getTxSync(from: Long, to: Long): List<TransactionEntity> =
        txDao.observeByPeriod(from, to).first()
}
