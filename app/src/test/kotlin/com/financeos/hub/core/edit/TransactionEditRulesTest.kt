package com.financeos.hub.core.edit

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val new = old.copy(accountId = "alfa")
        assertEquals(
            listOf(BalanceEffect("alfa", -1_500_00L)),
            balanceAdjustments(old, new, latestSnapshotAt = emptyMap()),
        )
    }

    @Test
    fun `a later bank balance already counted the money`() {
        // Банк прислал «Остаток» ПОСЛЕ этой операции — баланс стоит на цифре, в которой эти деньги
        // уже учтены. Дельта поверх уехала бы ровно на сумму операции.
        val old = tx(account = null)
        val new = old.copy(accountId = "sber")
        assertTrue(balanceAdjustments(old, new, mapOf("sber" to t0 + day)).isEmpty())
    }

    @Test
    fun `an earlier bank balance does not anchor`() {
        val old = tx(account = null)
        val new = old.copy(accountId = "sber")
        assertEquals(
            listOf(BalanceEffect("sber", -1_500_00L)),
            balanceAdjustments(old, new, mapOf("sber" to t0 - day)),
        )
    }

    @Test
    fun `moving to another account returns the money and takes it from the new one`() {
        val old = tx(account = "a")
        val new = old.copy(accountId = "b")
        assertEquals(
            listOf(BalanceEffect("a", 1_500_00L), BalanceEffect("b", -1_500_00L)),
            balanceAdjustments(old, new, emptyMap()),
        )
    }

    @Test
    fun `an anchored old account is left alone when moving away`() {
        val old = tx(account = "a")
        val new = old.copy(accountId = "b")
        assertEquals(
            listOf(BalanceEffect("b", -1_500_00L)),
            balanceAdjustments(old, new, mapOf("a" to t0 + day)),
        )
    }

    @Test
    fun `detaching returns the money to the old account`() {
        val old = tx(account = "a")
        val new = old.copy(accountId = null)
        assertEquals(listOf(BalanceEffect("a", 1_500_00L)), balanceAdjustments(old, new, emptyMap()))
    }

    @Test
    fun `a row carrying the bank's balance never moves it by a delta`() {
        // Он ставил баланс абсолютно, а не сдвигал — откатывать нечего, как и при удалении.
        val old = tx(account = null, balance = 45_000_00L)
        val new = old.copy(accountId = "sber")
        assertTrue(balanceAdjustments(old, new, emptyMap()).isEmpty())
    }

    @Test
    fun `a pdf statement row never touches a balance`() {
        val old = tx(account = null, source = TransactionSource.PDF)
        val new = old.copy(accountId = "a")
        assertTrue(balanceAdjustments(old, new, emptyMap()).isEmpty())
    }

    @Test
    fun `fixing a wrong direction on the same account moves it by twice the amount`() {
        // Пуш о приходе, записанный уходом (так было с СБП «от …»): баланс ушёл вниз на 1 500
        // вместо того, чтобы подняться на 1 500. Исправление типа обязано вернуть обе половины.
        val old = tx(account = "a", amount = -1_500_00L)
        val new = old.copy(type = TransactionType.INCOME, amountKopecks = 1_500_00L)
        assertEquals(listOf(BalanceEffect("a", 3_000_00L)), balanceAdjustments(old, new, emptyMap()))
    }

    @Test
    fun `an anchored account ignores a direction fix too`() {
        val old = tx(account = "a", amount = -1_500_00L)
        val new = old.copy(type = TransactionType.INCOME, amountKopecks = 1_500_00L)
        assertTrue(balanceAdjustments(old, new, mapOf("a" to t0 + day)).isEmpty())
    }

    @Test
    fun `changing only the date moves no money`() {
        val old = tx(account = "a")
        val new = old.copy(timestamp = t0 - 3 * day)
        assertTrue(balanceAdjustments(old, new, emptyMap()).isEmpty())
    }

    @Test
    fun `one account is decided once, by the old moment`() {
        // Дата уехала раньше якоря, а знак сменился. Два разных решения по одному счёту откатили
        // бы старый сдвиг и не применили новый — баланс ушёл бы на сумму, которой нигде нет.
        val old = tx(account = "a", amount = -1_500_00L, ts = t0)
        val new = old.copy(
            type = TransactionType.INCOME, amountKopecks = 1_500_00L, timestamp = t0 - 3 * day,
        )
        assertEquals(
            listOf(BalanceEffect("a", 3_000_00L)),
            balanceAdjustments(old, new, mapOf("a" to t0 - day)),
        )
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
