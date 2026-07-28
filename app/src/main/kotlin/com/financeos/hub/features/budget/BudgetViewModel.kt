package com.financeos.hub.features.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.BudgetEntity
import com.financeos.hub.core.database.entities.BudgetPeriod
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.BudgetRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class BudgetEnvelope(
    val budgetId      : String,
    val categoryName  : String,
    val limitKopecks  : Long,
    val spentKopecks  : Long,
) {
    val spentPercent: Int
        get() = if (limitKopecks > 0) ((spentKopecks * 100) / limitKopecks).toInt() else 0
}

data class BudgetState(
    val envelopes : List<BudgetEnvelope> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepo        : BudgetRepository,
    private val txRepo            : TransactionRepository,
    private val categoryRepo      : CategoryRepository,
    private val notificationHelper: NotificationHelper,
    private val userPreferences   : UserPreferences,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    // In-session guard only (keys are "<budgetId>:<YYYY-MM>"). The AUTHORITATIVE record is
    // persisted in UserPreferences — this ViewModel dies on every navigation away from the
    // Budget screen, so an in-memory set alone re-fired the alert on each visit.
    private val alertedBudgets = mutableSetOf<String>()

    // Emits Unit at startup and once again at each calendar midnight so the query window
    // always covers the current month, even when the app is left open overnight.
    private val _monthTick = MutableStateFlow(Unit)

    init {
        viewModelScope.launch {
            while (true) {
                val now      = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, midnight).toMillis())
                alertedBudgets.clear()   // reset per-session alert tracker for the new month
                _monthTick.value = Unit
            }
        }
    }

    val state = _monthTick.flatMapLatest {
        val month = YearMonth.now()
        val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        combine(
            budgetRepo.observeAll(),
            txRepo.observeExpensesByCategory(from, to),
            categoryRepo.observeAll(),
        ) { budgets, catExpenses, categories ->
            val catMap   = categories.associate { it.id to it.name }
            val spentMap = catExpenses.associate { it.category_id.orEmpty() to it.total }

            val envelopes = budgets.map { budget ->
                BudgetEnvelope(
                    budgetId     = budget.id,
                    categoryName = catMap[budget.categoryId] ?: "Другое",
                    limitKopecks = budget.limitKopecks,
                    spentKopecks = spentMap[budget.categoryId] ?: 0L,
                )
            }

            checkAndFireAlerts(envelopes)
            BudgetState(envelopes = envelopes, categories = categories)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetState())

    fun createBudget(categoryId: String, limitKopecks: Long, period: BudgetPeriod) {
        viewModelScope.launch {
            budgetRepo.upsert(
                BudgetEntity(
                    id           = UUID.randomUUID().toString(),
                    categoryId   = categoryId,
                    limitKopecks = limitKopecks,
                    period       = period,
                )
            )
        }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch { budgetRepo.deactivate(id) }
    }

    /**
     * Fires budget alerts, throttled on THREE levels — opening the Budget screen used to re-fire
     * every over-threshold envelope, because the tracker was in-memory and this ViewModel is
     * recreated on each navigation to the screen (the reported "приходит 10 раз"):
     *  1. once per budget per month (persisted key `<budgetId>:<YYYY-MM>`),
     *  2. at most [MAX_ALERTS_PER_DAY] notifications per calendar day,
     *  3. an in-memory guard so repeated flow emissions in one session don't even read DataStore.
     */
    private fun checkAndFireAlerts(envelopes: List<BudgetEnvelope>) {
        viewModelScope.launch {
            if (!userPreferences.notificationsEnabled.first()) return@launch
            val threshold = userPreferences.budgetAlertThreshold.first()

            val month   = YearMonth.now().toString()
            val overs   = envelopes.filter { it.spentPercent >= threshold }
            // Cheap in-session guard: nothing new crossed the threshold since the last check.
            if (overs.isEmpty() || overs.all { "${it.budgetId}:$month" in alertedBudgets }) return@launch

            val today = LocalDate.now().toEpochDay()
            val saved = userPreferences.budgetAlertState()
            // A new day resets both the counter and the per-budget keys.
            var count = if (saved.epochDay == today) saved.countToday else 0
            val keys  = if (saved.epochDay == today) saved.alertedKeys.toMutableSet()
                        else saved.alertedKeys.filter { it.endsWith(":$month") }.toMutableSet()

            var changed = false
            for (envelope in overs) {
                val key = "${envelope.budgetId}:$month"
                alertedBudgets += key                      // remember in-session either way
                if (key in keys) continue                  // already alerted this budget this month
                if (count >= MAX_ALERTS_PER_DAY) break     // daily cap reached
                notificationHelper.sendBudgetAlert(
                    categoryName = envelope.categoryName,
                    spentPercent = envelope.spentPercent,
                )
                keys += key
                count++
                changed = true
            }
            if (changed) {
                userPreferences.saveBudgetAlertState(
                    UserPreferences.BudgetAlertState(today, count, keys)
                )
            }
        }
    }

    private companion object {
        /** Never show more than this many budget notifications in one calendar day. */
        const val MAX_ALERTS_PER_DAY = 2
    }
}
