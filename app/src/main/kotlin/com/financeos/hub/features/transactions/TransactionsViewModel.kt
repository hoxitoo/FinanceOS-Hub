package com.financeos.hub.features.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
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
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

enum class TxFilter(val label: String) {
    ALL("Все"),
    EXPENSE("Расходы"),
    INCOME("Доходы"),
}

data class TransactionsState(
    val grouped      : Map<Long, List<TransactionEntity>> = emptyMap(),
    val activeFilter : TxFilter                           = TxFilter.ALL,
    val searchQuery  : String                             = "",
    val categories   : List<CategoryEntity>               = emptyList(),
    private val categoryMap: Map<String, String>          = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categoryMap[it] } ?: "Другое"
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val txRepo      : TransactionRepository,
    private val categoryRepo: CategoryRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TxFilter.ALL)
    private val _search = MutableStateFlow("")

    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
        _filter,
        _search,
    ) { txList, categories, filter, query ->
        val catMap   = categories.associate { it.id to it.name }

        val filtered = txList
            .filter { tx ->
                when (filter) {
                    TxFilter.ALL     -> true
                    TxFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                    TxFilter.INCOME  -> tx.type == TransactionType.INCOME
                }
            }
            .filter { tx ->
                if (query.isBlank()) true
                else {
                    val q = query.trim().lowercase()
                    tx.merchant?.lowercase()?.contains(q) == true ||
                    tx.description?.lowercase()?.contains(q) == true ||
                    catMap[tx.categoryId]?.lowercase()?.contains(q) == true
                }
            }

        val grouped = filtered.groupBy { tx ->
            Instant.ofEpochMilli(tx.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        TransactionsState(
            grouped    = grouped,
            activeFilter = filter,
            searchQuery  = query,
            categories   = categories,
            categoryMap  = catMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun setFilter(filter: TxFilter) { _filter.value = filter }
    fun setSearch(query: String)    { _search.value = query }

    fun insertManual(
        type       : TransactionType,
        amountKopecks: Long,
        merchant   : String,
        categoryId : String?,
        note       : String?,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            txRepo.insert(
                TransactionEntity(
                    id            = UUID.randomUUID().toString(),
                    smsId         = null,
                    accountId     = null,
                    categoryId    = categoryId,
                    type          = type,
                    source        = TransactionSource.MANUAL,
                    amountKopecks = if (type == TransactionType.EXPENSE) -kotlin.math.abs(amountKopecks)
                                    else kotlin.math.abs(amountKopecks),
                    merchant      = merchant.ifBlank { null },
                    description   = note?.ifBlank { null },
                    timestamp     = now,
                    isDeleted     = false,
                    deletedAt     = null,
                )
            )
        }
    }
}
