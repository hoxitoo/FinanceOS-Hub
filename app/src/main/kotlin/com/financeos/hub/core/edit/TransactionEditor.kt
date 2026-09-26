package com.financeos.hub.core.edit

import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.database.daos.TransactionDao
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.transfer.TransferRouter
import com.financeos.hub.data.repositories.AccountRepository
import com.financeos.hub.data.repositories.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Правка операции — ОДНА на оба места, откуда её открывают (экран «Операции» и лист на главной).
 *
 * До этого у каждого экрана была своя копия, и копии уже расходились (инвариант #30). Теперь правка
 * умеет больше — менять счёт, дату и вторую ногу перевода, — и у каждой из этих правок есть
 * последствия для баланса и цели. Держать их в двух местах значило бы получить два разных баланса
 * от одной и той же правки.
 *
 * Строка перед записью ПЕРЕЧИТЫВАЕТСЯ (инвариант #35): лист держит снимок, сделанный при открытии,
 * и запись поверх снимка стёрла бы цель, привязанную пушем, пока лист был открыт.
 */
@Singleton
class TransactionEditor @Inject constructor(
    private val txRepo        : TransactionRepository,
    private val accountRepo   : AccountRepository,
    private val transactionDao: TransactionDao,
    private val accountLinker : AccountLinker,
    private val transferRouter: TransferRouter,
) {
    private val zone = ZoneId.systemDefault()

    /** Что человек поменял в карточке. */
    data class Edit(
        val type      : TransactionType,
        val merchant  : String,
        val categoryId: String?,
        val note      : String?,
        /** Счёт самой строки: откуда ушли деньги у расхода, куда пришли у дохода. `null` — без счёта. */
        val accountId : String?,
        val date      : LocalDate,
        /** Вторая сторона перевода. `null` — не трогать. */
        val counter   : CounterChange? = null,
    )

    /** Новая вторая сторона перевода; `accountId == null` — убрать её. */
    data class CounterChange(val accountId: String?)

    /**
     * Вторая сторона перевода, как её видит карточка.
     *
     * Править её можно не всегда, и это не каприз интерфейса. Если вторая сторона пришла из
     * сообщения банка («на счёт *3583»), её сдвиг баланса делался по условиям, которые задним числом
     * не восстановить (был ли уже встречный пуш в ту минуту), и откатить его честно нечем. Если это
     * отдельная банковская строка — у неё своя карточка, где её счёт и правится. Свободна для правки
     * только сторона, которой нет, или та, что приложение записало само.
     */
    data class CounterSide(
        val accountId: String?,
        val editable : Boolean,
        /** Почему править нельзя; показывается под полем. */
        val lockedReason: String? = null,
    )

    suspend fun counterSide(tx: TransactionEntity): CounterSide {
        val leg = pairLeg(tx)
        if (leg != null) {
            return if (leg.source == TransactionSource.MANUAL) CounterSide(leg.accountId, editable = true)
            else CounterSide(
                accountId    = leg.accountId,
                editable     = false,
                lockedReason = "это отдельная операция банка — её счёт правится в её карточке",
            )
        }
        val fromMessage = tx.counterpartyMask
            ?.let { accountLinker.resolveAccountId(it) }
            ?.takeIf { it != tx.accountId }
        return if (fromMessage != null) CounterSide(
            accountId    = fromMessage,
            editable     = false,
            lockedReason = "указан в сообщении банка",
        ) else CounterSide(accountId = null, editable = true)
    }

    suspend fun edit(txId: String, e: Edit) {
        val old = txRepo.getById(txId) ?: return
        val now = System.currentTimeMillis()

        val oldDate = Instant.ofEpochMilli(old.timestamp).atZone(zone).toLocalDate()
        // Меняется только ДЕНЬ, время суток остаётся своим: порядок операций внутри дня — тоже
        // информация, и перепрыгнуть на полночь строке незачем.
        val newTs = if (e.date == oldDate) old.timestamp
            else e.date.atTime(Instant.ofEpochMilli(old.timestamp).atZone(zone).toLocalTime())
                .atZone(zone).toInstant().toEpochMilli()

        val leftTransfer = old.type == TransactionType.TRANSFER && e.type != TransactionType.TRANSFER
        // Обе стороны перевода на одном счёте — не перевод: деньги никуда не ушли, а балансы двух
        // строк сократились бы в ноль. Экран такого выбора не даёт; здесь — страховка.
        val sameAsCounter = e.type == TransactionType.TRANSFER && e.accountId != null &&
            e.accountId == (e.counter?.accountId ?: counterSide(old).accountId)
        val candidate = old.copy(
            type           = e.type,
            amountKopecks  = resignedAmount(old, e.type),
            merchant       = e.merchant.ifBlank { null },
            categoryId     = e.categoryId,
            description    = e.note,
            accountId      = if (sameAsCounter) old.accountId else e.accountId,
            timestamp      = newTs,
            transferPairId = if (leftTransfer) null else old.transferPairId,
            updatedAt      = now,
        )
        val written = applyRow(old, candidate)

        // Перевод — одно событие в двух строках: дата у них одна.
        val shift = newTs - old.timestamp
        if (shift != 0L && written.transferPairId != null) {
            txRepo.transferPair(written.transferPairId)
                .filter { it.id != written.id }
                .forEach { leg ->
                    txRepo.update(leg.copy(timestamp = leg.timestamp + shift, updatedAt = now))
                }
        }

        val change = e.counter
        if (change != null && written.type == TransactionType.TRANSFER) {
            applyCounterChange(old, written, change.accountId)
        }
    }

    /**
     * Удаление: откат баланса своего счёта и второй ноги — по тому же правилу якоря, что и правка.
     *
     * Без общего правила «отвязать счёт» и «удалить операцию» давали бы на одном и том же счёте
     * разные балансы: правка знала бы, что банковский «Остаток» позже уже учёл эти деньги, а
     * удаление — нет, и откатывало бы их поверх банковской цифры.
     *
     * @return сколько откатить с каждого счёта для строки [tx]; пусто, если откатывать нечего.
     */
    suspend fun reversalFor(tx: TransactionEntity): BalanceEffect? {
        val eff = balanceEffectOf(tx) ?: return null
        val anchor = transactionDao.latestBalanceSnapshotForAccount(eff.accountId)?.timestamp
        return if (anchor != null && anchor > tx.timestamp) null
        else BalanceEffect(eff.accountId, -eff.amountKopecks)
    }

    // ── Внутреннее ───────────────────────────────────────────────────────────────

    /** Записывает [candidate] поверх [old] со всеми последствиями и возвращает то, что легло в базу. */
    private suspend fun applyRow(old: TransactionEntity, candidate: TransactionEntity): TransactionEntity {
        val accountRouted = old.goalId != null && transferRouter.isAccountRouted(old)
        val plan = goalPlan(old, candidate, accountRouted)

        // Якоря считаются ДО записи: после неё строка с «Остатком» сама стала бы якорем своему счёту.
        val touched = listOfNotNull(old.accountId, candidate.accountId).distinct()
        val snapshots = touched.mapNotNull { id ->
            transactionDao.latestBalanceSnapshotForAccount(id)?.let { id to it.timestamp }
        }.toMap()

        if (plan.reverseOld) transferRouter.onTransactionReversed(old)
        balanceAdjustments(old, candidate, snapshots).forEach {
            accountLinker.adjustBalance(it.accountId, it.amountKopecks)
        }

        val row = candidate.copy(goalId = if (plan.keepGoalId) old.goalId else null)
        txRepo.update(row)
        if (plan.routeNew) transferRouter.onManualRowInserted(row)

        // Строка с банковским «Остатком», привязанная к новому счёту, — это его снимок. Применяется
        // тем же путём, что и при усыновлении сирот: только если он свежее последней правки счёта.
        if (row.balanceKopecks != null && row.accountId != null && row.accountId != old.accountId) {
            accountLinker.snapToAuthoritativeIfNewer(row.accountId)
        }
        // Маршрутизатор мог записать цель прямо в базу — дальше работаем со свежей строкой.
        return txRepo.getById(row.id) ?: row
    }

    private suspend fun applyCounterChange(old: TransactionEntity, row: TransactionEntity, target: String?) {
        val side = counterSide(old)
        if (!side.editable || target == side.accountId) return
        // Перевод на тот же счёт — не перевод: деньги никуда не ушли.
        if (target != null && target == row.accountId) return

        val leg = pairLeg(row)
        when {
            leg == null && target != null -> createLeg(row, target)
            leg != null && target == null -> removeLeg(row, leg)
            leg != null && target != null -> applyRow(leg, leg.copy(accountId = target, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Вторая нога перевода — своя строка на счёте-приёмнике, как у ручного перевода (инвариант #16):
     * откатить можно только тот счёт, на котором строка лежит.
     */
    private suspend fun createLeg(row: TransactionEntity, targetAccountId: String) {
        val target = accountRepo.getById(targetAccountId) ?: return
        val pairId = row.transferPairId ?: UUID.randomUUID().toString()
        val leg = TransactionEntity(
            id             = UUID.randomUUID().toString(),
            smsId          = null,
            accountId      = target.id,
            categoryId     = null,   // перевод не трата, категории у него нет
            type           = TransactionType.TRANSFER,
            source         = TransactionSource.MANUAL,
            amountKopecks  = -row.amountKopecks,
            merchant       = row.merchant ?: "Перевод",
            description    = null,
            timestamp      = row.timestamp,
            currency       = row.currency,
            transferPairId = pairId,
        )
        txRepo.insert(leg)
        val anchor = transactionDao.latestBalanceSnapshotForAccount(target.id)?.timestamp
        if (anchor == null || anchor <= leg.timestamp) {
            accountLinker.adjustBalance(target.id, leg.amountKopecks)
        }
        // Одно событие пополняет цель один раз. Перевод, уже засчитанный в цель по карте получателя,
        // не должен засчитаться второй раз через привязку к счёту новой ноги.
        if (row.goalId == null) transferRouter.onManualRowInserted(leg)

        txRepo.getById(row.id)?.let { fresh ->
            txRepo.update(fresh.copy(transferPairId = pairId, updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun removeLeg(row: TransactionEntity, leg: TransactionEntity) {
        if (leg.goalId != null) transferRouter.onTransactionReversed(leg)
        reversalFor(leg)?.let { accountLinker.adjustBalance(it.accountId, it.amountKopecks) }
        txRepo.softDelete(leg.id)
        txRepo.getById(row.id)?.let { fresh ->
            txRepo.update(fresh.copy(transferPairId = null, updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun pairLeg(tx: TransactionEntity): TransactionEntity? =
        tx.transferPairId?.let { pairId -> txRepo.transferPair(pairId).firstOrNull { it.id != tx.id } }
}
