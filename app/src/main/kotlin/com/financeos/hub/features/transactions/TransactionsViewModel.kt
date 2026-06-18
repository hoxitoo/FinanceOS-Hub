package com.financeos.hub.features.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class TxFilter(val label: String) {
    ALL("Все"),
    EXPENSE("Расходы"),
    INCOME("Доходы"),
}

data class TransactionsState(
    val grouped      : Map<Long, List<TransactionEntity>> = emptyMap(),
    val activeFilter : TxFilter                           = TxFilter.ALL,
    private val categories: Map<String, String>           = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categories[it] } ?: "Другое"
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    txRepo       : TransactionRepository,
    categoryRepo : CategoryRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TxFilter.ALL)

    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
        _filter,
    ) { txList, categories, filter ->
        val catMap   = categories.associate { it.id to it.name }
        val filtered = when (filter) {
            TxFilter.ALL     -> txList
            TxFilter.EXPENSE -> txList.filter { it.type == TransactionType.EXPENSE }
            TxFilter.INCOME  -> txList.filter { it.type == TransactionType.INCOME }
        }
        val grouped = filtered.groupBy { tx ->
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        TransactionsState(grouped = grouped, activeFilter = filter, categories = catMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun setFilter(filter: TxFilter) { _filter.value = filter }
}
