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
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class AnalyticsState(
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
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val txRepo         : TransactionRepository,
    private val categoryRepo   : CategoryRepository,
    private val analyticsEngine: AnalyticsEngine,
) : ViewModel() {

    private val zone  = ZoneId.systemDefault()
    private val month = YearMonth.now()
    private val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    private val _score       = MutableStateFlow<ScoreCalculator.ScoreBreakdown?>(null)
    private val _insights    = MutableStateFlow<List<Insight>>(emptyList())
    private val _sparkline   = MutableStateFlow<List<Float>>(emptyList())
    private val _forecast    = MutableStateFlow(0L)
    private val _heatmap     = MutableStateFlow<HeatmapData?>(null)
    private val _fatigue     = MutableStateFlow<FatigueCurve?>(null)
    private val _impulse     = MutableStateFlow<ImpulseStats?>(null)
    private val _anomalies   = MutableStateFlow<List<CategoryAnomaly>>(emptyList())
    private val _waterfall   = MutableStateFlow<List<WaterfallBar>>(emptyList())
    private val _narratives  = MutableStateFlow<List<NarrativeInsight>>(emptyList())
    private val _fixedVar    = MutableStateFlow<FixedVariableResult?>(null)

    init {
        viewModelScope.launch {
            // Launch all analytics computations concurrently
            val scoreD      = async { analyticsEngine.computeScore() }
            val insightsD   = async { analyticsEngine.generateInsights() }
            val sparklineD  = async { analyticsEngine.sparkline30Days() }
            val forecastD   = async { analyticsEngine.forecastMonthEnd() }
            val heatmapD    = async { analyticsEngine.computeHeatmap() }
            val fatigueD    = async { analyticsEngine.computeFatigueCurve() }
            val impulseD    = async { analyticsEngine.computeImpulseStats() }
            val anomaliesD  = async { analyticsEngine.detectCategoryAnomalies() }
            val waterfallD  = async { analyticsEngine.computeWaterfallBars() }
            val narrativesD = async { analyticsEngine.generateNarratives() }
            val fixedVarD   = async { analyticsEngine.classifyFixedVariable() }

            _score.value      = scoreD.await()
            _insights.value   = insightsD.await()
            _sparkline.value  = sparklineD.await()
            _forecast.value   = forecastD.await()
            _heatmap.value    = heatmapD.await()
            _fatigue.value    = fatigueD.await()
            _impulse.value    = impulseD.await()
            _anomalies.value  = anomaliesD.await()
            _waterfall.value  = waterfallD.await()
            _narratives.value = narrativesD.await()
            _fixedVar.value   = fixedVarD.await()
        }
    }

    val state = combine(
        txRepo.observeByPeriod(from, to),
        categoryRepo.observeAll(),
        combine(_score, _insights, _sparkline, _forecast) { a, b, c, d ->
            listOf(a, b, c, d)
        },
        combine(_heatmap, _fatigue, _impulse, _anomalies) { a, b, c, d ->
            listOf(a, b, c, d)
        },
        combine(_waterfall, _narratives, _fixedVar) { a, b, c -> listOf(a, b, c) },
    ) { txList, categories, scores, behavioral, extras ->
        val catMap = categories.associate { it.id to it.name }

        @Suppress("UNCHECKED_CAST")
        val catExpenses = txList
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId ?: "cat_other" }
            .mapValues { (_, list) -> list.sumOf { kotlin.math.abs(it.amountKopecks) } }

        val daily = txList
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx ->
                Instant.ofEpochMilli(tx.timestamp)
                    .atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .map { (day, list) -> Pair(day, list.sumOf { kotlin.math.abs(it.amountKopecks) }) }
            .sortedBy { it.first }

        AnalyticsState(
            transactions      = txList,
            categoryExpenses  = catExpenses,
            categoryNames     = catMap,
            dailyExpenses     = daily,
            score             = scores[0] as ScoreCalculator.ScoreBreakdown?,
            insights          = (scores[1] as List<*>).filterIsInstance<Insight>(),
            sparkline         = (scores[2] as List<*>).filterIsInstance<Float>(),
            forecastKopecks   = scores[3] as Long,
            heatmap           = behavioral[0] as HeatmapData?,
            fatigueCurve      = behavioral[1] as FatigueCurve?,
            impulseStats      = behavioral[2] as ImpulseStats?,
            categoryAnomalies = (behavioral[3] as List<*>).filterIsInstance<CategoryAnomaly>(),
            waterfallBars     = (extras[0] as List<*>).filterIsInstance<WaterfallBar>(),
            narratives        = (extras[1] as List<*>).filterIsInstance<NarrativeInsight>(),
            fixedVariable     = extras[2] as FixedVariableResult?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
