package com.financeos.hub.core.edit

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType

/**
 * Правила правки операции задним числом — отдельно от записи, чтобы их можно было проверить.
 *
 * Правка счёта и даты — это не косметика карточки. Операция уже подвинула баланс счёта (или не
 * подвинула, если счёта не было), уже засчитана или не засчитана в цель. Сменить ей счёт значит
 * ответить на два вопроса: что сделать с балансом старого и нового счёта и что — с целью.
 */

/** Сдвиг баланса, которым строка «владеет»: её знаковая сумма на её счёте. */
data class BalanceEffect(val accountId: String, val amountKopecks: Long)

/**
 * Сдвинула ли эта строка баланс своего счёта ДЕЛЬТОЙ — и, значит, может его и откатить.
 *
 * Тот же критерий, что у удаления (инвариант #2): строка с банковским «Остатком» ставила баланс
 * АБСОЛЮТНО, а не сдвигала, и откатывать её нечем; строка из PDF — это выписка задним числом,
 * баланс она не трогала никогда. Строка без счёта ничего не сдвигала.
 */
fun balanceEffectOf(tx: TransactionEntity): BalanceEffect? =
    if (tx.accountId != null && tx.balanceKopecks == null && tx.source != TransactionSource.PDF)
        BalanceEffect(tx.accountId, tx.amountKopecks)
    else null

/**
 * Сколько сдвинуть балансы счетов, когда строка [old] превращается в [new].
 *
 * Откатить старый сдвиг, применить новый — с ОДНИМ исключением, ради которого функция и написана:
 * если у счёта после этой операции уже был банковский «Остаток», баланс заякорен позже неё и сдвиги
 * задним числом его только испортят.
 *
 * Это ровно случай с устройства. Пуш без реквизитов пришёл без счёта, баланс не двигал; потом
 * банк прислал следующий пуш с «Остатком», и баланс встал на его цифру — в которой эти деньги уже
 * учтены. Привяжи сироту к счёту с дельтой — баланс уедет на сумму операции. Без банковского якоря
 * (у Альфы «Остатка» в пушах нет) дельта нужна: иначе привязанная операция не отразилась бы на
 * счёте вовсе, и ручной ввод, которым человек до сих пор обходил эту беду, вёл себя бы иначе.
 *
 * Якорем считается ТОЛЬКО банковский «Остаток». Ручная правка баланса после операции тоже его
 * якорит, но следа во времени не оставляет — `updatedAt` сдвигает каждая дельта, — и отличить её
 * нечем. Это известное ограничение, а не забытое условие.
 *
 * Один и тот же счёт решается ОДНИМ флагом — по времени старой строки: смена даты вместе со сменой
 * знака иначе откатила бы старый сдвиг и не применила новый.
 *
 * @param latestSnapshotAt время последнего банковского «Остатка» по каждому затронутому счёту.
 */
fun balanceAdjustments(
    old             : TransactionEntity,
    new             : TransactionEntity,
    latestSnapshotAt: Map<String, Long>,
): List<BalanceEffect> {
    val was = balanceEffectOf(old)
    val now = balanceEffectOf(new)
    if (was == now) return emptyList()

    fun anchored(accountId: String, timestamp: Long) =
        (latestSnapshotAt[accountId] ?: Long.MIN_VALUE) > timestamp

    if (was != null && now != null && was.accountId == now.accountId) {
        if (anchored(was.accountId, old.timestamp)) return emptyList()
        val delta = now.amountKopecks - was.amountKopecks
        return if (delta == 0L) emptyList() else listOf(BalanceEffect(was.accountId, delta))
    }

    return buildList {
        if (was != null && !anchored(was.accountId, old.timestamp))
            add(BalanceEffect(was.accountId, -was.amountKopecks))
        if (now != null && !anchored(now.accountId, new.timestamp))
            add(BalanceEffect(now.accountId, now.amountKopecks))
    }
}

/**
 * Что сделать с целью, когда строка меняет счёт, сумму или тип.
 *
 * @property reverseOld откатить зачисление, которое сделала старая строка.
 * @property keepGoalId оставить строке прежнюю цель.
 * @property routeNew   провести новую строку через привязку к её СОБСТВЕННОМУ счёту.
 */
data class GoalPlan(
    val reverseOld: Boolean,
    val keepGoalId: Boolean,
    val routeNew  : Boolean,
)

/**
 * Одно правило на все правки, вместо прежнего `applyRetype`, который знал только про тип.
 *
 * «Деньги сдвинулись» — это смена счёта или суммы (у расхода/дохода знак — часть суммы). Только
 * тогда есть что пересчитывать; правка названия или заметки не должна вдруг засчитать старую
 * операцию в цель, привязанную уже после неё (инвариант #31: привязка описывает будущие движения).
 *
 * - Цель по СЧЁТУ следует за деньгами на счёте: сдвинулись деньги — откат и новая привязка по
 *   новому счёту. Ровно это и нужно человеку, привязавшему сироту к накопительному счёту.
 * - Цель по КАРТЕ или СЛОВУ говорит «ПЕРЕВОД туда-то — пополнение»: переживает смену своего счёта,
 *   но не уход из перевода.
 * - Строка без цели получает её только если сдвинулись деньги.
 */
fun goalPlan(
    old             : TransactionEntity,
    new             : TransactionEntity,
    oldAccountRouted: Boolean,
): GoalPlan {
    val moneyMoved   = old.accountId != new.accountId || old.amountKopecks != new.amountKopecks
    val leftTransfer = old.type == TransactionType.TRANSFER && new.type != TransactionType.TRANSFER
    return when {
        old.goalId == null -> GoalPlan(reverseOld = false, keepGoalId = false, routeNew = moneyMoved)
        oldAccountRouted   ->
            if (moneyMoved) GoalPlan(reverseOld = true, keepGoalId = false, routeNew = true)
            else            GoalPlan(reverseOld = false, keepGoalId = true, routeNew = false)
        leftTransfer       -> GoalPlan(reverseOld = true, keepGoalId = false, routeNew = moneyMoved)
        else               -> GoalPlan(reverseOld = false, keepGoalId = true, routeNew = false)
    }
}

/** Знак суммы под новый тип: расход −, доход +, перевод сохраняет своё направление. */
fun resignedAmount(old: TransactionEntity, newType: TransactionType): Long {
    val mag = kotlin.math.abs(old.amountKopecks)
    return when (newType) {
        TransactionType.EXPENSE  -> -mag
        TransactionType.INCOME   ->  mag
        TransactionType.TRANSFER -> old.amountKopecks
    }
}
