package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import com.financeos.hub.core.invest.Portfolio.ResultPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

/** Результат за 24 часа / месяц / всё время: изменение всего у брокера минус заведённые деньги. */
class PeriodResultTest {

    private val day = 24 * 3_600_000L
    private val now = 1_790_000_000_000L
    private val utc = ZoneOffset.UTC

    private fun buy(at: Long, price: Long) =
        BrokerOrder(BKS, at, "LQDT", OrderSide.BUY, 1000, price, OrderStatus.FILLED)

    private fun result(events: List<BrokerEvent>, p: ResultPeriod) =
        Portfolio.compute(events, now = now, zone = utc).periods.getValue(p).single()

    @Test
    fun `a deposit is not a profit in any period`() {
        val events = listOf(BrokerCashMove(BKS, now - 3_600_000L, "580922/19-м", 1_000_000L, "RUB"))
        ResultPeriod.values().forEach { assertEquals(it.name, 0L, result(events, it).pnlKopecks) }
    }

    @Test
    fun `a price change today shows in 24 hours, older trades do not`() {
        val events = listOf(
            BrokerCashMove(BKS, now - 40 * day, null, 1_000_000L, "RUB"),
            buy(now - 40 * day, 2_000_000L),            // 1000 × 2,00 = 2 000 ₽
            buy(now - 10 * day, 2_100_000L),            // цена выросла до 2,10: +100 ₽ на первой тысяче
            buy(now - 3_600_000L, 2_200_000L),          // сегодня 2,20: +200 ₽ на двух тысячах
        )
        assertEquals(20_000L, result(events, ResultPeriod.DAY).pnlKopecks)
        assertEquals(30_000L, result(events, ResultPeriod.MONTH).pnlKopecks)
        assertEquals(30_000L, result(events, ResultPeriod.ALL).pnlKopecks)
    }

    @Test
    fun `money that came in during the period is the base of the percent`() {
        val events = listOf(BrokerCashMove(BKS, now - 3_600_000L, null, 1_000_000L, "RUB"))
        assertEquals(0.0, result(events, ResultPeriod.DAY).percent!!, 1e-9)
        val nothing = listOf(BrokerOrder(BKS, now, "LQDT", OrderSide.BUY, 1, 1_000_000L, OrderStatus.ACTIVE))
        assertNull(Portfolio.compute(nothing, now = now, zone = utc).periods[ResultPeriod.ALL]?.firstOrNull()?.percent)
    }
}
