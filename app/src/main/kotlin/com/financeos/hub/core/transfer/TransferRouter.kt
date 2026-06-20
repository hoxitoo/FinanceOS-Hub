package com.financeos.hub.core.transfer

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
) {
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

            // (A) Goal routing — ONLY for outgoing transfers (money put INTO savings)
            if (outgoing) {
                val routes = transferRouteRepo.getAllActive()
                val match = routes.firstOrNull { r ->
                    when (r.matchType) {
                        TransferMatchType.CARD ->
                            counterpartyMask != null && r.matchValue.equals(counterpartyMask, ignoreCase = true)
                        TransferMatchType.KEYWORD ->
                            rawSms != null && rawSms.contains(r.matchValue, ignoreCase = true)
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
            val window = 10 * 60 * 1000L
            val counterpart = transactionDao.findTransferCounterpart(
                selfId   = tx.id,
                magnitude = magnitude,
                outgoing  = if (outgoing) 1 else 0,
                fromTs    = tx.timestamp - window,
                toTs      = tx.timestamp + window,
                centerTs  = tx.timestamp,
            )
            if (counterpart != null) {
                val pairId = UUID.randomUUID().toString()
                transactionDao.markAsPairedTransfer(tx.id, pairId)
                transactionDao.markAsPairedTransfer(counterpart.id, pairId)
            }
        }
    }
}
