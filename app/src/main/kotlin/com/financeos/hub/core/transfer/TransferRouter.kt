package com.financeos.hub.core.transfer

import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.core.notifications.NotificationHelper
import com.financeos.hub.data.repositories.GoalRepository
import com.financeos.hub.data.repositories.TransferRouteRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Routes bank-transfer transactions:
 *  (A) outgoing transfers matching a linked card/keyword → add to a savings goal,
 *  (B) internal transfers between two tracked accounts → pair so net worth is unchanged,
 *  (C) unrouted larger outgoing transfers → a "назначить в цель?" push.
 */
@Singleton
class TransferRouter @Inject constructor(
    private val transferRouteRepo: TransferRouteRepository,
    private val goalRepo: GoalRepository,
    private val transactionDao: TransactionDao,
    private val notificationHelper: NotificationHelper,
    private val accountLinker: AccountLinker,
) {
    private companion object {
        /** Window for matching the opposite leg of the same internal transfer. */
        const val PAIR_WINDOW_MS = 10 * 60 * 1000L
    }

    /**
     * Called AFTER a transaction row is inserted. Handles goal routing + internal pairing
     * for TRANSFER-typed transactions. [tx] is the already-inserted entity (has signed amount).
     */
    suspend fun onTransactionInserted(
        tx: TransactionEntity,
        rawSms: String?,
        counterpartyMask: String?,
    ) {
        runCatching {
            if (tx.type != TransactionType.TRANSFER) return
            val magnitude = abs(tx.amountKopecks)
            val outgoing  = tx.amountKopecks < 0

            // (A) Goal routing
            val routes = transferRouteRepo.getAllActive()

            // A transfer touches up to TWO tracked accounts:
            //   • the account this SMS/push is booked on (tx.accountId — the source for an
            //     outgoing transfer, the destination for an incoming one), and
            //   • the counterparty account named in the body ("на счёт *NNNN"), resolved here.
            // A goal linked to an account moves with the money flowing into/out of THAT account:
            // money arriving → +progress, money leaving → −progress. Without the counterparty
            // leg, an outgoing transfer INTO a goal-linked savings account never funded the goal,
            // because the push is booked on the (unlinked) source account.
            val destAccountId = counterpartyMask
                ?.let { accountLinker.resolveAccountId(it) }
                ?.takeIf { it != tx.accountId }

            // Move the OTHER leg's money. The bank books an internal transfer on ONE account only
            // (e.g. Alfa's "со счета 1139 на счет 3583" push), and syncBalance already applied this
            // row to tx.accountId — so without crediting the counterparty the amount just disappears
            // from net worth. Skipped when a counterpart row already exists, because then each
            // account received its own push and moving money here would double-count.
            if (destAccountId != null) {
                val counterpartExists = transactionDao.findTransferCounterpart(
                    selfId    = tx.id,
                    magnitude = magnitude,
                    outgoing  = if (outgoing) 1 else 0,
                    fromTs    = tx.timestamp - PAIR_WINDOW_MS,
                    toTs      = tx.timestamp + PAIR_WINDOW_MS,
                    centerTs  = tx.timestamp,
                ) != null
                if (!counterpartExists) {
                    accountLinker.adjustBalance(destAccountId, if (outgoing) magnitude else -magnitude)
                }
            }

            val accountLegs = buildList {
                tx.accountId?.let { add(it to if (outgoing) -magnitude else magnitude) }
                destAccountId?.let { add(it to if (outgoing) magnitude else -magnitude) }
            }
            for ((accId, delta) in accountLegs) {
                val route = routes.firstOrNull { r ->
                    r.matchType == TransferMatchType.ACCOUNT && r.matchValue == accId
                } ?: continue
                goalRepo.contribute(route.goalId, delta)
                transactionDao.setGoal(tx.id, route.goalId)
                return   // routed; skip pairing
            }

            // CARD / KEYWORD routes — only for outgoing transfers (money put INTO savings)
            if (outgoing) {
                val match = routes.firstOrNull { r ->
                    when (r.matchType) {
                        TransferMatchType.CARD ->
                            counterpartyMask != null && r.matchValue.equals(counterpartyMask, ignoreCase = true)
                        TransferMatchType.KEYWORD ->
                            rawSms != null && rawSms.contains(r.matchValue, ignoreCase = true)
                        TransferMatchType.ACCOUNT -> false  // handled above
                    }
                }
                if (match != null) {
                    goalRepo.contribute(match.goalId, magnitude)
                    transactionDao.setGoal(tx.id, match.goalId)
                    return   // routed to goal; don't also pair
                }
                // (C) Push fallback: unrouted outgoing transfer >= 1000 RUB
                if (magnitude >= 100_000L) {   // 1000.00 rub in kopecks
                    notificationHelper.notifyUnroutedTransfer(magnitude)
                }
            }

            // (B) Internal pairing — find opposite-sign equal-magnitude counterpart within +/-10 min
            val counterpart = transactionDao.findTransferCounterpart(
                selfId   = tx.id,
                magnitude = magnitude,
                outgoing  = if (outgoing) 1 else 0,
                fromTs    = tx.timestamp - PAIR_WINDOW_MS,
                toTs      = tx.timestamp + PAIR_WINDOW_MS,
                centerTs  = tx.timestamp,
            )
            if (counterpart != null) {
                val pairId = UUID.randomUUID().toString()
                transactionDao.markAsPairedTransfer(tx.id, pairId)
                transactionDao.markAsPairedTransfer(counterpart.id, pairId)

                // Both legs of the same transfer arrived as separate rows. The FIRST leg already
                // moved BOTH sides (its own account via syncBalance, the counterparty via
                // adjustBalance above), so this leg's own balance application is a duplicate —
                // the destination would end up credited twice. Undo it, but only when:
                //  • the first leg really did credit THIS account (its counterparty resolves here),
                //    otherwise nothing was double-applied, and
                //  • this row moved the balance as a DELTA (balanceKopecks == null). A row carrying
                //    a bank «Остаток» set an absolute snapshot, which must not be nudged.
                val firstLegCreditedUs = counterpart.counterpartyMask
                    ?.let { accountLinker.resolveAccountId(it) } == tx.accountId
                if (firstLegCreditedUs && tx.accountId != null && tx.balanceKopecks == null) {
                    accountLinker.adjustBalance(tx.accountId, -tx.amountKopecks)
                }
            }
        }
    }

    /**
     * Reverses the goal contribution that [onTransactionInserted] applied, so deleting (or
     * un-routing) a transfer that funded a goal restores the goal's progress. Mirrors the
     * original sign exactly:
     *  - ACCOUNT routes applied the signed amount (incoming +, outgoing −) → undo with −signed.
     *  - CARD/KEYWORD routes applied +magnitude (outgoing only) → undo with −magnitude.
     * Best-effort: the goal's clamp to [0, target] means a reversal can't always be exact, but it
     * prevents a goal staying permanently inflated by a deleted transaction.
     */
    suspend fun onTransactionReversed(tx: TransactionEntity) {
        runCatching {
            val goalId = tx.goalId ?: return
            val magnitude = abs(tx.amountKopecks)
            val accountRoute = transferRouteRepo.getAllActive().firstOrNull {
                it.goalId == goalId && it.matchType == TransferMatchType.ACCOUNT
            }
            // Mirror exactly what onTransactionInserted applied to the goal:
            //  • own-account leg (route on tx.accountId)  → signed amount (tx.amountKopecks)
            //  • counterparty leg (route on the dest acct) → the opposite sign
            //  • CARD/KEYWORD                              → +magnitude (outgoing only)
            val appliedDelta = when {
                accountRoute == null -> magnitude
                accountRoute.matchValue == tx.accountId -> tx.amountKopecks
                else -> -tx.amountKopecks
            }
            goalRepo.contribute(goalId, -appliedDelta)
        }
    }
}
