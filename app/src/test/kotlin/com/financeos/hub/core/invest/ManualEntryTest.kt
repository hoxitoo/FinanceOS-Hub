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
        // Цена × количество, переполняющие Long, — не сделка, а ошибка ввода.
        assertNull(ManualEntry.operation(Kind.BUY, BKS, ts, ticker = "SBER", quantity = 999_999_999_999, priceMicros = 999_999_999_999_000_000))
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

    // ── Валюта и правка (#51) ────────────────────────────────────────────────────

    @Test
    fun `cents and fen are entered as an amount in their own currency`() {
        val usd = ManualEntry.currencyCash(BKS, ts, "USD000SMALL", 41L)!!.single() as BrokerCashMove
        assertEquals("USD", usd.currency)
        assertEquals(41L, usd.amountKopecks)
        val cny = ManualEntry.currencyCash(BKS, ts, "CNY000SMALL", 41_60L, contract = "580922/19-м")!!.single() as BrokerCashMove
        assertEquals("CNY", cny.currency)
        assertEquals("580922/19-м", cny.contract)
        assertNull(ManualEntry.currencyCash(BKS, ts, "SBER", 100L))
        assertNull(ManualEntry.currencyCash(BKS, ts, "USD000SMALL", 0L))
        // Портфель держит их отдельными валютами, а не рублями.
        val p = Portfolio.compute(listOf(usd, cny), now = ts + 1)
        assertEquals(setOf("USD", "CNY"), p.summaries.map { it.currency }.toSet())
        assertEquals(41L, p.summaries.first { it.currency == "USD" }.totalKopecks)
    }

    @Test
    fun `a deposit in dollars keeps its currency`() {
        val d = ManualEntry.operation(Kind.DEPOSIT, BKS, ts, amountKopecks = 41L, currency = "USD")!!.single() as BrokerCashMove
        assertEquals("USD", d.currency)
    }

    @Test
    fun `the edit draft of an asset covers the whole record`() {
        val record = ManualEntry.asset(BKS, ts, "LQDT", 4800, 2_098_500L, "580922/19-м")!! +
            BrokerPriceMark(BKS, ts + 1, "LQDT", 2_101_700L)
        val d = ManualEntry.draftOf(record[0], record)!!
        assertTrue(d.asset)
        assertEquals("LQDT", d.ticker)
        assertEquals(4800L, d.quantity)
        assertEquals(2_098_500L, d.priceMicros)
        assertEquals(2_101_700L, d.currentPriceMicros)
        assertEquals("580922/19-м", d.contract)
        // Нажата покупка — та же запись.
        assertEquals(d, ManualEntry.draftOf(record[1], record))
    }

    @Test
    fun `the edit draft of single operations`() {
        val out = BrokerCashMove(BKS, ts, "1", -500_00L, "CNY")
        val d = ManualEntry.draftOf(out, listOf(out))!!
        assertEquals(Kind.WITHDRAW, d.kind)
        assertEquals(500_00L, d.amountKopecks)
        assertEquals("CNY", d.currency)
        val t = BrokerInternalTransfer(BKS, ts, 189_00L, "RUB", "580922/19-м", "3468071/25")
        val dt = ManualEntry.draftOf(t, listOf(t))!!
        assertEquals(Kind.TRANSFER, dt.kind)
        assertEquals("3468071/25", dt.toContract)
        val sell = BrokerOrder(BKS, ts, "SBER", OrderSide.SELL, 10, 300_000_000L, OrderStatus.FILLED)
        assertEquals(Kind.SELL, ManualEntry.draftOf(sell, listOf(sell))!!.kind)
        assertNull(ManualEntry.draftOf(BrokerMarginAlert(BKS, ts, "1", null, 1L), emptyList()))
    }

    @Test
    fun `editing keeps the time of day, and the exact moment when the day is the same`() {
        val zone = java.time.ZoneId.of("Europe/Moscow")
        val original = java.time.ZonedDateTime.of(2026, 10, 1, 9, 53, 12, 0, zone).toInstant().toEpochMilli()
        assertEquals(original, ManualEntry.editedTimestamp(original, java.time.LocalDate.of(2026, 10, 1), zone))
        val moved = ManualEntry.editedTimestamp(original, java.time.LocalDate.of(2026, 9, 30), zone)
        val at = java.time.Instant.ofEpochMilli(moved).atZone(zone)
        assertEquals(java.time.LocalDate.of(2026, 9, 30), at.toLocalDate())
        assertEquals(java.time.LocalTime.of(9, 53, 12), at.toLocalTime())
    }

    @Test
    fun `an edited pushed order keeps what ties it to its earlier pushes`() {
        val pushed = BrokerOrder(BKS, ts, "LQDT", OrderSide.BUY, 20, BrokerOrder.UNKNOWN_PRICE, OrderStatus.FILLED, "Рыночная", id = "ru.broker.my_1_2")
        // Форма прислала бы и другие лоты, и «сейчас» — правка пуша меняет только цену, счёт, валюту.
        val edited = ManualEntry.operation(Kind.BUY, BKS, ts + 99_000, ticker = "LQDT", quantity = 21, priceMicros = 2_101_700L, contract = "580922/19-м")!!
        val kept = ManualEntry.keepPushed(pushed, edited).single() as BrokerOrder
        assertEquals(OrderStatus.FILLED, kept.status)
        assertEquals("Рыночная", kept.kind)
        assertEquals(20L, kept.lots)
        assertEquals(ts, kept.timestamp)
        assertEquals(2_101_700L, kept.priceMicros)
        assertEquals("580922/19-м", kept.contract)
        // Отменённая остаётся отменённой.
        val cancelled = pushed.copy(status = OrderStatus.CANCELLED)
        assertEquals(OrderStatus.CANCELLED, (ManualEntry.keepPushed(cancelled, edited).single() as BrokerOrder).status)
        // Ручная запись — как ввёл человек.
        val manual = pushed.copy(id = "manual_x_0")
        assertEquals(21L, (ManualEntry.keepPushed(manual, edited).single() as BrokerOrder).lots)
    }

    @Test
    fun `an edited pushed transfer keeps the account names the broker wrote`() {
        val pushed = BrokerInternalTransfer(BKS, ts, 189_00L, "RUB", "№580922/19-м", "3468071/25", null, "Облигации", id = "ru.broker.my_3_4")
        val edited = ManualEntry.operation(Kind.TRANSFER, BKS, ts, amountKopecks = 190_00L, contract = "580922/19-м", toContract = "3468071/25")!!
        val kept = ManualEntry.keepPushed(pushed, edited).single() as BrokerInternalTransfer
        assertEquals("Облигации", kept.toLabel)
        assertEquals(190_00L, kept.amountKopecks)
    }

    @Test
    fun `moving an entry to today never puts it in the future`() {
        val zone = java.time.ZoneId.of("Europe/Moscow")
        val yesterdayLate = java.time.ZonedDateTime.of(2026, 10, 4, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = java.time.ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(now, ManualEntry.editedTimestamp(yesterdayLate, java.time.LocalDate.of(2026, 10, 5), zone, now))
    }

    @Test
    fun `the draft of an order without a price leaves the price empty`() {
        val market = BrokerOrder(BKS, ts, "LQDT", OrderSide.BUY, 20, BrokerOrder.UNKNOWN_PRICE, OrderStatus.FILLED)
        assertNull(ManualEntry.draftOf(market, listOf(market))!!.priceMicros)
    }

    @Test
    fun `price input round-trips without grouping`() {
        assertEquals("2,0985", ManualEntry.priceInput(2_098_500L))
        assertEquals("300", ManualEntry.priceInput(300_000_000L))
        assertEquals(2_098_500L, ManualEntry.parsePrice(ManualEntry.priceInput(2_098_500L)))
    }
}
