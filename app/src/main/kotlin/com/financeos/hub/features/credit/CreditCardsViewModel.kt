package com.financeos.hub.features.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.credit.CreditCycle
import com.financeos.hub.core.credit.DuePayment
import com.financeos.hub.core.credit.aprPercent
import com.financeos.hub.core.credit.creditCycle
import com.financeos.hub.core.credit.creditUtilization
import com.financeos.hub.core.credit.debtKopecks
import com.financeos.hub.core.credit.duePayment
import com.financeos.hub.core.credit.freeLimitKopecks
import com.financeos.hub.core.credit.MinimumPaymentOutlook
import com.financeos.hub.core.credit.accruedInterest
import com.financeos.hub.core.credit.minPaymentKopecks
import com.financeos.hub.core.credit.minimumPaymentOutlook
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.CardRepository
import com.financeos.hub.data.repositories.CategoryRepository
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** One credit card, with everything the screen needs already computed. */
data class CreditCardState(
    val account          : AccountEntity,
    val cards            : List<CardEntity>,
    val cycle            : CreditCycle?,
    /** Debt as of the last statement close — this is what has to be paid by the due date. */
    val statementDebt    : Long,
    /** Spent since the statement closed. Rolls into the NEXT statement, not the current bill. */
    val spentSinceStatement: Long,
    /** What to pay and by when — the bank's own demand when it sent one, else our inference. */
    val duePayment       : DuePayment?,
    val minPayment       : Long?,
    val freeLimit        : Long?,
    val utilization      : Float?,
    val aprPercent       : Double?,
    /**
     * Interest already run up, ESTIMATED. 0 while the card is inside its interest-free period —
     * that zero is exact. Non-zero only once the deadline has passed. Null without a rate.
     */
    val interestSoFar    : Long?,
    /** What paying only the minimum would cost. Null when the rate or the minimum % is unset. */
    val minimumOutlook   : MinimumPaymentOutlook?,
) {
    val debt: Long get() = account.debtKopecks
    /** True when nothing — neither a bank reminder nor entered terms — can date a payment. */
    val termsMissing: Boolean get() = duePayment == null
}

