package com.financeos.hub.features.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.analytics.SubscriptionDetector
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import com.financeos.hub.data.preferences.UserPreferences
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

/**
 * Итог за месяц в ОДНОЙ валюте. Складывать доллары с рублями нельзя, поэтому итог не один.
 */
data class MonthlyTotal(val currency: String, val kopecks: Long)

data class SubscriptionsState(
    val active    : List<SubscriptionDetector.Subscription> = emptyList(),
    val missed    : List<SubscriptionDetector.Subscription> = emptyList(),
    /** По одному итогу на валюту, крупнейший первым. */
    val totals    : List<MonthlyTotal> = emptyList(),
    /** Скрыто кнопкой «Не подписка» (#56). */
    val dismissedCount: Int = 0,
    val isLoading : Boolean = true,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
    private val prefs : UserPreferences,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val state = combine(txRepo.observeAll(), prefs.dismissedSubscriptions) { txList, dismissed ->
        val now  = System.currentTimeMillis()
        // Больше двух лет. Ритм подтверждается ТРЕМЯ списаниями, поэтому годовой подписке нужно
        // окно в два с лишним года — на 400 днях она была недостижима в принципе, и ветка Yearly
        // существовала бы только в тестах.
        val from = now - WINDOW_DAYS * 24L * 60 * 60 * 1000

        val charges = txList
            // Только расходы (observeAll уже отсеивает удалённые): перевод между своими счетами и
            // приход зарплаты подпиской не бывают.
            .filter { it.type == TransactionType.EXPENSE && it.timestamp >= from }
            .map {
                SubscriptionDetector.Charge(
                    timestamp     = it.timestamp,
                    amountKopecks = abs(it.amountKopecks),
                    currency      = it.currency,
                    merchant      = it.merchant,
                    description   = it.description,
                    categoryId    = it.categoryId,
                )
            }

        // «Не подписка» (#56) — тот же список, что и в календаре: скрытое там не всплывает здесь.
        val all    = SubscriptionDetector.detect(charges, now).filter { it.key !in dismissed }
        val missed = all.filter { it.isMissed }
        val active = all.filter { !it.isMissed }

        SubscriptionsState(
            active    = active,
            missed    = missed,
            // Пропущенные в итог не идут: если списание перестало приходить, деньги за него
            // больше не уходят, и обещать обратное — врать в большую сторону.
            totals    = active
                .groupBy { it.currency }
                .map { (currency, subs) -> MonthlyTotal(currency, subs.sumOf { it.monthlyKopecks }) }
                .sortedByDescending { it.kopecks },
            dismissedCount = dismissed.size,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsState())

    fun dismiss(key: String) = viewModelScope.launch { prefs.dismissSubscription(key) }

    fun restoreDismissed() = viewModelScope.launch { prefs.restoreDismissedSubscriptions() }

    private companion object {
        const val WINDOW_DAYS = 800L
    }
}
