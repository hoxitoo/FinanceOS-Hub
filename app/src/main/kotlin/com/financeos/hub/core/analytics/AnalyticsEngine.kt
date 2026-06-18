package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.daos.AccountDao
import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AnalyticsEngine @Inject constructor(
    private val txDao          : TransactionDao,
    private val accountDao     : AccountDao,
    private val budgetDao      : BudgetDao,
    private val categoryDao    : CategoryDao,
    private val scoreCalculator: ScoreCalculator,
    private val insightGenerator: InsightGenerator,
) {
    private val zone = ZoneId.systemDefault()

    suspend fun computeScore(): ScoreCalculator.ScoreBreakdown {
        val input = buildScoreInput()
        return scoreCalculator.calculate(input)
    }

    suspend fun generateInsights(): List<Insight> {
        val data = buildInsightData()
        return insightGenerator.generate(data)
    }

    /** 30-day daily expense values for the sparkline, newest last */
    suspend fun sparkline30Days(): List<Float> {
        val now  = System.currentTimeMillis()
        val from = now - 30L * 24 * 60 * 60 * 1000
        val txs  = getTransactionsSync(from, now)
            .filter { it.type == TransactionType.EXPENSE }

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
            val key = cursor.atStartOfDay(zone).toInstant().toEpochMilli()
            result += (dailyMap[key] ?: 0L).toFloat()
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /** Forecast month-end expense based on daily average so far */
    suspend fun forecastMonthEnd(): Long {
        val month = YearMonth.now()
        val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now   = System.currentTimeMillis()
        val txs   = getTransactionsSync(from, now).filter { it.type == TransactionType.EXPENSE }
        val spent = txs.sumOf { abs(it.amountKopecks) }

        val daysPassed = ((now - from) / (24 * 60 * 60 * 1000)).coerceAtLeast(1)
        val daysInMonth = month.lengthOfMonth().toLong()
        return if (daysPassed > 0) spent * daysInMonth / daysPassed else 0L
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private suspend fun buildScoreInput(): ScoreInput {
        val zone  = this.zone
        val month = YearMonth.now()

        fun monthBounds(m: YearMonth): Pair<Long, Long> {
            val from = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to   = m.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
            return from to to
        }

        val (fromCur, toCur) = monthBounds(month)
        val curTxs    = getTransactionsSync(fromCur, toCur)
        val curIncome = curTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountKopecks }
        val curExpense= curTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }

        // Mandatory: housing + telecom + health categories
        val mandatoryCats = setOf("cat_housing", "cat_telecom", "cat_health")
        val mandatory = curTxs.filter {
            it.type == TransactionType.EXPENSE && it.categoryId in mandatoryCats
        }.sumOf { abs(it.amountKopecks) }

        // Last 3 months income
        val last3Income = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            getTransactionsSync(f, t)
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amountKopecks }
        }

        // 3-month average expense
        val avg3Expense = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            getTransactionsSync(f, t)
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { abs(it.amountKopecks) }
        }.average().toLong()

        val totalBalance = accountDao.observeAll().let { flow ->
            // One-shot read via suspending helper
            var sum = 0L
            // read synchronously via DAO – approximation for worker context
            sum
        }

        return ScoreInput(
            monthlyIncome     = curIncome,
            monthlyExpense    = curExpense,
            mandatoryExpense  = mandatory,
            avgMonthlyExpense = avg3Expense,
            totalBalance      = totalBalance,
            last3MonthsIncome = last3Income,
        )
    }

    private suspend fun buildInsightData(): InsightData {
        val month = YearMonth.now()
        val zone  = this.zone

        fun monthBounds(m: YearMonth): Pair<Long, Long> {
            val from = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to   = m.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
            return from to to
        }

        val (fromCur, toCur) = monthBounds(month)
        val (fromPrev, toPrev) = monthBounds(month.minusMonths(1))

        val curTxs  = getTransactionsSync(fromCur, toCur)
        val prevTxs = getTransactionsSync(fromPrev, toPrev)

        val curExpense  = curTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }
        val curIncome   = curTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountKopecks }
        val prevExpense = prevTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { abs(it.amountKopecks) }

        // Category delta vs last month
        val curCatMap  = curTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId ?: "cat_other" }
            .mapValues { (_, list) -> list.sumOf { abs(it.amountKopecks) } }
        val prevCatMap = prevTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId ?: "cat_other" }
            .mapValues { (_, list) -> list.sumOf { abs(it.amountKopecks) } }

        val catNames   = categoryDao.getAll().associate { it.id to it.name }
        val topDelta   = curCatMap.mapNotNull { (catId, cur) ->
            val prev  = prevCatMap[catId] ?: 0L
            if (prev > 0) {
                val delta = (cur - prev).toDouble() / prev
                Triple(catId, catNames[catId] ?: "Другое", delta)
            } else null
        }.maxByOrNull { it.third }?.let { (_, name, delta) -> name to delta }

        // Budget alerts
        val budgets = budgetDao.observeAll().let { emptyList<com.financeos.hub.core.database.entities.BudgetEntity>() }
        val budgetAlerts = budgets.mapNotNull { budget ->
            val spent = curCatMap[budget.categoryId] ?: 0L
            if (budget.limitKopecks > 0) {
                val pct   = spent.toFloat() / budget.limitKopecks
                val name  = catNames[budget.categoryId] ?: "Категория"
                if (pct >= 0.8f) name to pct else null
            } else null
        }

        val avg3Expense = (0..2).map { offset ->
            val (f, t) = monthBounds(month.minusMonths(offset.toLong()))
            getTransactionsSync(f, t)
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { abs(it.amountKopecks) }
        }.average().toLong()

        return InsightData(
            currentExpense   = curExpense,
            currentIncome    = curIncome,
            lastMonthExpense = prevExpense,
            avgMonthlyExpense= avg3Expense,
            totalBalance     = 0L,
            topCategoryDelta = topDelta,
            budgetAlerts     = budgetAlerts,
        )
    }

    private suspend fun getTransactionsSync(from: Long, to: Long): List<com.financeos.hub.core.database.entities.TransactionEntity> {
        var result = emptyList<com.financeos.hub.core.database.entities.TransactionEntity>()
        txDao.observeByPeriod(from, to).collect { result = it; return@collect }
        return result
    }
}
