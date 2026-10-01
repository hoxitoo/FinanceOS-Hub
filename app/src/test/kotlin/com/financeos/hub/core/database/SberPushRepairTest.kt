package com.financeos.hub.core.database

import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Починка истории пушей Сбера (v21→v22): меняется только то, что записал прежний разбор.
 * Тексты — реальные пуши тестера.
 */
class SberPushRepairTest {

    private val ts = 1_759_300_000_000L

    private val dodo = "Шикарный перекус в DODO PIZZA PERM-5 1 034 ₽ — В запасе: 1 121,07 ₽ Счёт карты МИР •• 1238"
    private val sbp  = "Ловкость лапок и оплата по СБП удалась в AVPERM_SBP 760,32 ₽ — В запасе: 6 298,54 ₽ Счёт карты МИР •• 1238"
    private val interest = "Выплата процентов + 77,23 ₽ — Баланс: 10 213,38 ₽ Накопительный счет •• 4958"

    /** Строка ровно такая, какой её записал прежний разбор. */
    private fun asStoredByOldParser(raw: String, balance: Long): SberPushRepair.Stored {
        val old = SberPushRepair.legacy(raw)!!
        return SberPushRepair.Stored(
            type = old.type, amountKopecks = old.signed, merchant = old.merchant, balanceKopecks = balance,
            goalId = null, transferPairId = null, rawText = raw, timestamp = ts,
        )
    }

    @Test
    fun `the old parser really produced the reported garbage`() {
        // Документирует сам дефект: без этого тест ниже проверял бы починку несуществующей ошибки.
        assertEquals(-5_103_400L, SberPushRepair.legacy(dodo)!!.signed)
        assertEquals(TransactionType.TRANSFER, SberPushRepair.legacy(sbp)!!.type)
        assertEquals(TransactionType.EXPENSE, SberPushRepair.legacy(interest)!!.type)
    }

    @Test
    fun `a glued amount is repaired, the merchant cleaned`() {
        val fix = SberPushRepair.plan(asStoredByOldParser(dodo, 1_121_07L))
        assertNotNull(fix)
        assertEquals(-1_034_00L, fix!!.amountKopecks)
        assertEquals("DODO PIZZA PERM-5", fix.merchant)
        assertEquals(TransactionType.EXPENSE, fix.type)
        assertFalse(fix.typeChanged)
    }

    @Test
    fun `an SBP shop payment stored as a transfer becomes an expense`() {
        val fix = SberPushRepair.plan(asStoredByOldParser(sbp, 6_298_54L))!!
        assertEquals(TransactionType.EXPENSE, fix.type)
        assertEquals(-760_32L, fix.amountKopecks)
        assertEquals("AVPERM_SBP", fix.merchant)
        assertTrue(fix.typeChanged)
    }

    @Test
    fun `interest stored as spending becomes income`() {
        val fix = SberPushRepair.plan(asStoredByOldParser(interest, 10_213_38L))!!
        assertEquals(TransactionType.INCOME, fix.type)
        assertEquals(77_23L, fix.amountKopecks)
        assertEquals("Проценты", fix.merchant)
    }

    @Test
    fun `a row the person edited is left alone`() {
        val stored = asStoredByOldParser(dodo, 1_121_07L)
        assertNull(SberPushRepair.plan(stored.copy(amountKopecks = -1_034_00L)))
        assertNull(SberPushRepair.plan(stored.copy(merchant = "Додо")))
        assertNull(SberPushRepair.plan(stored.copy(type = TransactionType.INCOME)))
    }

    @Test
    fun `rows whose amount sits in the balance or in a goal are left alone`() {
        val stored = asStoredByOldParser(dodo, 1_121_07L)
        assertNull("без «Остатка» сумма лежит в балансе", SberPushRepair.plan(stored.copy(balanceKopecks = null)))
        assertNull(SberPushRepair.plan(stored.copy(goalId = "g")))
        assertNull(SberPushRepair.plan(stored.copy(transferPairId = "p")))
        assertNull("другой «Остаток» — текст понят иначе", SberPushRepair.plan(stored.copy(balanceKopecks = 1L)))
    }

    @Test
    fun `an already correct row is not touched`() {
        val dns = "Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703"
        assertNull(SberPushRepair.plan(asStoredByOldParser(dns, 411_301_00L)))
    }

    @Test
    fun `dictionary lookup takes the first match and honours regex rules`() {
        val rules = listOf(
            SberPushRepair.Rule("ozon premium", false, "cat_subscription"),
            SberPushRepair.Rule("ozon", false, "cat_shopping"),
            SberPushRepair.Rule("яндексgo", false, "cat_transport"),
            SberPushRepair.Rule("(?<![a-z0-9])wb(?![a-z0-9])", true, "cat_shopping"),
        )
        assertEquals("cat_subscription", SberPushRepair.categorize("OZON PREMIUM", rules))
        assertEquals("cat_shopping", SberPushRepair.categorize("Ozon", rules))
        assertEquals("cat_transport", SberPushRepair.categorize("ЯндексGo", rules))
        assertEquals("cat_shopping", SberPushRepair.categorize("WB PVZ", rules))
        assertNull(SberPushRepair.categorize("SWBANK", rules))
        assertNull(SberPushRepair.categorize(null, rules))
    }
}
