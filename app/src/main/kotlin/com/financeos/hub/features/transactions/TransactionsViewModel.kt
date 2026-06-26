package com.financeos.hub.features.transactions

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.pdf.PdfImporter
import com.financeos.hub.core.pdf.PdfTransactionParser
import com.financeos.hub.core.transfer.TransferRouter
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.CardRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val grouped        : Map<Long, List<TransactionEntity>> = emptyMap(),
    val activeFilter   : TxFilter                           = TxFilter.ALL,
    val searchQuery    : String                             = "",
    val categories     : List<CategoryEntity>               = emptyList(),
    val categoryFilter : String?                            = null,
    val accounts       : List<AccountEntity>               = emptyList(),
    val cards          : List<CardEntity>                  = emptyList(),
    private val categoryMap: Map<String, String>            = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categoryMap[it] } ?: "Другое"
}

data class PdfImportResult(val found: Int, val inserted: Int)

sealed interface PdfImportState {
    object Idle    : PdfImportState
    object Loading : PdfImportState
    data class Success(val result: PdfImportResult) : PdfImportState
    data class Error(val message: String)           : PdfImportState
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val txRepo      : TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo : AccountRepository,
    private val cardRepo    : CardRepository,
    private val pdfImporter : PdfImporter,
    private val classifier  : CategoryClassifier,
    private val transferRouter: TransferRouter,
    savedStateHandle        : SavedStateHandle,
) : ViewModel() {

    private val _pdfState = MutableStateFlow<PdfImportState>(PdfImportState.Idle)
    val pdfImportState: StateFlow<PdfImportState> = _pdfState.asStateFlow()

    fun dismissPdfResult() { _pdfState.value = PdfImportState.Idle }

    private val _filter         = MutableStateFlow(TxFilter.ALL)
    private val _search         = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<String?>(savedStateHandle["categoryId"])

    fun clearCategoryFilter() { _categoryFilter.value = null }

    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
        accountRepo.observeAll(),
        cardRepo.observeAll(),
        combine(_filter, _search, _categoryFilter) { f, s, c -> Triple(f, s, c) },
    ) { txList, categories, accounts, cards, (filter, query, catFilter) ->
        val catMap = categories.associate { it.id to it.name }

        val filtered = txList
            .filter { tx ->
                when (filter) {
                    TxFilter.ALL     -> true
                    TxFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                    TxFilter.INCOME  -> tx.type == TransactionType.INCOME
                }
            }
            .filter { tx ->
                if (catFilter == null) true else tx.categoryId == catFilter
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
            grouped        = grouped,
            activeFilter   = filter,
            searchQuery    = query,
            categories     = categories,
            categoryFilter = catFilter,
            accounts       = accounts,
            cards          = cards,
            categoryMap    = catMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun setFilter(filter: TxFilter) { _filter.value = filter }
    fun setSearch(query: String)    { _search.value = query }

    fun updateTransaction(
        tx         : TransactionEntity,
        newType    : TransactionType,
        merchant   : String,
        categoryId : String?,
        note       : String?,
    ) {
        viewModelScope.launch {
            // Reclassifying away from TRANSFER (e.g. "перевод другу" → расход) must undo any goal
            // routing this transfer applied, otherwise the goal stays inflated by money now counted
            // as a plain expense/income.
            val leftTransfer = tx.type == TransactionType.TRANSFER && newType != TransactionType.TRANSFER
            if (leftTransfer && tx.goalId != null) {
                transferRouter.onTransactionReversed(tx)
            }
            // Re-sign the amount to match the new type: expense negative, income positive; a transfer
            // keeps its original direction. Magnitude is preserved, so balances are unaffected — only
            // how analytics counts the row changes.
            val mag = kotlin.math.abs(tx.amountKopecks)
            val newAmount = when (newType) {
                TransactionType.EXPENSE  -> -mag
                TransactionType.INCOME   ->  mag
                TransactionType.TRANSFER -> tx.amountKopecks
            }
            txRepo.update(
                tx.copy(
                    type           = newType,
                    amountKopecks  = newAmount,
                    merchant       = merchant.ifBlank { null },
                    categoryId     = categoryId,
                    description    = note,
                    // Once it is no longer a transfer, drop the transfer-only links so it can't be
                    // re-paired or counted against a goal.
                    goalId         = if (leftTransfer) null else tx.goalId,
                    transferPairId = if (leftTransfer) null else tx.transferPairId,
                    updatedAt      = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            // Reverse the balance effect of a MANUAL op before soft-deleting it: insertManual()
            // applied its signed amount to the chosen account, so deletion must undo it.
            // SMS/PUSH balances come from the bank's authoritative "Остаток" snapshot (not a
            // delta we own), and PDF rows have no account — so we only reverse MANUAL entries.
            val tx = txRepo.getById(id)
            if (tx != null && tx.accountId != null && tx.source == TransactionSource.MANUAL) {
                val acc = accountRepo.getById(tx.accountId)
                if (acc != null) {
                    accountRepo.upsert(acc.copy(
                        balanceKopecks = acc.balanceKopecks - tx.amountKopecks,
                        updatedAt      = System.currentTimeMillis(),
                    ))
                }
            }
            // A transfer that funded a savings goal must un-fund it on delete, else the goal
            // stays permanently inflated by money no longer backed by a transaction.
            if (tx != null && tx.goalId != null) {
                transferRouter.onTransactionReversed(tx)
            }
            txRepo.softDelete(id)
        }
    }

    fun insertManual(
        type         : TransactionType,
        amountKopecks: Long,
        merchant     : String,
        categoryId   : String?,
        note         : String?,
        accountId    : String? = null,
        sourceMask   : String? = null,
        timestamp    : Long    = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val mag = kotlin.math.abs(amountKopecks)
            txRepo.insert(
                TransactionEntity(
                    id            = UUID.randomUUID().toString(),
                    smsId         = null,
                    accountId     = accountId,
                    categoryId    = categoryId,
                    type          = type,
                    source        = TransactionSource.MANUAL,
                    amountKopecks = when (type) {
                        TransactionType.EXPENSE  -> -mag
                        TransactionType.INCOME   ->  mag
                        TransactionType.TRANSFER -> -mag   // manual transfer treated as outgoing
                    },
                    merchant      = merchant.ifBlank { null },
                    description   = note?.ifBlank { null },
                    timestamp     = timestamp,
                    sourceMask    = sourceMask,
                    isDeleted     = false,
                    deletedAt     = null,
                )
            )
            // Reflect the operation on the chosen account's balance so the dashboard stays in sync.
            if (accountId != null) {
                val acc = accountRepo.getById(accountId)
                if (acc != null) {
                    val delta = when (type) {
                        TransactionType.INCOME   ->  mag
                        TransactionType.EXPENSE  -> -mag
                        TransactionType.TRANSFER -> -mag
                    }
                    accountRepo.upsert(acc.copy(
                        balanceKopecks = acc.balanceKopecks + delta,
                        updatedAt      = now,
                    ))
                }
            }
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _pdfState.value = PdfImportState.Loading
            runCatching {
                withContext(Dispatchers.IO) {
                    val text        = pdfImporter.extractText(uri)
                    val parsed      = PdfTransactionParser.parse(text)
                    val existingIds = txRepo.getAllSmsHashes().toHashSet()

                    var inserted = 0
                    val now = System.currentTimeMillis()

                    parsed.forEach { raw ->
                        if (raw.dedupKey in existingIds) return@forEach
                        val catId = runCatching {
                            classifier.classify(raw.merchant, null)
                        }.getOrNull()
                        txRepo.insert(
                            TransactionEntity(
                                id            = UUID.randomUUID().toString(),
                                smsId         = raw.dedupKey,
                                accountId     = null,
                                categoryId    = catId,
                                type          = raw.type,
                                source        = TransactionSource.PDF,
                                amountKopecks = if (raw.type == TransactionType.EXPENSE)
                                                    -raw.amountKopecks else raw.amountKopecks,
                                merchant      = raw.merchant,
                                description   = null,
                                timestamp     = raw.timestampMillis,
                                isDeleted     = false,
                                deletedAt     = null,
                                createdAt     = now,
                                updatedAt     = now,
                            )
                        )
                        inserted++
                    }
                    PdfImportResult(found = parsed.size, inserted = inserted)
                }
            }.onSuccess { result ->
                _pdfState.value = PdfImportState.Success(result)
            }.onFailure { e ->
                _pdfState.value = PdfImportState.Error(e.message ?: "Ошибка импорта PDF")
            }
        }
    }

    fun buildCsvString(): String {
        val s    = state.value
        val zone = ZoneId.systemDefault()
        val sb   = StringBuilder()
        sb.appendLine("Дата,Тип,Сумма (₽),Получатель,Категория,Примечание")

        s.grouped.entries
            .sortedByDescending { it.key }
            .forEach { (_, txList) ->
                txList.sortedByDescending { it.timestamp }.forEach { tx ->
                    val date     = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                    val type     = if (tx.type == TransactionType.EXPENSE) "Расход" else "Доход"
                    val amount   = kotlin.math.abs(tx.amountKopecks) / 100.0
                    sb.appendLine(
                        listOf(
                            date.toString(),
                            type,
                            amount.toString(),
                            csvField(tx.merchant),
                            csvField(s.categoryName(tx.categoryId)),
                            csvField(tx.description),
                        ).joinToString(",")
                    )
                }
            }

        return sb.toString()
    }

    /**
     * Escapes a CSV field per RFC 4180 (quote-wrap when it contains a comma, quote, or
     * newline; double internal quotes) and neutralises spreadsheet formula injection by
     * prefixing a leading =, +, -, or @ with a single quote.
     */
    private fun csvField(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val guarded = if (raw.first() in "=+-@\t\r") "'$raw" else raw
        return if (guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else {
            guarded
        }
    }
}
