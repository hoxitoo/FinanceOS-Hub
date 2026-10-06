package com.financeos.hub.core.invest

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.parser.InvestmentTransfers
import kotlin.math.abs

/**
 * Склейка двух сторон одного пополнения брокерского счёта (инвариант #53).
 *
 * «Альфа −10 000 → BKS» в кошельке и «Вы пополнили счёт №… на 10 000 RUB» у брокера — одно событие,
 * увиденное с двух концов. Здесь оно склеивается: у пополнения брокера становится известно, ОТКУДА
 * пришли деньги, а перевод кошелька, которому брокер так и не ответил пушем, предлагается записать.
 *
 * Чистая функция, без базы: склейка вычисляется заново из обеих историй и нигде не хранится. Поэтому
 * правка или удаление любой стороны не оставляет за собой осиротевшей ссылки.
 *
 * Правила, и у каждого своя причина:
 * - **Сумма — до копейки, валюта и брокер — те же, направление — встречное.** Ушло из кошелька — пришло
 *   к брокеру (и наоборот при выводе). Приблизительная сумма склеила бы чужие события.
 * - **Окно — [WINDOW_MS]** (трое суток): зачисление у брокера приходит позже списания — в тот же час, а
 *   в выходные и через день.
 * - **Ближайшие по времени склеиваются первыми, каждая сторона — один раз.** Два пополнения по 10 000 за
 *   неделю — две пары, а не одна пара и одно «лишнее».
 * - **Перевод без пары — только ПРЕДЛОЖЕНИЕ**, не запись. Ошибки несимметричны, как у обязательств
 *   (#20): молча дописанное пополнение, о котором брокер всё-таки пришлёт пуш, посчитало бы деньги
 *   дважды. Человек решает сам — «записать» или «это не пополнение».
 * - **Предлагается только то, что портфель уже должен был видеть**: перевод не раньше первого своего
 *   события брокера (до него портфель не вёлся — старые переводы давно потрачены на бумаги, о которых
 *   приложение не знает) и не моложе [GRACE_MS] (пуш брокера ещё может прийти).
 */
object DepositLinks {

    /** Трое суток: зачисление у брокера приходит позже списания, иногда — через выходные. */
    const val WINDOW_MS = 3L * 24 * 3_600_000

    /** Сутки на то, чтобы пуш брокера успел прийти, прежде чем предлагать запись. */
    const val GRACE_MS = 24L * 3_600_000

    /**
     * Перевод кошелька брокеру или от брокера — то, что нужно склейке, без зависимости от базы.
     * [amountKopecks] со знаком кошелька: − ушло к брокеру, + пришло от брокера.
     */
    data class WalletLeg(
        val txId         : String,
        val timestamp    : Long,
        val amountKopecks: Long,
        val currency     : String,
        /** Брокер по имени получателя («БКС»). */
        val broker       : String,
        /** Откуда ушли деньги, как это увидит человек: «Альфа-Банк •• 1139». */
        val source       : String?,
    )

    data class Result(
        /** Пополнение / вывод брокера → перевод кошелька, который его оплатил. */
        val sourceOf  : Map<BrokerCashMove, WalletLeg>,
        /** Переводы кошелька, брокер о которых не написал, — предложения записать пополнение. */
        val unmatched : List<WalletLeg>,
    )

    val EMPTY = Result(emptyMap(), emptyList())

    /**
     * Строка кошелька → сторона склейки, если это перевод брокеру/от брокера с узнанным брокером
     * (#43). Перевод с категорией «Инвестиции», поставленной вручную, без имени брокера в получателе
     * не склеивается: с каким брокером его сравнивать — неизвестно.
     */
    fun legOf(tx: TransactionEntity, accounts: List<AccountEntity>): WalletLeg? {
        if (!InvestmentTransfers.isInvestmentTransfer(tx) || tx.isDeleted) return null
        val broker = InvestmentTransfers.brokerName(tx.merchant) ?: return null
        val account = accounts.firstOrNull { it.id == tx.accountId }
        val mask = tx.sourceMask?.let { "•• $it" }
        val source = when {
            account != null -> listOfNotNull(account.bank, mask ?: account.name).joinToString(" ")
            else            -> mask
        }
        return WalletLeg(tx.id, tx.timestamp, tx.amountKopecks, tx.currency, broker, source)
    }

    fun link(
        legs     : List<WalletLeg>,
        events   : List<BrokerEvent>,
        dismissed: Set<String> = emptySet(),
        now      : Long = System.currentTimeMillis(),
    ): Result {
        val moves = events.filterIsInstance<BrokerCashMove>()
        // Все возможные пары, ближайшие по времени — первыми.
        val candidates = legs.flatMap { leg ->
            moves.filter { m ->
                m.broker == leg.broker && m.currency == leg.currency &&
                    m.amountKopecks == -leg.amountKopecks && leg.amountKopecks != 0L &&
                    abs(m.timestamp - leg.timestamp) <= WINDOW_MS
            }.map { m -> Triple(leg, m, abs(m.timestamp - leg.timestamp)) }
        }.sortedBy { it.third }

        val usedLegs  = mutableSetOf<String>()
        val usedMoves = mutableSetOf<BrokerCashMove>()
        val sourceOf  = linkedMapOf<BrokerCashMove, WalletLeg>()
        for ((leg, move, _) in candidates) {
            if (leg.txId in usedLegs || move in usedMoves) continue
            usedLegs += leg.txId
            usedMoves += move
            sourceOf[move] = leg
        }

        // С какого момента портфель ведётся: первое своё событие брокера, кроме отметок счёта и цены
        // (те ставит человек когда угодно и о движении денег они ничего не говорят).
        val trackedSince = events
            .filter { it is BrokerCashMove || it is BrokerOrder || it is BrokerInternalTransfer }
            .minOfOrNull { it.timestamp }
        val unmatched = if (trackedSince == null) emptyList() else legs.filter { leg ->
            leg.txId !in usedLegs && leg.txId !in dismissed &&
                leg.timestamp >= trackedSince - WINDOW_MS && now - leg.timestamp >= GRACE_MS
        }.sortedByDescending { it.timestamp }

        return Result(sourceOf, unmatched)
    }

    /**
     * Пополнение (или вывод) брокера, которое записывается по переводу кошелька: та же сумма с
     * обратным знаком, тот же момент и брокер. Счёт у брокера — единственный известный, иначе не
     * указан: угаданный счёт хуже пустого.
     */
    fun depositFor(leg: WalletLeg, contracts: List<Portfolio.Contract>): BrokerCashMove =
        BrokerCashMove(
            broker        = leg.broker,
            timestamp     = leg.timestamp,
            contract      = contracts.filter { it.broker == leg.broker }.singleOrNull()?.contract,
            amountKopecks = -leg.amountKopecks,
            currency      = leg.currency,
        )
}
