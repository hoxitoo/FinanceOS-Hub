package com.financeos.hub.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.analytics.AnalyticsEngine
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.CardRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DashboardState(
    val heroVariant         : String                    = "CALM",
    val netWorthKopecks     : Long                      = 0L,
    val netWorthByCurrency  : Map<String, Long>         = emptyMap(),
    val incomeKopecks       : Long                      = 0L,
    val expenseKopecks      : Long                      = 0L,
    val forecastKopecks     : Long                      = 0L,
    val financialScore      : Int                       = 0,
    val scoreBreakdown      : com.financeos.hub.core.analytics.ScoreCalculator.ScoreBreakdown? = null,
    val sparkline           : List<Float>               = emptyList(),
    val accounts            : List<AccountEntity>       = emptyList(),
    val cards               : List<CardEntity>          = emptyList(),
    val recentTransactions  : List<TransactionEntity>   = emptyList(),
    val categoryEntities    : List<CategoryEntity>      = emptyList(),
    private val categories  : Map<String, String>       = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categories[it] } ?: "Другое"
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val txRepo      : TransactionRepository,
    private val accountRepo : AccountRepository,
    private val cardRepo    : CardRepository,
    categoryRepo            : CategoryRepository,
    private val prefs       : UserPreferences,
    private val engine      : AnalyticsEngine,
    private val accountLinker: AccountLinker,
    private val transferRouteRepo: com.financeos.hub.data.repositories.TransferRouteRepository,
    private val transferRouter: com.financeos.hub.core.transfer.TransferRouter,
) : ViewModel() {

    // Full breakdown (not just the total) so the dashboard can draw the multi-colour score donut.
    private val _score     = MutableStateFlow<com.financeos.hub.core.analytics.ScoreCalculator.ScoreBreakdown?>(null)
    private val _forecast  = MutableStateFlow(0L)
    private val _sparkline = MutableStateFlow<List<Float>>(emptyList())

    init {
        viewModelScope.launch {
            txRepo.observeCurrentMonth()
                .debounce(500)
                .collectLatest {
                    // Re-throw CancellationException so collectLatest can cancel in-flight work
                    // when a new emission arrives — bare runCatching{} would swallow it and
                    // let all three expensive DB calls run to completion even after cancellation.
                    try { _score.value     = engine.computeScore()        } catch (e: Exception) { if (e is CancellationException) throw e }
                    try { _forecast.value  = engine.forecastMonthEnd()    } catch (e: Exception) { if (e is CancellationException) throw e }
                    try { _sparkline.value = engine.sparkline30Days()     } catch (e: Exception) { if (e is CancellationException) throw e }
                }
        }
    }

    val state = combine(
        combine(
            txRepo.observeCurrentMonth(),
            accountRepo.observeAll(),
            categoryRepo.observeAll(),
            cardRepo.observeAll(),
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            val tx    = arr[0] as List<TransactionEntity>
            @Suppress("UNCHECKED_CAST")
            val accts = arr[1] as List<AccountEntity>
            @Suppress("UNCHECKED_CAST")
            val cats  = arr[2] as List<com.financeos.hub.core.database.entities.CategoryEntity>
            @Suppress("UNCHECKED_CAST")
            val cards = arr[3] as List<CardEntity>
            listOf<Any?>(tx, accts, cats, cards)
        },
        combine(
            prefs.heroVariant,
            _score,
            _forecast,
            _sparkline,
        ) { hero, score, forecast, sparkline ->
            listOf<Any?>(hero, score, forecast, sparkline)
        },
    ) { inner, meta ->
        @Suppress("UNCHECKED_CAST")
        val txList    = inner[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts  = inner[1] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = inner[2] as List<com.financeos.hub.core.database.entities.CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val cards     = inner[3] as List<CardEntity>

        @Suppress("UNCHECKED_CAST")
        val heroVariant = meta[0] as String
        val breakdown   = meta[1] as? com.financeos.hub.core.analytics.ScoreCalculator.ScoreBreakdown
        val score       = breakdown?.total ?: 0
        val forecast    = meta[2] as Long
        @Suppress("UNCHECKED_CAST")
        val sparkline   = meta[3] as List<Float>

        val catMap   = categories.associate { it.id to it.name }
        val income   = txList.filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountKopecks }
        val expense  = txList.filter { it.type == TransactionType.EXPENSE }
            .sumOf { kotlin.math.abs(it.amountKopecks) }
        val netWorth = accounts.sumOf { it.balanceKopecks }
        val netWorthByCurrency = accounts.groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.balanceKopecks } }

        DashboardState(
            heroVariant          = heroVariant,
            netWorthKopecks      = netWorth,
            netWorthByCurrency   = netWorthByCurrency,
            incomeKopecks        = income,
            expenseKopecks       = expense,
            forecastKopecks      = forecast,
            financialScore       = score,
            scoreBreakdown       = breakdown,
            sparkline            = sparkline,
            accounts             = accounts,
            cards                = cards,
            recentTransactions   = txList.take(5),
            categoryEntities     = categories,
            categories           = catMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    /** Edit a recent operation from the dashboard detail sheet. Mirrors TransactionsViewModel: re-signs
     *  the amount for the new type and un-routes a goal when a transfer is reclassified. */
    fun updateTransaction(
        tx: TransactionEntity,
        newType: TransactionType,
        merchant: String,
        categoryId: String?,
        note: String?,
    ) {
        viewModelScope.launch {
            val leftTransfer = tx.type == TransactionType.TRANSFER && newType != TransactionType.TRANSFER
            if (leftTransfer && tx.goalId != null) transferRouter.onTransactionReversed(tx)
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
                    goalId         = if (leftTransfer) null else tx.goalId,
                    transferPairId = if (leftTransfer) null else tx.transferPairId,
                    updatedAt      = System.currentTimeMillis(),
                )
            )
        }
    }

    fun createAccount(name: String, bank: String, cardMask: String?, balanceKopecks: Long, currency: String = "RUB") {
        viewModelScope.launch {
            val account = AccountEntity(
                id             = UUID.randomUUID().toString(),
                name           = name,
                bank           = bank,
                cardMask       = cardMask,
                balanceKopecks = balanceKopecks,
                currency       = currency,
            )
            accountRepo.upsert(account)
            // Attach any transactions already ingested for this card (and reconcile to the
            // bank-authoritative balance) — they may have arrived before the account existed.
            accountLinker.relinkOrphans(account.id, cardMask)
        }
    }

    fun updateAccountBalance(account: AccountEntity, newBalanceKopecks: Long) {
        viewModelScope.launch {
            accountRepo.upsert(account.copy(
                balanceKopecks = newBalanceKopecks,
                updatedAt      = System.currentTimeMillis(),
            ))
        }
    }

    /**
     * Manual "пересчитать баланс": adopt every orphan SMS/PUSH transaction whose card belongs to
     * this account and snap the balance to the bank's latest "Остаток". The reliable fix when a
     * debit on a second card didn't update the balance because it didn't resolve at ingest time.
     */
    fun reconcileAccount(accountId: String) {
        viewModelScope.launch { accountLinker.reconcileAccount(accountId, force = true) }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            // Cards belong to the account, and goal-routes may point at it. Deactivate both so a
            // deleted account leaves no zombie card (which then looks "auto-detached" when the user
            // re-creates a same-named account) and no stale goal link (which silently stops funding
            // the goal). The goal itself is kept — only its dangling link to the dead account is cut.
            cardRepo.deactivateByAccount(id)
            transferRouteRepo.removeAccountRoutes(id)
            accountRepo.deactivate(id)
        }
    }

    fun addCard(card: CardEntity) {
        viewModelScope.launch {
            cardRepo.addCard(card)
            // Re-link orphan transactions for this newly-registered card to its account and
            // reconcile the balance to the latest bank-reported "Остаток" (fixes the case where
            // a push for a second card landed before the card was added → balance left stale).
            accountLinker.relinkOrphans(card.accountId, card.cardMask)
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch { cardRepo.deactivate(id) }
    }
}
