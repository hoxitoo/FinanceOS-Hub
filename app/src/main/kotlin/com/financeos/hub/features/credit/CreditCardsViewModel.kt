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
            //
            // Spending and repayments are kept APART. Netting them made the amount due immune to
            // being paid: a repayment raised the balance and the rolled-back sum by the same
            // figure, so after settling the bill in full the card still asked for it — and the
            // repayment sheet prefilled that stale figure, inviting the user to pay twice.
            val sinceStatement = cycle?.let { c ->
                val cutoff = c.statementDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                accountTx.filter { it.timestamp >= cutoff }
            }.orEmpty()
            val purchasesSince  = -sinceStatement.filter { it.amountKopecks < 0 }.sumOf { it.amountKopecks }
            val repaymentsSince =  sinceStatement.filter { it.amountKopecks > 0 }.sumOf { it.amountKopecks }

            // balanceAtStatement = now − everything since; debt is its negation, floored at 0.
            val signedSince   = repaymentsSince - purchasesSince
            val statementDebt = if (cycle == null) account.debtKopecks
            else (-(account.balanceKopecks - signedSince)).coerceAtLeast(0L)
            // What is actually left to pay of that statement, after money already sent to it.
            // Capped by the live debt so an over-payment can never leave a phantom balance owing.
            val stillDue = (statementDebt - repaymentsSince)
                .coerceAtLeast(0L)
                .coerceAtMost(account.debtKopecks)

            val due = duePayment(
                reportedAmountKopecks = account.duePaymentKopecks,
                reportedDueDate       = account.duePaymentAt?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                },
                cycle                 = cycle,
                statementDebtKopecks  = stillDue,
                today                 = today,
            )

            CreditCardState(
                account             = account,
                cards               = cardList.filter { it.accountId == account.id },
                cycle               = cycle,
                statementDebt       = stillDue,
                duePayment          = due,
                // Purchases only — «Потрачено после выписки» means shopping, and netting a
                // repayment against it would understate (or hide) what rolls into the next bill.
                spentSinceStatement = purchasesSince,
                minPayment          = minPaymentKopecks(
                    debtKopecks  = stillDue,
                    minPaymentBp = account.minPaymentBp,
                    floorKopecks = account.minPaymentFloorKopecks,
                ),
                // Only what the deadline has already passed by earns interest; inside the
                // interest-free period the answer is a genuine, exact zero.
                // Once a payment is missed the tariff switches to the penalty rate («неустойка,
                // если пропустить обязательный платёж»), so an overdue card is not charged the
                // ordinary purchase rate. Falls back to the ordinary rate when no penalty is set.
                interestSoFar       = accruedInterest(
                    debtKopecks = account.debtKopecks,
                    aprBp       = account.penaltyAprBp ?: account.aprBp,
                    days        = due?.daysUntilDue?.let { if (it < 0) -it else 0 } ?: 0,
                ),
                minimumOutlook      = minimumPaymentOutlook(
                    debtKopecks  = account.debtKopecks,
                    aprBp        = account.aprBp,
                    minPaymentBp = account.minPaymentBp,
                    floorKopecks = account.minPaymentFloorKopecks,
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
     * TWO rows are written, one per account, linked by a shared `transferPairId`. A single row
     * would move both balances while only one of them could ever be undone: deleting it reverses
     * the account it sits on and leaves the other permanently wrong. With a row on each side, each
     * deletion reverses its own leg, and the repayment shows up in both accounts' histories —
     * which is also exactly the shape a bank-side repayment arrives in, as two pushes.
     */
    fun repay(
        cardId         : String,
        sourceAccountId: String,
        amountKopecks  : Long,
        note           : String? = null,
    ) {
        val amount = kotlin.math.abs(amountKopecks)
        if (amount <= 0L || cardId == sourceAccountId) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Both accounts are re-read here rather than taken from the captured UI state. That
            // state can be seconds stale — a push may have moved the balance, or CreditNoticeApplier
            // written a payment demand — and writing back a stale copy would silently revert it.
            val card   = accountRepo.getById(cardId) ?: return@launch
            val source = accountRepo.getById(sourceAccountId) ?: return@launch
            val pairId = UUID.randomUUID().toString()

            txRepo.insert(
                TransactionEntity(
                    id             = UUID.randomUUID().toString(),
                    smsId          = null,          // entered by hand, not ingested from a message
                    accountId      = source.id,
                    categoryId     = null,          // a transfer is not spending, so no category
                    type           = TransactionType.TRANSFER,
                    source         = TransactionSource.MANUAL,
                    amountKopecks  = -amount,       // leaving the source
                    merchant       = "Погашение кредитки",
                    description    = note?.ifBlank { null } ?: "на «${card.name}»",
                    timestamp      = now,
                    transferPairId = pairId,
                    currency       = source.currency,
                )
            )
            txRepo.insert(
                TransactionEntity(
                    id             = UUID.randomUUID().toString(),
                    smsId          = null,
                    accountId      = card.id,
                    categoryId     = null,
                    type           = TransactionType.TRANSFER,
                    source         = TransactionSource.MANUAL,
                    amountKopecks  = amount,        // arriving on the card → debt shrinks
                    merchant       = "Погашение",
                    description    = note?.ifBlank { null } ?: "с «${source.name}»",
                    timestamp      = now,
                    transferPairId = pairId,
                    currency       = card.currency,
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
     * Saves the card's whole tariff AND its debt in ONE write.
     *
     * Deliberately not split per field: two `copy(...)` calls both branch off the same captured
     * entity, so whichever coroutine lands second writes back the other's stale field — correcting
     * the debt and the limit together would silently discard one of them.
     *
     * [debtKopecks] arrives as a positive magnitude and is negated here, at the storage boundary.
     */
    fun saveCard(account: AccountEntity, debtKopecks: Long, terms: CreditTermsState) {
        viewModelScope.launch {
            // Re-read rather than trusting the captured entity: a push may have moved the balance
            // or CreditNoticeApplier written a payment demand since the sheet opened, and writing
            // a stale copy back would silently revert it.
            val fresh = accountRepo.getById(account.id) ?: return@launch
            accountRepo.upsert(fresh.copy(
                balanceKopecks     = -kotlin.math.abs(debtKopecks),
                creditLimitKopecks = terms.limitKopecks,
                aprBp              = terms.aprBpValue,
                statementDay       = terms.statementDayValue,
                dueDays            = terms.dueDaysValue,
                minPaymentBp       = terms.minPaymentBpValue,
                minPaymentFloorKopecks = terms.minPaymentFloorKopecks,
                interestFreeDays   = terms.interestFreeDaysValue,
                penaltyAprBp       = terms.penaltyAprBpValue,
                cashFeeBp          = terms.cashFeeBpValue,
                cashFeeFixedKopecks = terms.cashFeeFixedKopecks,
                updatedAt          = System.currentTimeMillis(),
            ))
        }
    }
}
