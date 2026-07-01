package com.financeos.hub.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.AnalyticsEngine
import com.financeos.hub.core.analytics.CategoryAnomaly
import com.financeos.hub.core.analytics.FatigueCurve
import com.financeos.hub.core.analytics.FixedVariableResult
import com.financeos.hub.core.analytics.HeatmapData
import com.financeos.hub.core.analytics.Insight
import com.financeos.hub.core.analytics.ImpulseStats
import com.financeos.hub.core.analytics.NarrativeInsight
import com.financeos.hub.core.analytics.ScoreCalculator
import com.financeos.hub.core.analytics.WaterfallBar
import com.financeos.hub.core.ml.BehavioralCluster
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Period for the cumulative category/daily statistics (does NOT affect the health score or the
 *  behavioural trends, which keep their own monthly/rolling windows). */
enum class AnalyticsPeriod(val label: String) {
    MONTH("Месяц"), HALF_YEAR("6 мес"), YEAR("Год"), ALL("Всё время")
}

data class AnalyticsState(
    val selectedPeriod   : AnalyticsPeriod                  = AnalyticsPeriod.MONTH,
    // Base data
    val transactions     : List<TransactionEntity>         = emptyList(),
    val categoryExpenses : Map<String, Long>               = emptyMap(),
    val categoryNames    : Map<String, String>             = emptyMap(),
    val dailyExpenses    : List<Pair<Long, Long>>          = emptyList(),
    // Score & insights
    val score            : ScoreCalculator.ScoreBreakdown? = null,
    val insights         : List<Insight>                   = emptyList(),
    val sparkline        : List<Float>                     = emptyList(),
    val forecastKopecks  : Long                            = 0L,
    // Behavioral
    val heatmap          : HeatmapData?                    = null,
    val fatigueCurve     : FatigueCurve?                   = null,
    val impulseStats     : ImpulseStats?                   = null,
    val categoryAnomalies: List<CategoryAnomaly>           = emptyList(),
    val waterfallBars    : List<WaterfallBar>              = emptyList(),
    val narratives       : List<NarrativeInsight>          = emptyList(),
    val fixedVariable    : FixedVariableResult?            = null,
    val userArchetype    : BehavioralCluster.ClusterResult? = null,
    val isLoading        : Boolean                         = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val txRepo         : TransactionRepository,
    private val categoryRepo   : CategoryRepository,
    private val analyticsEngine: AnalyticsEngine,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _period = kotlinx.coroutines.flow.MutableStateFlow(AnalyticsPeriod.MONTH)
    fun setPeriod(period: AnalyticsPeriod) { _period.value = period }

    // Window for the selected period, recomputed on each emission (so crossing a month boundary
    // never keeps showing a stale window). MONTH = current calendar month; the longer periods end
    // at the current month's end and reach back the corresponding number of whole months.
    private fun periodWindow(period: AnalyticsPeriod): Pair<Long, Long> {
        val month = YearMonth.now()
        val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val from  = when (period) {
            AnalyticsPeriod.MONTH     -> month.atDay(1)
            AnalyticsPeriod.HALF_YEAR -> month.minusMonths(5).atDay(1)
            AnalyticsPeriod.YEAR      -> month.minusMonths(11).atDay(1)
            AnalyticsPeriod.ALL       -> null
        }?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        return from to to
    }

    /**
     * The analytics screen recomputes whenever the underlying transactions or categories
     * change (mapLatest cancels any in-flight computation, so we never show stale numbers).
     * Every engine call is individually guarded with runCatching so that a failure in one
     * computation leaves the others intact instead of cancelling the whole pipeline.
     */
    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
        _period,
    ) { txList, categories, period -> Triple(txList, categories, period) }
        .mapLatest { (allTx, categories, period) ->
            val (from, to) = periodWindow(period)
            val monthTx = allTx.filter { it.timestamp in from..to }
            val catMap  = categories.associate { it.id to it.name }

            val catExpenses = monthTx
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId ?: "cat_other" }
                .mapValues { (_, list) -> list.sumOf { kotlin.math.abs(it.amountKopecks) } }

            val daily = monthTx
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { tx ->
                    Instant.ofEpochMilli(tx.timestamp)
                        .atZone(zone).toLocalDate()
                        .atStartOfDay(zone).toInstant().toEpochMilli()
                }
                .map { (day, list) -> day to list.sumOf { kotlin.math.abs(it.amountKopecks) } }
                .sortedBy { it.first }

            coroutineScope {
                fun <T> safeAsync(block: suspend () -> T?) = async {
                    runCatching { block() }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                }
                val scoreD     = safeAsync { analyticsEngine.computeScore() }
                val insightsD  = safeAsync { analyticsEngine.generateInsights() ?: emptyList() }
                val sparklineD = safeAsync { analyticsEngine.sparkline30Days() ?: emptyList() }
                val forecastD  = safeAsync { analyticsEngine.forecastMonthEnd() ?: 0L }
                val heatmapD   = safeAsync { analyticsEngine.computeHeatmap() }
                val fatigueD   = safeAsync { analyticsEngine.computeFatigueCurve() }
                val impulseD   = safeAsync { analyticsEngine.computeImpulseStats() }
                val anomaliesD = safeAsync { analyticsEngine.detectCategoryAnomalies() ?: emptyList() }
                val waterfallD = safeAsync { analyticsEngine.computeWaterfallBars() ?: emptyList() }
                val narrativesD= safeAsync { analyticsEngine.generateNarratives() ?: emptyList() }
                val fixedVarD  = safeAsync { analyticsEngine.classifyFixedVariable() }
                val archetypeD = safeAsync { analyticsEngine.classifyBehavior() }

                AnalyticsState(
                    selectedPeriod    = period,
                    transactions      = monthTx,
                    categoryExpenses  = catExpenses,
                    categoryNames     = catMap,
                    dailyExpenses     = daily,
                    score             = scoreD.await(),
                    insights          = insightsD.await()    ?: emptyList(),
                    sparkline         = sparklineD.await()   ?: emptyList(),
                    forecastKopecks   = forecastD.await()    ?: 0L,
                    heatmap           = heatmapD.await(),
                    fatigueCurve      = fatigueD.await(),
                    impulseStats      = impulseD.await(),
                    categoryAnomalies = anomaliesD.await()   ?: emptyList(),
                    waterfallBars     = waterfallD.await()   ?: emptyList(),
                    narratives        = narrativesD.await()  ?: emptyList(),
                    fixedVariable     = fixedVarD.await(),
                    userArchetype     = archetypeD.await(),
                    isLoading         = false,
                )
            }
        }
        .catch { emit(AnalyticsState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
