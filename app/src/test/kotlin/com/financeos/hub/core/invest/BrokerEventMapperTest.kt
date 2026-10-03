package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import org.junit.Assert.assertEquals
import org.junit.Test

/** Каждое поле события доезжает до строки базы и обратно — иначе история портфеля врала бы молча. */
class BrokerEventMapperTest {

    private val ts = 1_759_300_000_000L

    private fun roundTrip(e: BrokerEvent, id: String = "x") =
        BrokerEventMapper.toDomain(BrokerEventMapper.toEntity(e, id, "raw", now = ts))

    @Test
    fun `every kind of event survives the database`() {
        val cash = BrokerCashMove(BKS, ts, "580922/19-м", 1_000_000L, "RUB")
        val transfer = BrokerInternalTransfer(BKS, ts, 18_900L, "RUB", "580922/19-м", "3468071/25", null, "Облигации")
        val order = BrokerOrder(BKS, ts, "LQDT", OrderSide.BUY, 4760, 2_098_500L, OrderStatus.FILLED, "Лимитная")
        // Строка возвращается со своим id — по нему её и удаляют.
        assertEquals(cash.copy(id = "x"), roundTrip(cash))
        assertEquals(transfer.copy(id = "x"), roundTrip(transfer))
        assertEquals(order.copy(id = "x"), roundTrip(order))
    }

    @Test
    fun `manual accounts, prices and the account of a trade survive the database`() {
        val acc = BrokerAccountMark(BKS, ts, "3468071/25", "Облигации", hidden = true)
        val price = BrokerPriceMark(BKS, ts, "LQDT", 2_100_900L)
        val order = BrokerOrder(BKS, ts, "SBER", OrderSide.SELL, 10, 300_000_000L, OrderStatus.FILLED, contract = "580922/19-м")
        assertEquals(acc.copy(id = "x"), roundTrip(acc))
        assertEquals(price.copy(id = "x"), roundTrip(price))
        assertEquals(order.copy(id = "x"), roundTrip(order))
    }

    @Test
    fun `an alert comes back with its id, so it can be dismissed`() {
        val alert = BrokerMarginAlert(BKS, ts, "3468071/25", "Облигации", 18_802L)
        assertEquals(alert.copy(id = "row1"), roundTrip(alert, "row1"))
    }

    @Test
    fun `an unknown kind is skipped, not a crash`() {
        val row = BrokerEventMapper.toEntity(BrokerCashMove(BKS, ts, null, 1L, "RUB"), "x", "raw").copy(kind = "COUPON")
        assertEquals(null, BrokerEventMapper.toDomain(row))
    }
}
