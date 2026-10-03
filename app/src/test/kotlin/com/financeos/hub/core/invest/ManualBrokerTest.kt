package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ручной ввод в режиме инвестора (инвариант #49): счета, цены, счёт сделки. */
class ManualBrokerTest {

    private val ts = 1_759_300_000_000L

    @Test
    fun `an account added by hand is listed and keeps the screen out of the empty state`() {
        val r = Portfolio.compute(listOf(BrokerAccountMark(BKS, ts, "1230947/21-м-иис", "ИИС")))
        assertFalse(r.isEmpty)
        assertEquals("1230947/21-м-иис (ИИС)", r.contracts.single().title)
    }

    @Test
    fun `a hidden account disappears even if pushes mention it, and comes back when added again`() {
        val push = BrokerCashMove(BKS, ts, "580922/19-м", 1_000_000L, "RUB")
        val hide = BrokerAccountMark(BKS, ts + 1, "580922/19-м", null, hidden = true)
        val hiddenResult = Portfolio.compute(listOf(push, hide))
        assertTrue(hiddenResult.contracts.isEmpty())
        // Деньги по скрытому счёту из итога не пропадают — удаляются только операции.
        assertEquals(1_000_000L, hiddenResult.summaries.single().totalKopecks)
        val back = BrokerAccountMark(BKS, ts + 2, "580922/19-м", "Основной")
        assertEquals("580922/19-м (Основной)", Portfolio.compute(listOf(push, hide, back)).contracts.single().title)
    }

    @Test
    fun `a manual price moves the value of a held security, not its cost`() {
        val events = listOf(
            BrokerCashMove(BKS, ts, null, 1_000_000L, "RUB"),
            BrokerOrder(BKS, ts + 1, "LQDT", OrderSide.BUY, 1000, 2_000_000L, OrderStatus.FILLED),
            BrokerPriceMark(BKS, ts + 2, "LQDT", 2_100_000L),
        )
        val p = Portfolio.compute(events).positions.single()
        assertEquals(210_000L, p.valueKopecks)
        assertEquals(200_000L, p.costKopecks)
        assertEquals(10_000L, p.pnlKopecks)
    }

    @Test
    fun `a price for a security that is not held changes nothing`() {
        val r = Portfolio.compute(listOf(
            BrokerCashMove(BKS, ts, null, 100_000L, "RUB"),
            BrokerPriceMark(BKS, ts + 1, "SBER", 300_000_000L),
        ))
        assertTrue(r.positions.isEmpty())
        assertEquals(100_000L, r.summaries.single().totalKopecks)
    }

    @Test
    fun `an asset added as money plus purchase has zero result and the right account`() {
        // «Добавить актив» = пополнение на стоимость + покупка: деньги на счёте не уходят в минус.
        val events = listOf(
            BrokerCashMove(BKS, ts, "580922/19-м", 998_886L, "RUB", id = "manual_g_0"),
            BrokerOrder(BKS, ts, "LQDT", OrderSide.BUY, 4760, 2_098_500L, OrderStatus.FILLED, contract = "580922/19-м", id = "manual_g_1"),
        )
        val r = Portfolio.compute(events)
        assertEquals(0L, r.accounts.single().cashKopecks)
        assertEquals(998_886L, r.summaries.single().totalKopecks)
        assertEquals(0L, r.periods.getValue(Portfolio.ResultPeriod.ALL).single().pnlKopecks)
        assertTrue(events.all { it.isManual })
    }
}
