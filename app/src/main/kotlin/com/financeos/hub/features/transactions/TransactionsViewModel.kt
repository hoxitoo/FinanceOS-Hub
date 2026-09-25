package com.financeos.hub.features.transactions

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.parser.MerchantNames
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

enum class TxFilter(val label: String) {
    ALL("Все"),
    EXPENSE("Расходы"),
    INCOME("Доходы"),
    // Переводов в фильтре не было вовсе, хотя тип первоклассный: погашение кредитки и
    // перекладывание между своими счетами нельзя было отделить от трат никак.
    TRANSFER("Переводы"),
}

/**
 * Отрезок дат, по которому сужается список. `null` — без ограничения.
 *
 * Обе границы — ДНИ, а не метки времени: человек выбирает «с 1 по 7 сентября», а не «с 1 сентября
 * 00:00:00.000». Сравнение идёт по началу дня операции, поэтому операция в 23:59 последнего дня
 * попадает внутрь — иначе выбранный день молча терял бы вечерние покупки.
 */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    val single: Boolean get() = from == to
}

data class TransactionsState(
    val grouped        : Map<Long, List<TransactionEntity>> = emptyMap(),
    val activeFilter   : TxFilter                           = TxFilter.ALL,
    val dateRange      : DateRange?                         = null,
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
    private val accountLinker : com.financeos.hub.core.account.AccountLinker,
    savedStateHandle        : SavedStateHandle,
) : ViewModel() {

    private val _pdfState = MutableStateFlow<PdfImportState>(PdfImportState.Idle)
    val pdfImportState: StateFlow<PdfImportState> = _pdfState.asStateFlow()

    fun dismissPdfResult() { _pdfState.value = PdfImportState.Idle }

    private val _filter         = MutableStateFlow(TxFilter.ALL)
    private val _search         = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<String?>(savedStateHandle["categoryId"])
    private val _dateRange      = MutableStateFlow<DateRange?>(null)

    /** Всё, чем сужается список. Собрано в один поток: `combine` принимает не больше пяти. */
    private data class Filters(
        val type    : TxFilter,
        val query   : String,
        val category: String?,
        val dates   : DateRange?,
    )

    fun clearCategoryFilter() { _categoryFilter.value = null }

    val state = combine(
        txRepo.observeAll(),
        categoryRepo.observeAll(),
        accountRepo.observeAll(),
        cardRepo.observeAll(),
        combine(_filter, _search, _categoryFilter, _dateRange) { f, s, c, d -> Filters(f, s, c, d) },
    ) { txList, categories, accounts, cards, filters ->
        val filter    = filters.type
        val query     = filters.query
        val catFilter = filters.category
        val dates     = filters.dates
        val catMap = categories.associate { it.id to it.name }

        val filtered = txList
            .filter { tx ->
                when (filter) {
                    TxFilter.ALL      -> true
                    TxFilter.EXPENSE  -> tx.type == TransactionType.EXPENSE
                    TxFilter.INCOME   -> tx.type == TransactionType.INCOME
                    TxFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
                }
            }
            .filter { tx ->
                // Сравниваем ДНИ, а не метки времени: иначе выбранный день обрезался бы по
                // полуночи и терял всё, что куплено вечером.
                if (dates == null) true else {
                    val day = Instant.ofEpochMilli(tx.timestamp)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    !day.isBefore(dates.from) && !day.isAfter(dates.to)
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
                    // И по разобранному имени тоже: в списке написано «Aeza», а в самой операции
                    // лежит «Аеза VPS» кириллицей — искать по видимому слову и не находить ничего
                    // хуже, чем не чистить название вовсе.
                    MerchantNames.display(tx.merchant)?.lowercase()?.contains(q) == true ||
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
            dateRange      = dates,
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
    fun setDateRange(range: DateRange?) { _dateRange.value = range }

    fun updateTransaction(
        tx         : TransactionEntity,
        newType    : TransactionType,
        merchant   : String,
        categoryId : String?,
        note       : String?,
    ) {
        viewModelScope.launch {
            // Re-sign the amount to match the new type: expense negative, income positive; a transfer
            // keeps its original direction. Magnitude is preserved, so balances are unaffected — only
            // how analytics counts the row changes.
            val mag = kotlin.math.abs(tx.amountKopecks)
            val newAmount = when (newType) {
                TransactionType.EXPENSE  -> -mag
                TransactionType.INCOME   ->  mag
                TransactionType.TRANSFER -> tx.amountKopecks
            }
            // Смена типа разрывает спаривание переводов (пара — это две ноги ОДНОГО перевода),
            // но привязку к цели больше не рвёт: цель следует за деньгами на счёте независимо от
            // того, назвали операцию тратой или переводом.
            val leftTransfer = tx.type == TransactionType.TRANSFER && newType != TransactionType.TRANSFER
            // Знак мог перевернуться (расход → доход). Зачисление в цель считается по знаку строки,
            // поэтому старое надо снять и применить новое — иначе цель осталась бы с прежним
            // вкладом, вдвое разошедшимся с историей.
            val amountFlipped = tx.goalId != null && newAmount != tx.amountKopecks
            if (amountFlipped) transferRouter.onTransactionReversed(tx)

            val updated = tx.copy(
                    type           = newType,
                    amountKopecks  = newAmount,
                    merchant       = merchant.ifBlank { null },
                    categoryId     = categoryId,
                    description    = note,
                    goalId         = tx.goalId,
                    transferPairId = if (leftTransfer) null else tx.transferPairId,
                    updatedAt      = System.currentTimeMillis(),
            )
            txRepo.update(updated)
            if (amountFlipped) transferRouter.onManualRowInserted(updated)
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            // Reverse the balance effect of any op that moved the balance as a DELTA we own, so
            // deletion is symmetric with insertion. That covers MANUAL entries AND any SMS/PUSH row
            // that carried NO authoritative "Остаток" (balanceKopecks == null → syncBalance applied
            // `acc.balance + signedDelta` at insert). Rows that DID carry a bank balance set an
            // absolute snapshot, not a delta, so they're left as-is; PDF rows have no account.
            // This lets the user undo a mis-parsed push (e.g. a marketing "transfer" that wrongly
            // debited 163 000 ₽) simply by deleting it — previously that delta stuck forever.
            val tx = txRepo.getById(id)
            if (tx != null && tx.accountId != null && tx.balanceKopecks == null &&
                tx.source != TransactionSource.PDF) {
                val acc = accountRepo.getById(tx.accountId)
                if (acc != null) {
                    accountRepo.upsert(acc.copy(
                        balanceKopecks = acc.balanceKopecks - tx.amountKopecks,
                        updatedAt      = System.currentTimeMillis(),
                    ))
                }
            }
            // An UNPAIRED transfer also credited its counterparty account at insert
            // (TransferRouter moves the other leg, because the bank books an internal transfer on
            // one account only). Undo that too, or the destination keeps the money forever while
            // the source is restored. Paired transfers are excluded on purpose: there the second
            // leg is its own row and still represents that side of the move.
            if (tx != null && tx.type == TransactionType.TRANSFER && tx.transferPairId == null) {
                tx.counterpartyMask
                    ?.let { accountLinker.resolveAccountId(it) }
                    ?.takeIf { it != tx.accountId }
                    ?.let { destId ->
                        val magnitude = kotlin.math.abs(tx.amountKopecks)
                        val credited  = if (tx.amountKopecks < 0) magnitude else -magnitude
                        accountLinker.adjustBalance(destId, -credited)
                    }
            }
            // A transfer that funded a savings goal must un-fund it on delete, else the goal
            // stays permanently inflated by money no longer backed by a transaction.
            if (tx != null && tx.goalId != null) {
                transferRouter.onTransactionReversed(tx)
            }
            txRepo.softDelete(id)

            // Перевод между своими счетами — ОДНО событие, записанное двумя строками. Удалив только
            // ту, что попалась под руку, человек вернул бы деньги одному счёту и оставил второй
            // сдвинутым навсегда: откатить чужую ногу нечем, она сама себе запись. Поэтому парная
            // строка удаляется вместе с этой и откатывает свой счёт сама.
            tx?.transferPairId?.let { pairId ->
                txRepo.transferPair(pairId)
                    .filter { it.id != id }
                    .forEach { leg ->
                        // Вторая нога могла зачислить в свою цель (у сторон перевода цели разные).
                        // Без этого отката удалённый перевод оставлял бы цель приёмника наполненной
                        // деньгами, которых больше нет ни на счёте, ни в истории.
                        if (leg.goalId != null) transferRouter.onTransactionReversed(leg)
                        if (leg.accountId != null && leg.balanceKopecks == null &&
                            leg.source != TransactionSource.PDF
                        ) {
                            accountRepo.getById(leg.accountId)?.let { acc ->
                                accountRepo.upsert(acc.copy(
                                    balanceKopecks = acc.balanceKopecks - leg.amountKopecks,
                                    updatedAt      = System.currentTimeMillis(),
                                ))
                            }
                        }
                        txRepo.softDelete(leg.id)
                    }
            }
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
        destAccountId: String? = null,   // TRANSFER only: the account the money arrives on
        timestamp    : Long    = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val mag = kotlin.math.abs(amountKopecks)
            // Tag the manual op with the chosen account's currency so a non-RUB account (e.g. a
            // МБанк USD/сом card) renders the correct symbol in history instead of ₽.
            val acc = accountId?.let { accountRepo.getById(it) }

            // Перевод между СВОИМИ счетами пишется ДВУМЯ строками с общим transferPairId — так же,
            // как это делает «Погасить» (инвариант #16). Раньше здесь была одна строка, которая
            // молча двигала баланс второго счёта, не оставляя следа: удалить её значило вернуть
            // деньги источнику и НАВСЕГДА оставить приёмник сдвинутым. Именно так «удалил погашение
            // — долг по карте не вернулся»: строка знала только свой счёт, а второй восстановить
            // было нечем.
            val pairId = if (type == TransactionType.TRANSFER &&
                destAccountId != null && destAccountId != accountId
            ) UUID.randomUUID().toString() else null

            val row = TransactionEntity(
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
                    currency      = acc?.currency ?: "RUB",
                    transferPairId = pairId,
                    isDeleted     = false,
                    deletedAt     = null,
            )
            txRepo.insert(row)
            // Ручная операция тоже проходит привязку к цели. Раньше этого вызова здесь не было
            // вовсе: перевод на привязанный счёт цель не двигал, а удаление такой операции цель
            // уменьшало — зачисления нет, списание есть.
            transferRouter.onManualRowInserted(row)
            // Reflect the operation on the chosen account's balance so the dashboard stays in sync.
            if (accountId != null) {
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
            // Встречная нога: своя строка на счёте-приёмнике. Она и двигает его баланс, и позволяет
            // этот сдвиг отменить — откатить можно только тот счёт, на котором строка лежит.
            if (pairId != null && destAccountId != null) {
                accountRepo.getById(destAccountId)?.let { dest ->
                    val incomingLeg = TransactionEntity(
                            id             = UUID.randomUUID().toString(),
                            smsId          = null,
                            accountId      = dest.id,
                            categoryId     = null,   // перевод не трата, категории у него нет
                            type           = TransactionType.TRANSFER,
                            source         = TransactionSource.MANUAL,
                            amountKopecks  = mag,    // деньги пришли
                            merchant       = merchant.ifBlank { null } ?: "Перевод",
                            description    = acc?.let { "с «${it.name}»" },
                            timestamp      = timestamp,
                            currency       = dest.currency,
                            transferPairId = pairId,
                    )
                    txRepo.insert(incomingLeg)
                    // Цель, привязанная к счёту-ПРИЁМНИКУ, растёт именно на этой ноге: первая
                    // лежит на счёте-источнике и о приёмнике ничего не знает.
                    transferRouter.onManualRowInserted(incomingLeg)
                    accountRepo.upsert(dest.copy(
                        balanceKopecks = dest.balanceKopecks + mag,
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
