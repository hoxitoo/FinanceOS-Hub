package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import com.financeos.hub.core.invest.ManualEntry.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryTest {

    private val ts = 1_759_300_000_000L

    @Test
    fun `cash operations carry the sign of their direction`() {
        val dep = ManualEntry.operation(Kind.DEPOSIT, BKS, ts, amountKopecks = 500_00L, contract = "580922/19-м")!!.single()
        assertEquals(500_00L, (dep as BrokerCashMove).amountKopecks)
        val out = ManualEntry.operation(Kind.WITHDRAW, BKS, ts, amountKopecks = 500_00L)!!.single() as BrokerCashMove
        assertEquals(-500_00L, out.amountKopecks)
    }

    @Test
    fun `a transfer needs two different accounts`() {
        assertNull(ManualEntry.operation(Kind.TRANSFER, BKS, ts, amountKopecks = 189_00L, contract = "1", toContract = "1"))
        assertNull(ManualEntry.operation(Kind.TRANSFER, BKS, ts, amountKopecks = 189_00L, contract = "1"))
        val t = ManualEntry.operation(Kind.TRANSFER, BKS, ts, amountKopecks = 189_00L, contract = "580922/19-м", toContract = "3468071/25")!!
        assertTrue(t.single() is BrokerInternalTransfer)
    }

    @Test
    fun `a trade is a filled order in pieces, with the chosen account`() {
        val o = ManualEntry.operation(Kind.SELL, BKS, ts, ticker = " sber ", quantity = 10, priceMicros = 300_000_000L, contract = "580922/19-м")!!
            .single() as BrokerOrder
        assertEquals("SBER", o.ticker)
        assertEquals(OrderSide.SELL, o.side)
        assertEquals(OrderStatus.FILLED, o.status)
        assertEquals("580922/19-м", o.contract)
        assertNull(ManualEntry.operation(Kind.BUY, BKS, ts, ticker = "", quantity = 1, priceMicros = 1))
        assertNull(ManualEntry.operation(Kind.BUY, BKS, ts, ticker = "SBER", quantity = 0, priceMicros = 1))
    }

    @Test
    fun `an asset bought earlier brings its own money`() {
        val events = ManualEntry.asset(BKS, ts, "LQDT", 4760, 2_098_500L, "580922/19-м")!!
        assertEquals(998_886L, (events[0] as BrokerCashMove).amountKopecks)
        assertTrue(events[1] is BrokerOrder)
    }

    @Test
    fun `price input keeps exchange precision`() {
        assertEquals("2,0985", ManualEntry.sanitizePrice("2.0985"))
        assertEquals("2,123456", ManualEntry.sanitizePrice("2,1234567"))
        assertEquals("12,5", ManualEntry.sanitizePrice("1a2,,5"))
        assertEquals(2_098_500L, ManualEntry.parsePrice("2,0985"))
        assertNull(ManualEntry.parsePrice(""))
    }
}
