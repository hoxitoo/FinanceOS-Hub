package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Financial health score 0–100.
 *
 * Formula (max 100):
 *   savings  30 — savings rate vs income (target ≥ 20%)
 *   stability 20 — income stability: months with income / last 3 months
 *   mandatory 25 — mandatory expenses ratio (housing + telecom + health ≤ 50% of expenses)
 *   cushion   25 — emergency fund: balance / avg monthly expense (target ≥ 3 months)
 */
@Singleton
class ScoreCalculator @Inject constructor() {

    data class ScoreBreakdown(
        val total    : Int,   // 0–100
        val savings  : Int,   // 0–30
        val stability: Int,   // 0–20
        val mandatory: Int,   // 0–25
        val cushion  : Int,   // 0–25
    )

    fun calculate(input: ScoreInput): ScoreBreakdown {
        val savings   = calcSavings(input)
        val stability = calcStability(input)
        val mandatory = calcMandatory(input)
        val cushion   = calcCushion(input)
        return ScoreBreakdown(
            total     = savings + stability + mandatory + cushion,
            savings   = savings,
            stability = stability,
            mandatory = mandatory,
            cushion   = cushion,
        )
    }

    // 30 pts: savingsRate = (income - expenses) / income; full score at ≥ 20%
    private fun calcSavings(input: ScoreInput): Int {
        if (input.monthlyIncome <= 0) return 0
        val rate = (input.monthlyIncome - input.monthlyExpense).toDouble() / input.monthlyIncome
        return min(30, (rate / 0.20 * 30).roundToInt()).coerceAtLeast(0)
    }

    // 20 pts: ratio of months with any income in the last 3 months
    private fun calcStability(input: ScoreInput): Int {
        if (input.last3MonthsIncome.isEmpty()) return 0
        val monthsWithIncome = input.last3MonthsIncome.count { it > 0 }
        return (monthsWithIncome.toFloat() / 3 * 20).roundToInt()
    }

    // 25 pts: mandatory ratio ≤ 50% of total expense = full score; ≥ 90% = 0
    private fun calcMandatory(input: ScoreInput): Int {
        if (input.monthlyExpense <= 0) return 25
        val ratio = input.mandatoryExpense.toDouble() / input.monthlyExpense
        return when {
            ratio <= 0.50 -> 25
            ratio >= 0.90 -> 0
            else          -> ((0.90 - ratio) / 0.40 * 25).roundToInt()
        }
    }

    // 25 pts: balance / avgMonthlyExpense; full score at ≥ 3 months cushion
    private fun calcCushion(input: ScoreInput): Int {
        if (input.avgMonthlyExpense <= 0) return 0
        val months = input.totalBalance.toDouble() / input.avgMonthlyExpense
        return min(25, (months / 3.0 * 25).roundToInt()).coerceAtLeast(0)
    }
}

data class ScoreInput(
    val monthlyIncome    : Long,
    val monthlyExpense   : Long,
    val mandatoryExpense : Long,              // housing + telecom + health this month
    val avgMonthlyExpense: Long,              // rolling 3-month average
    val totalBalance     : Long,             // sum of all accounts
    val last3MonthsIncome: List<Long>,       // income per month, newest first
)
