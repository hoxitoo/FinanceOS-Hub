package com.financeos.hub.core.edit

import com.financeos.hub.core.account.AccountLinker
import com.financeos.hub.core.credit.balanceFromReportedFigure
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
import kotlin.math.abs

/**
 * Правка операции — ОДНА на оба места, откуда её открывают (экран «Операции» и лист на главной).
 *
 * До этого у каждого экрана была своя копия, и копии уже расходились (инвариант #30). Теперь правка
 * умеет больше — менять счёт, дату и вторую ногу перевода, — и у каждой из этих правок есть
 * последствия для баланса и цели. Держать их в двух местах значило бы получить два разных баланса
 * от одной и той же правки.
 *
 * **Накладываются только ИЗМЕНЁННЫЕ поля, поверх СВЕЖЕЙ строки** (инвариант #35). Лист держит
 * снимок, сделанный при открытии; пока он открыт, пуш мог привязать сироту к счёту или спарить
 * перевод. Запиши снимок целиком — и правка одной заметки отвязала бы счёт, откатив сдвиг, которого
 * не было, и разорвала бы пару.
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

    /** Новое значение поля, которое само может быть `null` («без счёта», «без категории»). */
    data class Field<T>(val value: T)

    /**
     * Что человек поменял в карточке. `null` — поле не трогали, и в базе оно остаётся таким, каким
     * его застанет запись, а не таким, каким его показал лист.
     */
    data class Edit(
        val type      : TransactionType?        = null,
        val merchant  : String?                 = null,
        val categoryId: Field<String?>?         = null,
        val note      : Field<String?>?         = null,
        /** Счёт самой строки: откуда ушли деньги у расхода, куда пришли у дохода. */
        val accountId : Field<String?>?         = null,
        val date      : LocalDate?              = null,
        /** Вторая сторона перевода. */
        val counter   : CounterChange?          = null,
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
        val fromMessage = counterpartyFromMessage(tx)
        return if (fromMessage != null) CounterSide(
            accountId    = fromMessage,
            editable     = false,
            lockedReason = "указан в сообщении банка",
        ) else CounterSide(accountId = null, editable = true)
    }

    suspend fun edit(txId: String, e: Edit) {
        val old = txRepo.getById(txId) ?: return
        val now = System.currentTimeMillis()

        val type = e.type ?: old.type
        val oldDate = Instant.ofEpochMilli(old.timestamp).atZone(zone).toLocalDate()
        // Меняется только ДЕНЬ, время суток остаётся своим: порядок операций внутри дня — тоже
        // информация, и перепрыгнуть на полночь строке незачем.
        val newTs = if (e.date == null || e.date == oldDate) old.timestamp
            else e.date.atTime(Instant.ofEpochMilli(old.timestamp).atZone(zone).toLocalTime())
                .atZone(zone).toInstant().toEpochMilli()

        // Обе стороны перевода на одном счёте — не перевод: деньги никуда не ушли. Экран такого
        // выбора не даёт; здесь — страховка. Вторая сторона берётся ПОСЛЕ правки: если её в этой же
        // правке убрали, сравнивать со старой незачем.
        val counterAfter = if (e.counter != null) e.counter.accountId else counterSide(old).accountId
        val wantedAccount = e.accountId?.value
        val accountId = when {
            e.accountId == null                                           -> old.accountId
            type == TransactionType.TRANSFER && wantedAccount != null &&
                wantedAccount == counterAfter                             -> old.accountId
            else                                                          -> wantedAccount
        }

        val leftTransfer = old.type == TransactionType.TRANSFER && type != TransactionType.TRANSFER
        // Непарный перевод из сообщения банка при вставке зачислил деньги второму счёту (инвариант
        // #16 для банковских переводов). Переименованный в расход, он этими деньгами больше не
        // является — их надо снять, иначе второй счёт навсегда остаётся с суммой, которой нет. Тем же
        // путём, что и удаление, и маска после этого забывается: иначе при возврате типа перевода
        // удаление сняло бы эти деньги второй раз.
        val creditedCounter = if (leftTransfer && old.transferPairId == null) counterpartyFromMessage(old) else null
        if (creditedCounter != null) {
            val magnitude = abs(old.amountKopecks)
            accountLinker.adjustBalance(creditedCounter, if (old.amountKopecks < 0) -magnitude else magnitude)
        }

        val candidate = old.copy(
            type             = type,
            amountKopecks    = if (e.type == null) old.amountKopecks else resignedAmount(old, type),
            merchant         = if (e.merchant == null) old.merchant else e.merchant.ifBlank { null },
            categoryId       = if (e.categoryId != null) e.categoryId.value else old.categoryId,
            description      = if (e.note != null) e.note.value else old.description,
            accountId        = accountId,
            timestamp        = newTs,
            transferPairId   = if (leftTransfer) null else old.transferPairId,
            counterpartyMask = if (creditedCounter != null) null else old.counterpartyMask,
            updatedAt        = now,
        )
        val written = applyRow(old, candidate)

        // Перевод — одно событие в двух строках: дата у них одна. Но двигается только нога, которую
        // записало приложение: у банковской строки дата — факт банка, и её «Остаток» — якорь для
        // всех остальных операций счёта. Сдвинь её — и якорь уедет вместе с ней.
        val shift = newTs - old.timestamp
        if (shift != 0L && written.transferPairId != null) {
            txRepo.transferPair(written.transferPairId)
                .filter { it.id != written.id && it.source == TransactionSource.MANUAL }
                .forEach { leg ->
                    txRepo.update(leg.copy(timestamp = leg.timestamp + shift, updatedAt = now))
                }
        }

        if (e.counter != null && written.type == TransactionType.TRANSFER) {
            applyCounterChange(old, written, e.counter.accountId)
        }
    }

    /**
     * Сколько вернуть счёту при удалении строки: её сдвиг наоборот — если сдвиг был.
     *
     * Никаких выводов по датам: лежит ли сумма в балансе, записано в самой строке. Первая версия
     * вычисляла это по «Остатку» позже операции, и удаление ручной операции вчерашним числом,
     * введённой после утреннего пуша с остатком, перестало возвращать деньги.
     */
    fun reversalFor(tx: TransactionEntity): BalanceEffect? =
        balanceEffectOf(tx)?.let { BalanceEffect(it.accountId, -it.amountKopecks) }

    // ── Внутреннее ───────────────────────────────────────────────────────────────

    /** Записывает [candidate] поверх [old] со всеми последствиями и возвращает то, что легло в базу. */
    private suspend fun applyRow(old: TransactionEntity, candidate: TransactionEntity): TransactionEntity {
        val accountRouted = old.goalId != null && transferRouter.isAccountRouted(old)
        val goals = goalPlan(old, candidate, accountRouted)

        val newAccount = candidate.accountId
        val anchored = newAccount != null && newAccount != old.accountId &&
            anchoredAfter(newAccount, candidate.timestamp)
        val balance = balancePlan(old, candidate, anchored)

        if (goals.reverseOld) transferRouter.onTransactionReversed(old)
        balance.adjustments.forEach { accountLinker.adjustBalance(it.accountId, it.amountKopecks) }

        val row = candidate.copy(
            goalId          = if (goals.keepGoalId) old.goalId else null,
            balanceDetached = balance.detached,
        )
        txRepo.update(row)
        if (goals.routeNew) transferRouter.onRowReassigned(row)

        // Строка с банковским «Остатком», привязанная к новому счёту, — это его снимок. Применяется
        // тем же путём, что и при усыновлении сирот: только если он свежее последней правки счёта.
        if (row.balanceKopecks != null && newAccount != null && newAccount != old.accountId) {
            accountLinker.snapToAuthoritativeIfNewer(newAccount)
        }
        // Маршрутизатор мог записать цель прямо в базу — дальше работаем со свежей строкой.
        return txRepo.getById(row.id) ?: row
    }

    /**
     * Был ли у счёта банковский «Остаток» ПОСЛЕ момента [timestamp] — то есть знал ли банк об этих
     * деньгах, когда называл свою цифру.
     *
     * Считается только «Остаток», который приложение смогло применить. На кредитке с неизвестным
     * лимитом цифра банка — это свободный лимит, и без лимита её не в чем перевести (инвариант #12):
     * баланс тогда шёл дельтами, и такой «Остаток» ничего не заякорил.
     */
    private suspend fun anchoredAfter(accountId: String, timestamp: Long): Boolean {
        val snap = transactionDao.latestBalanceSnapshotForAccount(accountId) ?: return false
        if (snap.timestamp <= timestamp) return false
        val account = accountRepo.getById(accountId) ?: return false
        return balanceFromReportedFigure(account, snap.balanceKopecks) != null
    }

    private suspend fun applyCounterChange(old: TransactionEntity, row: TransactionEntity, target: String?) {
        val side = counterSide(old)
        if (!side.editable || target == side.accountId) return
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
        val anchored = anchoredAfter(target.id, row.timestamp)
        val leg = TransactionEntity(
            id              = UUID.randomUUID().toString(),
            smsId           = null,
            accountId       = target.id,
            categoryId      = null,   // перевод не трата, категории у него нет
            type            = TransactionType.TRANSFER,
            source          = TransactionSource.MANUAL,
            amountKopecks   = -row.amountKopecks,
            merchant        = row.merchant ?: "Перевод",
            description     = null,
            timestamp       = row.timestamp,
            currency        = row.currency,
            transferPairId  = pairId,
            balanceDetached = anchored,
        )
        txRepo.insert(leg)
        if (!anchored) accountLinker.adjustBalance(target.id, leg.amountKopecks)

        // Одно событие пополняет цель один раз. Перевод, уже засчитанный в цель ПО КАРТЕ ПОЛУЧАТЕЛЯ,
        // — это и есть зачисление в эту сторону, и второй раз через счёт новой ноги он не идёт. А вот
        // цель, привязанная к счёту-ИСТОЧНИКУ, — другая сторона того же события, и приёмник обязан
        // получить своё, как при ручном переводе.
        val fundedByCard = row.goalId != null && !transferRouter.isAccountRouted(row)
        if (!fundedByCard) transferRouter.onRowReassigned(leg)

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

    /** Второй счёт непарного перевода, названный в сообщении банка. */
    private suspend fun counterpartyFromMessage(tx: TransactionEntity): String? =
        if (tx.type != TransactionType.TRANSFER || tx.transferPairId != null) null
        else tx.counterpartyMask?.let { accountLinker.resolveAccountId(it) }?.takeIf { it != tx.accountId }
}
