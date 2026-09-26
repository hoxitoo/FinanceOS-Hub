package com.financeos.hub.core.edit

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правка счёта задним числом. Случай с устройства: пуш без реквизитов пришёл без счёта, сумма
 * есть, а откуда ушли деньги — нет. Раньше такую операцию приходилось удалять и вводить руками.
 */
class TransactionEditRulesTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0  = 1_800_000_000_000L

    private fun tx(
        amount : Long = -1_500_00L,
        account: String? = null,
        type   : TransactionType = TransactionType.EXPENSE,
        source : TransactionSource = TransactionSource.PUSH,
        balance: Long? = null,
        ts     : Long = t0,
        goal   : String? = null,
    ) = TransactionEntity(
        id             = "t1",
        accountId      = account,
        categoryId     = null,
        type           = type,
        source         = source,
        amountKopecks  = amount,
        merchant       = null,
        description    = null,
        timestamp      = ts,
        smsId          = null,
        currency       = "RUB",
        balanceKopecks = balance,
        goalId         = goal,
    )

    // ── Баланс ──────────────────────────────────────────────────────────────────

    @Test
    fun `attaching an orphan push moves the balance by its amount`() {
        // Счёт без банковского якоря (у Альфы в пушах «Остатка» нет): без дельты привязанная
        // операция не отразилась бы на счёте вовсе — не так, как ручной ввод, которым человек
        // до сих пор обходил эту беду.
        val old = tx(account = null)
        val plan = balancePlan(old, old.copy(accountId = "alfa"), anchoredOnNewAccount = false)
        assertEquals(listOf(BalanceEffect("alfa", -1_500_00L)), plan.adjustments)
        assertFalse(plan.detached)
    }

    @Test
    fun `a later bank balance already counted the money`() {
        // Банк прислал «Остаток» ПОСЛЕ этой операции — в его цифре эти деньги уже учтены. Строка
        // привязывается, но помечается: сдвига не было, и удалять потом будет нечего.
        val old = tx(account = null)
        val plan = balancePlan(old, old.copy(accountId = "sber"), anchoredOnNewAccount = true)
        assertTrue(plan.adjustments.isEmpty())
        assertTrue(plan.detached)
    }

    @Test
    fun `a detached row owns nothing to reverse`() {
        // Та же строка, удалённая или отвязанная позже: откат её «суммы» увёл бы баланс ниже
        // банковской цифры на деньги, которых в балансе никогда не было.
        val attached = tx(account = "sber").copy(balanceDetached = true)
        assertNull(balanceEffectOf(attached))
        val plan = balancePlan(attached, attached.copy(accountId = null), anchoredOnNewAccount = false)
        assertTrue(plan.adjustments.isEmpty())
        assertFalse(plan.detached)
    }

    @Test
    fun `a backdated manual entry made after a bank balance still owns its delta`() {
        // Замечание ревью, подтверждённое по коду. Утром пришёл пуш с «Остатком», днём человек ввёл
        // трату ВЧЕРАШНИМ числом — её сумма легла поверх банковской цифры. По датам «Остаток» позже
        // операции, но в балансе она лежит, и удаление обязано её вернуть. Первая версия вычисляла
        // это по датам и не возвращала.
        val manual = tx(account = "sber", source = TransactionSource.MANUAL, ts = t0 - day)
        assertEquals(BalanceEffect("sber", -1_500_00L), balanceEffectOf(manual))
    }

    @Test
    fun `moving to another account returns the money and takes it from the new one`() {
        val old = tx(account = "a")
        val plan = balancePlan(old, old.copy(accountId = "b"), anchoredOnNewAccount = false)
        assertEquals(listOf(BalanceEffect("a", 1_500_00L), BalanceEffect("b", -1_500_00L)), plan.adjustments)
    }

    @Test
    fun `moving onto an anchored account returns the money but takes nothing`() {
        val old = tx(account = "a")
        val plan = balancePlan(old, old.copy(accountId = "b"), anchoredOnNewAccount = true)
        assertEquals(listOf(BalanceEffect("a", 1_500_00L)), plan.adjustments)
        assertTrue(plan.detached)
    }

    @Test
    fun `detaching returns the money to the old account`() {
        val old = tx(account = "a")
        val plan = balancePlan(old, old.copy(accountId = null), anchoredOnNewAccount = false)
        assertEquals(listOf(BalanceEffect("a", 1_500_00L)), plan.adjustments)
    }

    @Test
    fun `a row carrying the bank's balance never moves it by a delta`() {
        // Он ставил баланс абсолютно, а не сдвигал — откатывать нечего, как и при удалении.
        val old = tx(account = null, balance = 45_000_00L)
        val plan = balancePlan(old, old.copy(accountId = "sber"), anchoredOnNewAccount = false)
        assertTrue(plan.adjustments.isEmpty())
        assertFalse(plan.detached)
    }

    @Test
    fun `a pdf statement row never touches a balance`() {
        val old = tx(account = null, source = TransactionSource.PDF)
        assertTrue(balancePlan(old, old.copy(accountId = "a"), anchoredOnNewAccount = false).adjustments.isEmpty())
    }

    @Test
    fun `fixing a wrong direction on the same account moves it by twice the amount`() {
        // Пуш о приходе, записанный уходом (так было с СБП «от …»): баланс ушёл вниз на 1 500
        // вместо того, чтобы подняться на 1 500. Исправление типа обязано вернуть обе половины.
        val old = tx(account = "a", amount = -1_500_00L)
        val new = old.copy(type = TransactionType.INCOME, amountKopecks = 1_500_00L)
        assertEquals(listOf(BalanceEffect("a", 3_000_00L)), balancePlan(old, new, false).adjustments)
    }

    @Test
    fun `a direction fix on a detached row moves nothing and stays detached`() {
        val old = tx(account = "a", amount = -1_500_00L).copy(balanceDetached = true)
        val new = old.copy(type = TransactionType.INCOME, amountKopecks = 1_500_00L)
        val plan = balancePlan(old, new, anchoredOnNewAccount = false)
        assertTrue(plan.adjustments.isEmpty())
        assertTrue(plan.detached)
    }

    @Test
    fun `changing only the date moves no money and keeps the flag`() {
        // Дата не решает, лежит ли сумма в балансе, — это решено при записи и хранится.
        val old = tx(account = "a").copy(balanceDetached = true)
        val plan = balancePlan(old, old.copy(timestamp = t0 - 3 * day), anchoredOnNewAccount = true)
        assertTrue(plan.adjustments.isEmpty())
        assertTrue(plan.detached)
    }

    // ── Цель ────────────────────────────────────────────────────────────────────

    @Test
    fun `attaching to an account lets its goal route the money`() {
        // Сирота, привязанная к накопительному счёту, обязана двинуть цель этого счёта.
        val old = tx(account = null)
        val plan = goalPlan(old, old.copy(accountId = "savings"), oldAccountRouted = false)
        assertEquals(GoalPlan(reverseOld = false, keepGoalId = false, routeNew = true), plan)
    }

    @Test
    fun `flipping the sign of an old row does not fund a goal linked later`() {
        // Замечание ревью: полугодовой расход на счёте, который ПОТОМ привязали к цели, исправлен в
        // доход — и цель вдруг выросла на деньги, которых привязка никогда не касалась.
        val old = tx(account = "savings", amount = -1_500_00L)
        val new = old.copy(type = TransactionType.INCOME, amountKopecks = 1_500_00L)
        assertFalse(goalPlan(old, new, oldAccountRouted = false).routeNew)
    }

    @Test
    fun `editing the name does not fund a goal retroactively`() {
        // Привязка описывает будущие движения (инвариант #31): правка названия старой операции не
        // должна вдруг засчитать её в цель, заведённую уже после неё.
        val old = tx(account = "savings")
        val plan = goalPlan(old, old.copy(merchant = "Пятёрочка"), oldAccountRouted = false)
        assertFalse(plan.routeNew)
    }

    @Test
    fun `an account-routed goal follows the money to the new account`() {
        val old = tx(account = "savings", goal = "g")
        val plan = goalPlan(old, old.copy(accountId = "current"), oldAccountRouted = true)
        assertEquals(GoalPlan(reverseOld = true, keepGoalId = false, routeNew = true), plan)
    }

    @Test
    fun `an account-routed goal stays when no money moved`() {
        val old = tx(account = "savings", goal = "g")
        val plan = goalPlan(old, old.copy(description = "заметка"), oldAccountRouted = true)
        assertEquals(GoalPlan(reverseOld = false, keepGoalId = true, routeNew = false), plan)
    }

    @Test
    fun `a card-routed goal is dropped when the row stops being a transfer`() {
        val old = tx(type = TransactionType.TRANSFER, account = "a", goal = "g")
        val new = old.copy(type = TransactionType.EXPENSE)
        assertEquals(
            GoalPlan(reverseOld = true, keepGoalId = false, routeNew = false),
            goalPlan(old, new, oldAccountRouted = false),
        )
    }

    @Test
    fun `a card-routed goal survives a change of its own account`() {
        // Привязка по карте получателя говорит «перевод туда-то — пополнение»; счёт-источник
        // здесь ни при чём.
        val old = tx(type = TransactionType.TRANSFER, account = "a", goal = "g")
        val plan = goalPlan(old, old.copy(accountId = "b"), oldAccountRouted = false)
        assertTrue(plan.keepGoalId)
        assertFalse(plan.reverseOld)
    }

    // ── Знак ────────────────────────────────────────────────────────────────────

    @Test
    fun `a transfer keeps its direction when retyped into`() {
        assertEquals(1_500_00L, resignedAmount(tx(amount = 1_500_00L, type = TransactionType.INCOME), TransactionType.TRANSFER))
        assertEquals(-1_500_00L, resignedAmount(tx(amount = -1_500_00L), TransactionType.TRANSFER))
        assertEquals(1_500_00L, resignedAmount(tx(amount = -1_500_00L), TransactionType.INCOME))
    }
}
