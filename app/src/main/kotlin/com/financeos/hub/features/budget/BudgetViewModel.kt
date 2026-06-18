package com.financeos.hub.features.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.BudgetRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
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
    val envelopes: List<BudgetEnvelope> = emptyList(),
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepo        : BudgetRepository,
    private val txRepo            : TransactionRepository,
    private val categoryRepo      : CategoryRepository,
    private val notificationHelper: NotificationHelper,
    private val userPreferences   : UserPreferences,
) : ViewModel() {

    private val zone  = ZoneId.systemDefault()
    private val month = YearMonth.now()
    private val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    // Track which budgets already had an alert sent this session to avoid spam
    private val alertedBudgets = mutableSetOf<String>()

    val state = combine(
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
        BudgetState(envelopes = envelopes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetState())

    private fun checkAndFireAlerts(envelopes: List<BudgetEnvelope>) {
        viewModelScope.launch {
            val notificationsOn = userPreferences.notificationsEnabled.first()
            if (!notificationsOn) return@launch

            val threshold = userPreferences.budgetAlertThreshold.first()

            envelopes.forEach { envelope ->
                if (envelope.spentPercent >= threshold && envelope.budgetId !in alertedBudgets) {
                    alertedBudgets += envelope.budgetId
                    notificationHelper.sendBudgetAlert(
                        categoryName = envelope.categoryName,
                        spentPercent = envelope.spentPercent,
                    )
                }
            }
        }
    }
}
