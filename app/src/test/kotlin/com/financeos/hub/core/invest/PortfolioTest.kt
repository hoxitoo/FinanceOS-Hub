package com.financeos.hub.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Портфель из событий брокера — на реальной последовательности пушей БКС от 1 октября. */
class PortfolioTest {

    private fun at(min: Int) = 1_759_300_000_000L + min * 60_000L

    private fun order(status: OrderStatus, price: Long, min: Int, side: OrderSide = OrderSide.BUY, lots: Long = 4760) =
        BrokerOrder(BrokerPushParser.BKS, at(min), "LQDT", side, lots, price, status)

    /** 09:53 пополнение, 09:56 заявка, 10:04 отмена, 10:04 исполнение по новой цене. */
    private val realDay = listOf(
        BrokerCashMove(BrokerPushParser.BKS, at(0), "580922/19-м", 1_000_000L, "RUB"),
        order(OrderStatus.ACTIVE,    2_098_400L, 3),
        order(OrderStatus.CANCELLED, 2_098_400L, 11),
        order(OrderStatus.FILLED,    2_098_500L, 12),
    )

    @Test
    fun `the real day ends with one position and the change on the account`() {
        val r = Portfolio.compute(realDay)
        val p = r.positions.single()
        assertEquals("LQDT", p.ticker)
        assertEquals(4760L, p.quantity)
        assertEquals(998_886L, p.costKopecks)          // 4760 × 2.0985 = 9 988,86 ₽
        assertEquals(998_886L, p.valueKopecks)         // цена последней своей сделки
        val acc = r.accounts.single()
        assertEquals(1_114L, acc.cashKopecks)          // 10 000 − 9 988,86 = 11,14 ₽
        assertEquals("580922/19-м", acc.contract)
        val s = r.summaries.single()
        assertEquals(1_000_000L, s.totalKopecks)       // всего на счёте — ровно то, что завели
        assertEquals(0L, s.pnlKopecks)
    }

    @Test
    fun `only a filled order moves money, and the order shows its last status`() {
        val r = Portfolio.compute(realDay)
        assertTrue("исполненная заявка не висит активной", r.activeOrders.isEmpty())
        assertEquals(2, r.history.size)                 // отменённая и исполненная
        assertEquals(OrderStatus.FILLED, r.history.first().status)

        val pending = Portfolio.compute(realDay.take(2))
        assertEquals(1, pending.activeOrders.size)
        assertTrue(pending.positions.isEmpty())
        assertEquals(1_000_000L, pending.accounts.single().cashKopecks)
    }

    @Test
    fun `average price on buys, realised on sell, last price drives the value`() {
        val r = Portfolio.compute(listOf(
            order(OrderStatus.FILLED, 2_000_000L, 1, lots = 100),
            order(OrderStatus.FILLED, 3_000_000L, 2, lots = 100),
            order(OrderStatus.FILLED, 4_000_000L, 3, side = OrderSide.SELL, lots = 50),
        ))
        val p = r.positions.single()
        assertEquals(150L, p.quantity)
        assertEquals(2_500_000L, p.avgPriceMicros)
        assertEquals(37_500L, p.costKopecks)            // 150 × 2,50
        assertEquals(60_000L, p.valueKopecks)           // 150 × 4,00 — цена последней сделки
        assertEquals(22_500L, p.pnlKopecks)
    }

    @Test
    fun `lot size multiplies the quantity`() {
        val r = Portfolio.compute(listOf(
            BrokerOrder(BrokerPushParser.BKS, at(1), "SBER", OrderSide.BUY, 2, 300_000_000L, OrderStatus.FILLED)
        ), lotSizes = mapOf("SBER" to 10L))
        assertEquals(20L, r.positions.single().quantity)
        assertEquals(600_000L, r.positions.single().costKopecks)   // 20 × 300 ₽
    }

    @Test
    fun `a sold-out position disappears`() {
        val r = Portfolio.compute(listOf(
            order(OrderStatus.FILLED, 2_000_000L, 1, lots = 10),
            order(OrderStatus.FILLED, 2_100_000L, 2, side = OrderSide.SELL, lots = 10),
        ))
        assertTrue(r.positions.isEmpty())
    }

    @Test
    fun `nothing in, nothing out`() {
        assertTrue(Portfolio.compute(emptyList()).isEmpty)
    }

    @Test
    fun `a market buy without a price is counted at the last known price and marked (#52)`() {
        val market = order(OrderStatus.FILLED, BrokerOrder.UNKNOWN_PRICE, 2000, lots = 20)
        val p = Portfolio.compute(realDay + market, now = at(2001))
        val lqdt = p.positions.single()
        assertEquals(4780L, lqdt.quantity)
        assertEquals(2_098_500L, p.estimates[market])
        assertTrue(p.unpriced.isEmpty())
        // Деньги ушли по той же цене: 20 × 2,0985 = 41,97 ₽.
        val cashBefore = Portfolio.compute(realDay, now = at(2001)).accounts.single().cashKopecks
        assertEquals(cashBefore - 41_97L, p.accounts.single().cashKopecks)
    }

    @Test
    fun `a market buy of a security with no known price stays out of the portfolio`() {
        val market = BrokerOrder(BrokerPushParser.BKS, at(5), "SBER", OrderSide.BUY, 1, BrokerOrder.UNKNOWN_PRICE, OrderStatus.FILLED)
        val p = Portfolio.compute(listOf(market), now = at(6))
        assertTrue(p.positions.isEmpty())
        assertEquals(listOf(market), p.unpriced)
        // В ленте сделок она есть — её видно и можно поправить.
        assertEquals(listOf(market), p.history)
    }
}
