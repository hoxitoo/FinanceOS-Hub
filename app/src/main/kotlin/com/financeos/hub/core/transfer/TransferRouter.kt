package com.financeos.hub.core.transfer

import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.core.database.entities.TransferRouteEntity
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
            // (A) Goal routing
            val routes = transferRouteRepo.getAllActive()

            // Цель, привязанная к СЧЁТУ, следует за деньгами на этом счёте — любыми, не только
            // переводами. Пополнили накопительный счёт зачислением — цель выросла; сняли —
            // упала. До этого зачисление на привязанный счёт цель не двигало вовсе, и
            // «автопополнение» работало ровно в одном случае из трёх (инвариант #31).
            //
            // Остальное ниже — про переводы, и по делу: спаривание ног и маршруты по карте
            // получателя существуют только у переводов.
            if (tx.type != TransactionType.TRANSFER) {
                routeByOwnAccount(tx, routes)
                return
            }

            val magnitude = abs(tx.amountKopecks)
            val outgoing  = tx.amountKopecks < 0

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
     * Зачисление в цель для строки, которую создало САМО приложение (ручная операция).
     *
     * Отдельный вход, а не [onTransactionInserted], и это не дублирование. Тот написан под
     * банковское сообщение: он двигает баланс встречного счёта (банк присылает перевод одним пушем
     * на один счёт) и ищет парную строку. У ручной операции обе ноги уже написаны, оба баланса уже
     * применены — прогон через ту логику добавил бы деньги второй раз и переписал бы `transferPairId`
     * своим. Поэтому здесь ТОЛЬКО привязка к цели, без балансов и без спаривания.
     *
     * До этой правки ручная операция не доходила до маршрутизатора вовсе: перевод на привязанный
     * счёт цель не двигал, а вот УДАЛЕНИЕ такой операции цель уменьшало (`onTransactionReversed`
     * вызывался). Зачисления не было, списание было — и «Автопополнение» выглядело сломанным,
     * потому что таким и было.
     *
     * Знак берётся у самой строки: пришли деньги на привязанный счёт — цель растёт, ушли — падает.
     * Ровно то, что обещает подпись в листе привязки.
     */
    suspend fun onManualRowInserted(tx: TransactionEntity) {
        runCatching { routeByOwnAccount(tx, transferRouteRepo.getAllActive()) }
    }

    /**
     * Привязана ли цель этой строки к ЕЁ СЧЁТУ (а не к карте получателя или слову в СМС).
     *
     * Разница решает судьбу зачисления при смене типа операции. Привязка к счёту говорит «деньги
     * на этом счёте — это цель», и она верна для траты ровно так же, как для перевода. Привязка по
     * карте или слову говорит «ПЕРЕВОД туда-то — это пополнение», и перестаёт действовать, как
     * только операция перестала быть переводом: иначе «перевод другу», переназванный в расход,
     * навсегда остался бы засчитанным в накопления.
     */
    suspend fun isAccountRouted(tx: TransactionEntity): Boolean {
        val goalId    = tx.goalId ?: return false
        val accountId = tx.accountId ?: return false
        return transferRouteRepo.getAllActive().any {
            it.goalId == goalId &&
                it.matchType == TransferMatchType.ACCOUNT &&
                it.matchValue == accountId
        }
    }

    /**
     * Единственное правило привязки «цель ↔ счёт»: сколько денег пришло на счёт — столько и
     * прибавилось к цели, сколько ушло — столько убавилось.
     *
     * Знак берётся у самой строки, поэтому правило одинаково работает для зачисления, траты и
     * перевода, а откат (`onTransactionReversed`) — это тот же знак наоборот. Один расчёт на все
     * способы создать операцию: банковский пуш, импорт, ручной ввод.
     */
    private suspend fun routeByOwnAccount(
        tx: TransactionEntity,
        routes: List<TransferRouteEntity>,
    ) {
        val accountId = tx.accountId ?: return
        val route = routes.firstOrNull { r ->
            r.matchType == TransferMatchType.ACCOUNT && r.matchValue == accountId
        } ?: return
        goalRepo.contribute(route.goalId, tx.amountKopecks)
        transactionDao.setGoal(tx.id, route.goalId)
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
            val goalRoutes = transferRouteRepo.getAllActive().filter { it.goalId == goalId }
            // Сначала ищем привязку именно к СВОЕМУ счёту строки, и только потом — любую другую.
            // У цели может быть несколько привязанных счетов (выбор в форме множественный), и
            // «первая попавшаяся привязка типа ACCOUNT» с приходом на второй счёт давала обратный
            // знак: удаление зачисления УВЕЛИЧИВАЛО бы цель.
            val ownRoute = goalRoutes.firstOrNull {
                it.matchType == TransferMatchType.ACCOUNT && it.matchValue == tx.accountId
            }
            val anyAccountRoute = goalRoutes.firstOrNull { it.matchType == TransferMatchType.ACCOUNT }
            // Mirror exactly what onTransactionInserted applied to the goal:
            //  • own-account leg (route on tx.accountId)  → signed amount (tx.amountKopecks)
            //  • counterparty leg (route on the dest acct) → the opposite sign
            //  • CARD/KEYWORD                              → +magnitude (outgoing only)
            val appliedDelta = when {
                ownRoute != null        -> tx.amountKopecks
                anyAccountRoute != null -> -tx.amountKopecks
                else                    -> magnitude
            }
            goalRepo.contribute(goalId, -appliedDelta)
        }
    }
}
