package com.financeos.hub.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.analytics.AnalyticsEngine
import com.financeos.hub.core.credit.creditCycle
import com.financeos.hub.core.credit.debtKopecks
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
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
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class DashboardState(
    val heroVariant         : String                    = "CALM",
    /** CASH accounts only — a credit line is the bank's money, not part of your state. */
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
    /** Aggregate of every active CREDIT account — drives the dashboard tile. */
    val credit              : CreditSummary?            = null,
    private val categories  : Map<String, String>       = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categories[it] } ?: "Другое"

    /** Accounts shown in the «Счета» carousel: credit cards live on their own screen instead. */
    val cashAccounts: List<AccountEntity>
        get() = accounts.filter { it.kind != AccountKind.CREDIT }
}

/**
 * Rolled-up credit position. Null when the user has no credit cards, which is what hides the
 * dashboard tile entirely — nobody should see an empty "Кредитные карты" block they never asked for.
 *
 * Amounts are summed across currencies as-is. That is only meaningful while every card is in the
 * same currency; a multi-currency credit portfolio would need the same per-currency breakdown the
 * net worth already has, and is deliberately out of scope until someone actually has one.
 */
data class CreditSummary(
    val cardCount    : Int,
    /** Total owed, positive magnitude. */
    val debtKopecks  : Long,
    /** Sum of configured limits; 0 when no card has one entered. */
    val limitKopecks : Long,
    /** limit − debt, floored at 0. */
    val freeKopecks  : Long,
    /** Days to the nearest payment across all cards; null when no card has its terms configured. */
    val daysUntilDue : Int?,
)

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
        // Net worth is CASH only. Folding a credit card in would make a purchase on it read as
        // your state falling and a repayment as it rising — the same money counted twice.
        val cashAccounts = accounts.filter { it.kind == AccountKind.CASH }
        val netWorth = cashAccounts.sumOf { it.balanceKopecks }
        val netWorthByCurrency = cashAccounts.groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.balanceKopecks } }
        val credit = summariseCredit(accounts.filter { it.kind == AccountKind.CREDIT })

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
            credit               = credit,
            categories           = catMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private fun summariseCredit(cards: List<AccountEntity>): CreditSummary? {
        if (cards.isEmpty()) return null
        val today = LocalDate.now()
        val debt  = cards.sumOf { it.debtKopecks }
        val limit = cards.sumOf { it.creditLimitKopecks ?: 0L }
        // Soonest deadline wins the tile: with several cards the one about to fall due is the only
        // one worth surfacing in a single line. Cards with no terms entered contribute nothing.
        val soonest = cards
            .mapNotNull { creditCycle(it.statementDay, it.dueDays, today)?.daysUntilDue }
            .minOrNull()
        return CreditSummary(
            cardCount    = cards.size,
            debtKopecks  = debt,
            limitKopecks = limit,
            freeKopecks  = (limit - debt).coerceAtLeast(0L),
            daysUntilDue = soonest,
        )
    }

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

    fun createAccount(draft: AccountDraft) {
        viewModelScope.launch {
            val account = AccountEntity(
                id             = UUID.randomUUID().toString(),
                name           = draft.name,
                bank           = draft.bank,
                cardMask       = draft.cardMask,
                balanceKopecks = draft.balanceKopecks,
                currency       = draft.currency,
                kind           = draft.kind,
                creditLimitKopecks = draft.creditLimitKopecks,
                aprBp              = draft.aprBp,
                statementDay       = draft.statementDay,
                dueDays            = draft.dueDays,
                minPaymentBp       = draft.minPaymentBp,
            )
            accountRepo.upsert(account)
            // Attach any transactions already ingested for this card (and reconcile to the
            // bank-authoritative balance) — they may have arrived before the account existed.
            accountLinker.relinkOrphans(account.id, draft.cardMask)
        }
    }

    /** Edits the credit terms of an existing card (limit, rate, statement day, days to pay). */
    fun updateCreditTerms(
        account           : AccountEntity,
        creditLimitKopecks: Long?,
        aprBp             : Int?,
        statementDay      : Int?,
        dueDays           : Int?,
        minPaymentBp      : Int?,
    ) {
        viewModelScope.launch {
            accountRepo.upsert(account.copy(
                creditLimitKopecks = creditLimitKopecks,
                aprBp              = aprBp,
                statementDay       = statementDay,
                dueDays            = dueDays,
                minPaymentBp       = minPaymentBp,
                updatedAt          = System.currentTimeMillis(),
            ))
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
