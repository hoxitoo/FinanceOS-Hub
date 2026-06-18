package com.financeos.hub.features.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.data.repositories.BudgetRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class BudgetEnvelope(
    val budgetId      : String,
    val categoryName  : String,
    val limitKopecks  : Long,
    val spentKopecks  : Long,
)

data class BudgetState(
    val envelopes: List<BudgetEnvelope> = emptyList(),
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    budgetRepo   : BudgetRepository,
    txRepo       : TransactionRepository,
    categoryRepo : CategoryRepository,
) : ViewModel() {

    private val zone  = ZoneId.systemDefault()
    private val month = YearMonth.now()
    private val from  = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val to    = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    val state = combine(
        budgetRepo.observeAll(),
        txRepo.observeExpensesByCategory(from, to),
        categoryRepo.observeAll(),
    ) { budgets, catExpenses, categories ->
        val catMap     = categories.associate { it.id to it.name }
        val spentMap   = catExpenses.associate { it.category_id.orEmpty() to it.total }

        val envelopes = budgets.map { budget ->
            BudgetEnvelope(
                budgetId     = budget.id,
                categoryName = catMap[budget.categoryId] ?: "Другое",
                limitKopecks = budget.limitKopecks,
                spentKopecks = spentMap[budget.categoryId] ?: 0L,
            )
        }
        BudgetState(envelopes = envelopes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetState())
}
