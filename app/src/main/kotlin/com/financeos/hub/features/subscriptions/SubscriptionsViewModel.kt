package com.financeos.hub.features.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.BehavioralAnalyzer
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
import kotlin.math.abs

data class SubscriptionInfo(
    val categoryId        : String,
    val categoryName      : String,
    val avgMonthlyKopecks : Long,
    val lastPaymentAt     : Long,
    val monthsPresent     : Int,
    val isMissedThisMonth : Boolean,
)

data class SubscriptionsState(
    val active              : List<SubscriptionInfo> = emptyList(),
    val missed              : List<SubscriptionInfo> = emptyList(),
    val totalMonthlyKopecks : Long                   = 0L,
    val isLoading           : Boolean                = true,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val txRepo      : TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val analyzer    : BehavioralAnalyzer,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
    ) { txList, categories ->
        val catMap   = categories.associate { it.id to it.name }
        val now      = YearMonth.now()

        // Build monthly expense maps for last 6 months (index 0 = current month)
        val monthMaps = (0..5).map { offset ->
            val ym   = now.minusMonths(offset.toLong())
            val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to   = ym.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
            txList
                .filter { it.type == TransactionType.EXPENSE && it.timestamp in from..to }
                .groupBy { it.categoryId ?: "cat_other" }
                .mapValues { (_, list) -> list.sumOf { abs(it.amountKopecks) } }
        }

        val currentMonth = monthMaps.first()
        val history      = monthMaps.drop(1)

        // Presence count per category across all 6 months
        val presenceCounts = monthMaps
            .flatMap { it.keys }
            .groupingBy { it }
            .eachCount()

        // Subscription = present in at least 3 of 6 months
        val subscriptionCatIds = presenceCounts.filter { (_, count) -> count >= 3 }.keys

        // Categories missing in current month but present in 3 of last 5 months
        val missedIds = analyzer.detectSubscriptionGaps(currentMonth, history).toSet()

        val allSubscriptions = subscriptionCatIds.mapNotNull { catId ->
            val monthlyAmounts = monthMaps.mapNotNull { it[catId] }
            if (monthlyAmounts.isEmpty()) return@mapNotNull null
            val avgMonthly = monthlyAmounts.sum() / monthlyAmounts.size
            val lastPayment = txList
                .filter { it.categoryId == catId && it.type == TransactionType.EXPENSE }
                .maxOfOrNull { it.timestamp } ?: 0L
            SubscriptionInfo(
                categoryId        = catId,
                categoryName      = catMap[catId] ?: "Другое",
                avgMonthlyKopecks = avgMonthly,
                lastPaymentAt     = lastPayment,
                monthsPresent     = presenceCounts[catId] ?: 0,
                isMissedThisMonth = catId in missedIds,
            )
        }.sortedByDescending { it.avgMonthlyKopecks }

        val missed  = allSubscriptions.filter { it.isMissedThisMonth }
        val active  = allSubscriptions.filter { !it.isMissedThisMonth }
        val total   = active.sumOf { it.avgMonthlyKopecks }

        SubscriptionsState(
            active              = active,
            missed              = missed,
            totalMonthlyKopecks = total,
            isLoading           = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsState())
}
