package com.financeos.hub.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardState(
    val netWorthKopecks     : Long                    = 0L,
    val incomeKopecks       : Long                    = 0L,
    val expenseKopecks      : Long                    = 0L,
    val accounts            : List<AccountEntity>     = emptyList(),
    val recentTransactions  : List<TransactionEntity> = emptyList(),
    private val categories  : Map<String, String>     = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categories[it] } ?: "Другое"
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    txRepo       : TransactionRepository,
    accountRepo  : AccountRepository,
    categoryRepo : CategoryRepository,
) : ViewModel() {

    val state = combine(
        txRepo.observeCurrentMonth(),
        accountRepo.observeAll(),
        categoryRepo.observeAll(),
    ) { txList, accounts, categories ->
        val catMap   = categories.associate { it.id to it.name }
        val income   = txList.filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountKopecks }
        val expense  = txList.filter { it.type == TransactionType.EXPENSE }
            .sumOf { kotlin.math.abs(it.amountKopecks) }
        val netWorth = accounts.sumOf { it.balanceKopecks }

        DashboardState(
            netWorthKopecks    = netWorth,
            incomeKopecks      = income,
            expenseKopecks     = expense,
            accounts           = accounts,
            recentTransactions = txList.take(5),
            categories         = catMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}
