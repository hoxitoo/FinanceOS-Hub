package com.financeos.hub.core.analytics

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosFormatter
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Generates short personal narrative insights from transaction history.
 * Template-based — no ML needed. Called monthly/weekly by AnalyticsWorker.
 */
@Singleton
class NarrativeEngine @Inject constructor() {

    private val zone = ZoneId.systemDefault()

    fun generate(input: NarrativeInput): List<NarrativeInsight> {
        val results = mutableListOf<NarrativeInsight>()

        // 1. Most expensive single day ever
        mostExpensiveDay(input.allTransactions)?.let { (ts, kopecks) ->
            results += NarrativeInsight(
                id   = "most_expensive_day",
                text = "Твой самый дорогой день — ${FosFormatter.dayLabel(ts)}, ${FosFormatter.compact(kopecks)}",
                icon = "📅",
            )
        }

        // 2. Average daily food spend
        input.categoryAveragePerDay["cat_food"]
            ?.let { avg ->
                if (avg > 0) results += NarrativeInsight(
                    id   = "daily_food_avg",
                    text = "За последние 3 месяца еда обходится примерно в ${FosFormatter.compact(avg)} в день",
                    icon = "🍽️",
                )
            }

        // 3. Savings rate growth
        input.savingsRateHistory.takeIf { it.size >= 2 }?.let { history ->
            val first  = history.last()   // oldest
            val latest = history.first()  // newest
            val delta  = latest - first
            if (delta >= 0.05) {
                results += NarrativeInsight(
                    id   = "savings_growth",
                    text = "Норма сбережений выросла с ${(first * 100).toInt()}% до ${(latest * 100).toInt()}% за ${history.size} месяцев",
                    icon = "📈",
                )
            } else if (delta <= -0.05) {
                results += NarrativeInsight(
                    id   = "savings_drop",
                    text = "Норма сбережений снизилась с ${(first * 100).toInt()}% до ${(latest * 100).toInt()}% — стоит пересмотреть расходы",
                    icon = "📉",
                )
            }
        }

        // 4. Best month (lowest expenses vs income)
        input.bestMonth?.let { (monthLabel, rate) ->
            results += NarrativeInsight(
                id   = "best_month",
                text = "$monthLabel — лучший месяц: расходы были ниже доходов на ${(rate * 100).toInt()}%",
                icon = "🏆",
            )
        }

        // 5. Payday effect
        input.paydayEffect?.takeIf { it.isSignificant }?.let { effect ->
            results += NarrativeInsight(
                id   = "payday_effect",
                text = "После зарплаты ты тратишь в среднем на ${effect.deltaPercent}% больше в первые 3 дня",
                icon = "💸",
            )
        }

        // 6. Impulse spending share
        input.impulseStats?.let { stats ->
            if (stats.impulseShareOfSpend >= 0.15f) {
                results += NarrativeInsight(
                    id   = "impulse_share",
                    text = "${(stats.impulseShareOfSpend * 100).toInt()}% расходов — импульсивные покупки (ночью, небольшие суммы)",
                    icon = "🌙",
                )
            }
        }

        // 7. Top merchant
        input.topMerchant?.let { (name, kopecks) ->
            results += NarrativeInsight(
                id   = "top_merchant",
                text = "«$name» — чаще всего встречается в расходах, всего ${FosFormatter.compact(kopecks)}",
                icon = "🏪",
            )
        }

        // 8. Weekend vs weekday spending
        input.weekendVsWeekday?.let { ratio ->
            if (ratio >= 1.4f) {
                results += NarrativeInsight(
                    id   = "weekend_spike",
                    text = "В выходные ты тратишь в ${String.format("%.1f", ratio)}× больше, чем в будни",
                    icon = "🎉",
                )
            }
        }

        return results.take(6)  // max 6 narrative cards per period
    }

    private fun mostExpensiveDay(
        transactions: List<TransactionEntity>,
    ): Pair<Long, Long>? {
        return transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx ->
                Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .mapValues { (_, list) -> list.sumOf { abs(it.amountKopecks) } }
            .maxByOrNull { it.value }
            ?.toPair()
    }
}

data class NarrativeInsight(
    val id   : String,
    val text : String,
    val icon : String,
)

data class NarrativeInput(
    val allTransactions      : List<TransactionEntity>,
    val categoryAveragePerDay: Map<String, Long>,       // categoryId → kopecks/day (3-month avg)
    val savingsRateHistory   : List<Float>,             // newest first, e.g. [0.18, 0.12, 0.08]
    val bestMonth            : Pair<String, Float>?,    // label, savingsRate
    val paydayEffect         : PaydayEffect?,
    val impulseStats         : ImpulseStats?,
    val topMerchant          : Pair<String, Long>?,     // merchant name, total kopecks
    val weekendVsWeekday     : Float?,                  // ratio weekend/weekday avg per day
)
