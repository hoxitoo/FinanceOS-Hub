package com.financeos.hub.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.AnalyticsEngine
import com.financeos.hub.core.analytics.Insight
import com.financeos.hub.core.analytics.ScoreCalculator
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val transactions     : List<TransactionEntity>         = emptyList(),
    val categoryExpenses : Map<String, Long>               = emptyMap(),
    val categoryNames    : Map<String, String>             = emptyMap(),
    val dailyExpenses    : List<Pair<Long, Long>>          = emptyList(),
    val insights         : List<Insight>                   = emptyList(),
    val score            : ScoreCalculator.ScoreBreakdown? = null,
    val sparkline        : List<Float>                     = emptyList(),
    val forecastKopecks  : Long                            = 0L,
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

    private val _score     = MutableStateFlow<ScoreCalculator.ScoreBreakdown?>(null)
    private val _insights  = MutableStateFlow<List<Insight>>(emptyList())
    private val _sparkline = MutableStateFlow<List<Float>>(emptyList())
    private val _forecast  = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            _score.value     = analyticsEngine.computeScore()
            _insights.value  = analyticsEngine.generateInsights()
            _sparkline.value = analyticsEngine.sparkline30Days()
            _forecast.value  = analyticsEngine.forecastMonthEnd()
        }
    }

    val state = combine(
        txRepo.observeByPeriod(from, to),
        categoryRepo.observeAll(),
        _score,
        _insights,
        _sparkline,
        _forecast,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val txList     = args[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = args[1] as List<com.financeos.hub.core.database.entities.CategoryEntity>
        val score      = args[2] as ScoreCalculator.ScoreBreakdown?
        @Suppress("UNCHECKED_CAST")
        val insights   = args[3] as List<Insight>
        @Suppress("UNCHECKED_CAST")
        val sparkline  = args[4] as List<Float>
        val forecast   = args[5] as Long

        val catMap = categories.associate { it.id to it.name }

        val catExpenses = txList
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId ?: "cat_other" }
            .mapValues { (_, list) -> list.sumOf { Math.abs(it.amountKopecks) } }

        val daily = txList
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx ->
                Instant.ofEpochMilli(tx.timestamp)
                    .atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .map { (day, list) -> Pair(day, list.sumOf { Math.abs(it.amountKopecks) }) }
            .sortedBy { it.first }

        AnalyticsState(
            transactions    = txList,
            categoryExpenses= catExpenses,
            categoryNames   = catMap,
            dailyExpenses   = daily,
            insights        = insights,
            score           = score,
            sparkline       = sparkline,
            forecastKopecks = forecast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