data class CreditScreenState(
    val isLoading   : Boolean               = true,
    val cards       : List<CreditCardState> = emptyList(),
    val totalDebt   : Long                  = 0L,
    val totalLimit  : Long                  = 0L,
    val totalFree   : Long                  = 0L,
    val history     : List<TransactionEntity> = emptyList(),
    /** Accounts a repayment can be made from — your own money, never another credit line. */
    val payFrom     : List<AccountEntity>     = emptyList(),
    private val categories: Map<String, String> = emptyMap(),
) {
    fun categoryName(id: String?): String = id?.let { categories[it] } ?: "Другое"

    /** Share of the total limit in use, or null when no card has a limit entered. */
    val utilization: Float?
        get() = totalLimit.takeIf { it > 0L }
            ?.let { (totalDebt.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
}

@HiltViewModel
class CreditCardsViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val txRepo     : TransactionRepository,
    cardRepo               : CardRepository,
    categoryRepo           : CategoryRepository,
) : ViewModel() {

    val state = combine(
        accountRepo.observeAll(),
        // The full history rather than a LIMIT-ed slice: the statement-debt roll-back has to see
        // EVERY transaction since the statement closed, and a truncated list would silently
        // understate the bill. Filtering happens in memory — the credit subset is small and this
        // screen is open briefly.
        txRepo.observeAll(),
        cardRepo.observeAll(),
        categoryRepo.observeAll(),
    ) { accountList, txList, cardList, catList ->
        val creditAccounts = accountList.filter { it.kind == AccountKind.CREDIT }
        val creditIds      = creditAccounts.mapTo(HashSet()) { it.id }
        val today          = LocalDate.now()

        val cardStates = creditAccounts.map { account ->
            val cycle = creditCycle(account.statementDay, account.dueDays, today)
            val accountTx = txList.filter { it.accountId == account.id }

            // Roll the current balance back to the statement close: everything booked after it
            // belongs to the next bill. This is an inference from the transaction log, not a figure
            // the bank told us — a manual balance edit in between will throw it off, which is why
            // the screen labels it as the statement amount rather than "the bank says".
            val spentSince = cycle?.let { c ->
                val cutoff = c.statementDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                accountTx.filter { it.timestamp >= cutoff }.sumOf { it.amountKopecks }
            } ?: 0L
            // balanceAtStatement = now − everything since; debt is its negation, floored at 0.
            val statementDebt = if (cycle == null) account.debtKopecks
            else (-(account.balanceKopecks - spentSince)).coerceAtLeast(0L)

            val due = duePayment(
                reportedAmountKopecks = account.duePaymentKopecks,
                reportedDueDate       = account.duePaymentAt?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                },
                cycle                 = cycle,
                statementDebtKopecks  = statementDebt,
                today                 = today,
            )

            CreditCardState(
                account             = account,
                cards               = cardList.filter { it.accountId == account.id },
                cycle               = cycle,
                statementDebt       = statementDebt,
                duePayment          = due,
                // Only outflow counts as "spent since" — a repayment in the same window is not
                // new spending and would otherwise show as a negative amount of shopping.
                spentSinceStatement = (-spentSince).coerceAtLeast(0L),
                minPayment          = minPaymentKopecks(statementDebt, account.minPaymentBp),
                // Only what the deadline has already passed by earns interest; inside the
                // interest-free period the answer is a genuine, exact zero.
                interestSoFar       = accruedInterest(
                    debtKopecks = account.debtKopecks,
                    aprBp       = account.aprBp,
                    days        = due?.daysUntilDue?.let { if (it < 0) -it else 0 } ?: 0,
                ),
                minimumOutlook      = minimumPaymentOutlook(
                    debtKopecks  = account.debtKopecks,
                    aprBp        = account.aprBp,
                    minPaymentBp = account.minPaymentBp,
                ),
                freeLimit           = account.freeLimitKopecks,
                utilization         = account.creditUtilization,
                aprPercent          = account.aprPercent,
            )
        }

        val totalDebt  = creditAccounts.sumOf { it.debtKopecks }
        val totalLimit = creditAccounts.sumOf { it.creditLimitKopecks ?: 0L }

        CreditScreenState(
            isLoading  = false,
            cards      = cardStates,
            totalDebt  = totalDebt,
            totalLimit = totalLimit,
            totalFree  = (totalLimit - totalDebt).coerceAtLeast(0L),
            history    = txList.filter { it.accountId in creditIds }.take(50),
            payFrom    = accountList.filter { it.kind == AccountKind.CASH },
            categories = catList.associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditScreenState())

    /**
     * Books a repayment: money leaves [sourceAccountId] and lands on the credit card, cancelling
     * that much debt.
     *
     * A TRANSFER, never an EXPENSE. Paying a card off does not consume anything — the purchases it
     * covers were already booked when they happened, so recording the repayment as spending would
     * count the same money twice and wreck every expense chart.
     *
     * The row is booked ON THE CARD as an incoming transfer (+amount, so the negative debt moves
     * toward zero) rather than on the source, for two reasons: it shows up in the card's own
     * history where the user looks for it, and the sign convention needs no special case — the
     * same `balance + amount` that credits a debit account cancels a credit card's debt.
     */
    fun repay(
        card           : AccountEntity,
        sourceAccountId: String,
        amountKopecks  : Long,
        note           : String? = null,
    ) {
        val amount = kotlin.math.abs(amountKopecks)
        if (amount <= 0L) return
        viewModelScope.launch {
            val now    = System.currentTimeMillis()
            val source = accountRepo.getById(sourceAccountId) ?: return@launch

            txRepo.insert(
                TransactionEntity(
                    id            = UUID.randomUUID().toString(),
                    smsId         = null,           // entered by hand, not ingested from a message
                    accountId     = card.id,
                    categoryId    = null,           // a transfer is not spending, so no category
                    type          = TransactionType.TRANSFER,
                    source        = TransactionSource.MANUAL,
                    amountKopecks = amount,         // incoming on the card → debt shrinks
                    merchant      = "Погашение",
                    description   = note?.ifBlank { null } ?: "с «${source.name}»",
                    timestamp     = now,
                    currency      = card.currency,
                )
            )

            // Both legs, so net worth is unchanged: the cash really left the source account, and
            // the card really owes that much less.
            accountRepo.upsert(card.copy(
                balanceKopecks = card.balanceKopecks + amount,
                updatedAt      = now,
            ))
            accountRepo.upsert(source.copy(
                balanceKopecks = source.balanceKopecks - amount,
                updatedAt      = now,
            ))
        }
    }

    /**
     * Saves the card's terms AND its debt in ONE write.
     *
     * Deliberately not two methods: two `account.copy(...)` calls both branch off the same captured
     * entity, so whichever coroutine lands second would write back the other's stale field — saving
     * a corrected debt and a new limit together would silently discard one of them.
     *
     * [debtKopecks] arrives as a positive magnitude and is negated here, at the storage boundary.
     */
    fun saveCard(
        account           : AccountEntity,
        debtKopecks       : Long,
        creditLimitKopecks: Long?,
        aprBp             : Int?,
        statementDay      : Int?,
        dueDays           : Int?,
        minPaymentBp      : Int?,
    ) {
        viewModelScope.launch {
            accountRepo.upsert(account.copy(
                balanceKopecks     = -kotlin.math.abs(debtKopecks),
                creditLimitKopecks = creditLimitKopecks,
                aprBp              = aprBp,
                statementDay       = statementDay,
                dueDays            = dueDays,
                minPaymentBp       = minPaymentBp,
                updatedAt          = System.currentTimeMillis(),
            ))
        }
    }
}
