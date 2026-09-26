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
 * Лежит ли сумма этой строки в балансе её счёта ДЕЛЬТОЙ — и, значит, её можно и нужно откатить.
 *
 * Тот же критерий, что у удаления (инвариант #2), плюс хранимый флаг правки: строка с банковским
 * «Остатком» ставила баланс АБСОЛЮТНО; строка из PDF — выписка задним числом, баланс не трогала;
 * строка без счёта ничего не сдвигала; [TransactionEntity.balanceDetached] — привязана к счёту
 * правкой, но банк уже учёл эти деньги, и сдвига не было.
 */
fun balanceEffectOf(tx: TransactionEntity): BalanceEffect? =
    if (tx.accountId != null && canOwnDelta(tx) && !tx.balanceDetached)
        BalanceEffect(tx.accountId, tx.amountKopecks)
    else null

/** Может ли строка вообще двигать баланс дельтой — у неё нет банковского «Остатка» и она не из PDF. */
fun canOwnDelta(tx: TransactionEntity): Boolean =
    tx.balanceKopecks == null && tx.source != TransactionSource.PDF

/**
 * Что сделать с балансами, когда строка [old] превращается в [new], и как пометить [new].
 *
 * @property adjustments сдвиги балансов по счетам.
 * @property detached    значение [TransactionEntity.balanceDetached] у новой строки.
 */
data class BalancePlan(val adjustments: List<BalanceEffect>, val detached: Boolean)

/**
 * Правка баланса при смене счёта, суммы или знака.
 *
 * **Откат старого сдвига — по факту, а не по датам.** Лежит ли сумма строки в балансе, записано в
 * самой строке ([balanceEffectOf]); вычислять это по времени событий нельзя. Ручная операция
 * вчерашним числом, введённая после утреннего пуша с «Остатком», в балансе лежит — хотя «Остаток»
 * по времени позже неё. Первая версия правки вычисляла, и удаление такой операции перестало
 * возвращать деньги.
 *
 * **Новый сдвиг — с одним исключением.** Строка получает НОВЫЙ счёт, а у того после операции был
 * банковский «Остаток» — [anchoredOnNewAccount]. Тогда в цифре банка эти деньги уже учтены, дельта
 * поверх уехала бы на сумму операции, и строка помечается [BalancePlan.detached]: привязана, но
 * баланс не трогала — и при удалении откатывать будет нечего. Здесь время событий как раз законно:
 * вопрос не «когда мы это применили», а «знал ли банк об этих деньгах, когда называл остаток».
 *
 * Это ровно случай с устройства: пуш без реквизитов пришёл без счёта, позже банк прислал «Остаток».
 * У Альфы «Остатка» в пушах нет, и там сдвиг нужен — иначе привязанная операция не отразилась бы на
 * счёте вовсе.
 *
 * Тот же счёт — флаг не меняется, сдвигается только разница сумм: расход −1 500 → доход +1 500 — это
 * +3 000, исправление обратного знака. Только дата — ничего.
 */
fun balancePlan(
    old                 : TransactionEntity,
    new                 : TransactionEntity,
    anchoredOnNewAccount: Boolean,
): BalancePlan {
    val was = balanceEffectOf(old)

    if (old.accountId == new.accountId) {
        val adjustments = if (was != null && new.amountKopecks != old.amountKopecks)
            listOf(BalanceEffect(was.accountId, new.amountKopecks - old.amountKopecks))
        else emptyList()
        return BalancePlan(adjustments, detached = old.balanceDetached)
    }

    val reverse = listOfNotNull(was?.let { BalanceEffect(it.accountId, -it.amountKopecks) })
    val target  = new.accountId
    return when {
        target == null || !canOwnDelta(new) -> BalancePlan(reverse, detached = false)
        anchoredOnNewAccount                -> BalancePlan(reverse, detached = true)
        else -> BalancePlan(reverse + BalanceEffect(target, new.amountKopecks), detached = false)
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
 * - Строка без цели получает её только при смене счёта — и только от привязки, существовавшей на
 *   момент операции (это проверяет `TransferRouter.onRowReassigned`).
 */
fun goalPlan(
    old             : TransactionEntity,
    new             : TransactionEntity,
    oldAccountRouted: Boolean,
): GoalPlan {
    val moneyMoved   = old.accountId != new.accountId || old.amountKopecks != new.amountKopecks
    val leftTransfer = old.type == TransactionType.TRANSFER && new.type != TransactionType.TRANSFER
    return when {
        // Строка без цели получает её только при смене СЧЁТА. Смена одного знака на прежнем счёте
        // её не заводит: если цель не взяла операцию при появлении, значит привязки тогда не было.
        old.goalId == null -> GoalPlan(
            reverseOld = false, keepGoalId = false, routeNew = old.accountId != new.accountId,
        )
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
