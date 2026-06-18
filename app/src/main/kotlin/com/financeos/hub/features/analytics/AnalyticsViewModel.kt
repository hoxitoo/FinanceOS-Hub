package com.financeos.hub.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class AnalyticsState(
    val transactions      : List<TransactionEntity>         = emptyList(),
    val categoryExpenses  : Map<String, Long>               = emptyMap(),  // categoryId → kopecks
    val categoryNames     : Map<String, String>             = emptyMap(),
    val dailyExpenses     : List<Pair<Long, Long>>          = emptyList(), // epochDay → kopecks
    val insights          : List<String>                    = emptyList(),
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    txRepo       : TransactionRepository,
    categoryRepo : CategoryRepository,
) : ViewModel() {

    private val zone  = ZoneId.systemDefault()
    private val month = YearMonth.now()
    private val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    val state = combine(
        txRepo.observeByPeriod(from, to),
        categoryRepo.observeAll(),
    ) { txList, categories ->
        val catMap = categories.associate { it.id to it.name }

        val catExpenses = txList
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId ?: "cat_other" }
            .mapValues { (_, list) -> list.sumOf { Math.abs(it.amountKopecks) } }

        // daily expenses for trend chart: (epochMillis of day start → kopecks)
        val daily = txList
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx ->
                java.time.Instant.ofEpochMilli(tx.timestamp)
                    .atZone(zone).toLocalDate()
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .map { (day, list) -> Pair(day, list.sumOf { Math.abs(it.amountKopecks) }) }
            .sortedBy { it.first }

        val totalExpense = txList.filter { it.type == TransactionType.EXPENSE }
            .sumOf { Math.abs(it.amountKopecks) }
        val totalIncome  = txList.filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountKopecks }

        val insights = mutableListOf<String>()
        if (totalExpense > totalIncome && totalIncome > 0) {
            insights += "Расходы превысили доходы на ${totalExpense - totalIncome} коп."
        }
        catExpenses.maxByOrNull { it.value }?.let { (catId, amt) ->
            insights += "Больше всего потрачено на «${catMap[catId] ?: "Другое"}»"
        }

        AnalyticsState(
            transactions     = txList,
            categoryExpenses = catExpenses,
            categoryNames    = catMap,
            dailyExpenses    = daily,
            insights         = insights,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
