package com.financeos.hub.core.analytics

import javax.inject.Inject
import javax.inject.Singleton

data class Insight(
    val id       : String,
    val text     : String,
    val severity : InsightSeverity,
)

enum class InsightSeverity { INFO, WARNING, CRITICAL }

@Singleton
class InsightGenerator @Inject constructor() {

    fun generate(data: InsightData): List<Insight> {
        val insights = mutableListOf<Insight>()

        // 1. Overspending vs last month
        if (data.currentExpense > 0 && data.lastMonthExpense > 0) {
            val delta = (data.currentExpense - data.lastMonthExpense).toDouble() / data.lastMonthExpense
            when {
                delta >= 0.30 -> insights += Insight(
                    "overspend_critical",
                    "Расходы выросли на ${(delta * 100).toInt()}% по сравнению с прошлым месяцем",
                    InsightSeverity.CRITICAL,
                )
                delta >= 0.15 -> insights += Insight(
                    "overspend_warn",
                    "Расходы выросли на ${(delta * 100).toInt()}% — следите за бюджетом",
                    InsightSeverity.WARNING,
                )
                delta <= -0.10 -> insights += Insight(
                    "underspend_good",
                    "Расходы снизились на ${(-delta * 100).toInt()}% — отличный результат",
                    InsightSeverity.INFO,
                )
            }
        }

        // 2. Top category spike
        data.topCategoryDelta?.let { (name, delta) ->
            if (delta >= 0.40) {
                insights += Insight(
                    "cat_spike",
                    "«$name» выросло на ${(delta * 100).toInt()}% — самая быстрорастущая категория",
                    InsightSeverity.WARNING,
                )
            }
        }

        // 3. Negative savings rate
        if (data.currentIncome > 0 && data.currentExpense > data.currentIncome) {
            insights += Insight(
                "negative_savings",
                "Расходы превышают доходы — вы тратите больше, чем зарабатываете",
                InsightSeverity.CRITICAL,
            )
        }

        // 4. Savings milestone
        if (data.currentIncome > 0) {
            val rate = (data.currentIncome - data.currentExpense).toDouble() / data.currentIncome
            when {
                rate >= 0.30 -> insights += Insight(
                    "savings_high",
                    "Норма сбережений ${(rate * 100).toInt()}% — вы на правильном пути",
                    InsightSeverity.INFO,
                )
                rate in 0.10..0.20 -> insights += Insight(
                    "savings_low",
                    "Норма сбережений ${(rate * 100).toInt()}% — попробуйте довести до 20%",
                    InsightSeverity.WARNING,
                )
            }
        }

        // 5. Budget almost exhausted (> 80%)
        data.budgetAlerts.forEach { (name, pct) ->
            if (pct >= 0.9f) {
                insights += Insight(
                    "budget_critical_$name",
                    "Бюджет «$name» исчерпан на ${(pct * 100).toInt()}%",
                    InsightSeverity.CRITICAL,
                )
            } else if (pct >= 0.8f) {
                insights += Insight(
                    "budget_warn_$name",
                    "Бюджет «$name» на ${(pct * 100).toInt()}% — осталось немного",
                    InsightSeverity.WARNING,
                )
            }
        }

        // 6. Low emergency cushion
        if (data.avgMonthlyExpense > 0) {
            val months = data.totalBalance.toDouble() / data.avgMonthlyExpense
            if (months < 1.0) {
                insights += Insight(
                    "cushion_critical",
                    "Финансовая подушка меньше одного месяца расходов",
                    InsightSeverity.CRITICAL,
                )
            } else if (months < 3.0) {
                insights += Insight(
                    "cushion_warn",
                    "Финансовая подушка ${String.format("%.1f", months)} мес. — рекомендуется 3+",
                    InsightSeverity.WARNING,
                )
            }
        }

        // Sort: CRITICAL first, then WARNING, then INFO
        return insights.sortedBy { it.severity.ordinal }.reversed()
    }
}

data class InsightData(
    val currentExpense    : Long,
    val currentIncome     : Long,
    val lastMonthExpense  : Long,
    val avgMonthlyExpense : Long,
    val totalBalance      : Long,
    // categoryName → growth delta (0.4 = +40%)
    val topCategoryDelta  : Pair<String, Double>?,
    // categoryName → spent/limit ratio
    val budgetAlerts      : List<Pair<String, Float>>,
)
