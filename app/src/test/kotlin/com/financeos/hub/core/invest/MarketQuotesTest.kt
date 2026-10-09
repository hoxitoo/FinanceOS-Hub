package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Котировки Мосбиржи (#55): разбор ответов ISS, история, кэш и оценка портфеля по рынку. */
class MarketQuotesTest {

    private val day = 24 * 3_600_000L
    private val t0  = 1_791_000_000_000L

    /** Формат ISS с iss.meta=off: блоки «columns + data»; колонки в другом порядке — разбор по именам. */
    private val shares = """
        {"securities":{"columns":["SECID","BOARDID","LOTSIZE","CURRENCYID","PREVPRICE","PREVLEGALCLOSEPRICE"],
          "data":[["LQDT","TQTF",1,"SUR",2.1001,2.1001],["SBER","SMAL",1,"SUR",300,300],["SBER","TQBR",10,"SUR",301.5,301.5]]},
         "marketdata":{"columns":["BOARDID","SECID","LAST","LCURRENTPRICE","MARKETPRICE"],
          "data":[["TQTF","LQDT",2.1017,2.1017,2.101],["TQBR","SBER",null,302.1,null],["SMAL","SBER",1,1,1]]}}
    """.trimIndent()

    @Test
    fun `shares and funds take the main board, last price and lot size`() {
        val q = MarketQuotes.parseShares(shares).associateBy { it.ticker }
        val lqdt = q.getValue("LQDT")
        assertEquals(2_101_700L, lqdt.priceMicros)
        assertEquals(SecurityGroup.FUNDS, lqdt.group)
        assertEquals("RUB", lqdt.currency)
        assertEquals(2_100_100L, lqdt.prevPriceMicros)
        // Вне сессии LAST пуст — текущая цена; режим TQBR, а не «неполные лоты» SMAL.
        val sber = q.getValue("SBER")
        assertEquals(302_100_000L, sber.priceMicros)
        assertEquals(10L, sber.lotSize)
        assertEquals(SecurityGroup.SHARES, sber.group)
    }

    @Test
    fun `a bond costs nominal times percent plus accrued coupon`() {
        val bonds = """
            {"securities":{"columns":["SECID","BOARDID","LOTSIZE","FACEVALUE","FACEUNIT","ACCRUEDINT","PREVPRICE","CURRENCYID"],
              "data":[["SU26238RMFS4","TQOB",1,1000,"SUR",12.34,98.2,"SUR"]]},
             "marketdata":{"columns":["SECID","BOARDID","LAST","MARKETPRICE"],"data":[["SU26238RMFS4","TQOB",98.5,null]]}}
        """.trimIndent()
        val q = MarketQuotes.parseBonds(bonds).single()
        assertEquals(997_340_000L, q.priceMicros)            // 1000 × 98,5 % + 12,34
        assertEquals(994_340_000L, q.prevPriceMicros)        // 1000 × 98,2 % + 12,34
        assertEquals(SecurityGroup.BONDS, q.group)
    }

    @Test
    fun `currency rates come from the exchange, previous price when there was no trade`() {
        val fx = """
            {"securities":{"columns":["SECID","BOARDID","PREVPRICE","PREVWAPRICE"],
              "data":[["USD000UTSTOM","CETS",81.2,81.25],["CNYRUB_TOM","CETS",11.3,11.31]]},
             "marketdata":{"columns":["SECID","BOARDID","LAST","WAPRICE","MARKETPRICE"],
              "data":[["USD000UTSTOM","CETS",81.5,81.4,null],["CNYRUB_TOM","CETS",null,null,null]]}}
        """.trimIndent()
        val r = MarketQuotes.parseFx(fx)
        assertEquals(81_500_000L, r["USD"])
        assertEquals(11_310_000L, r["CNY"])
        assertNull(r["EUR"])
    }

    @Test
    fun `broken answers give nothing instead of crashing`() {
        assertTrue(MarketQuotes.parseShares("<html>503</html>").isEmpty())
        assertTrue(MarketQuotes.parseFx("{}").isEmpty())
        assertEquals(MarketQuotes.Cache.EMPTY, MarketQuotes.fromJson("not json"))
    }

    private fun snapshot(at: Long, price: Long, prev: Long? = null, fx: Map<String, Long> = emptyMap()) =
        MarketQuotes.Snapshot(
            mapOf("LQDT" to MarketQuotes.Quote("LQDT", price, "RUB", 1, SecurityGroup.FUNDS, prev)), fx, at,
        )

    @Test
    fun `history keeps one point a day and the previous close a day before`() {
        var c = MarketQuotes.merge(MarketQuotes.Cache.EMPTY, snapshot(t0, 2_101_700L, prev = 2_100_100L))
        assertEquals(2_100_100L, c.priceAt("LQDT", t0 - day)?.priceMicros)
        assertEquals(2_101_700L, c.priceAt("LQDT", t0)?.priceMicros)
        // Повторное обновление в тот же день заменяет точку, а не множит.
        c = MarketQuotes.merge(c, snapshot(t0 + 60_000, 2_102_000L, prev = 2_100_100L))
        assertEquals(2, c.history.getValue("LQDT").size)
        assertEquals(2_102_000L, c.priceAt("LQDT", t0 + day)?.priceMicros)
        // И переживает запись на диск.
        assertEquals(c, MarketQuotes.fromJson(MarketQuotes.toJson(c)))
    }

    // ── Портфель по рынку ───────────────────────────────────────────────────────

    private val deposit = BrokerCashMove(BKS, t0 - 3 * day, "580922/19-м", 1_000_000L, "RUB", id = "p1")
    private val buy = BrokerOrder(BKS, t0 - 3 * day + 1, "LQDT", OrderSide.BUY, 4760, 2_098_500L, OrderStatus.FILLED, id = "p2")

    @Test
    fun `the position is valued at the exchange price when it is newer than own trade`() {
        val market = MarketQuotes.merge(MarketQuotes.Cache.EMPTY, snapshot(t0, 2_101_700L, prev = 2_100_100L))
        val p = Portfolio.compute(listOf(deposit, buy), now = t0 + 1, market = market)
        val lqdt = p.positions.single()
        assertEquals(2_101_700L, lqdt.lastPriceMicros)
        assertEquals(t0, lqdt.marketAt)
        assertEquals(t0, p.marketAt)
        // За 24 часа — разница с прошлым закрытием: 4760 × (2,1017 − 2,1001) ≈ 7,62 ₽; каждая
        // стоимость округляется до копейки (10 004,09 − 9 996,48), отсюда 7,61.
        assertEquals(761L, p.periods.getValue(Portfolio.ResultPeriod.DAY).single().pnlKopecks)
        // Своя сделка новее биржевой цены — побеждает она.
        val later = buy.copy(timestamp = t0 + 10, lots = 10, id = "p3", priceMicros = 2_200_000L)
        assertEquals(2_200_000L, Portfolio.compute(listOf(deposit, buy, later), now = t0 + 11, market = market)
            .positions.single().lastPriceMicros)
    }

    @Test
    fun `lot size multiplies pushed orders but not manual pieces`() {
        val q = MarketQuotes.Quote("SBER", 300_000_000L, "RUB", 10, SecurityGroup.SHARES, null)
        val market = MarketQuotes.Cache(MarketQuotes.Snapshot(mapOf("SBER" to q), emptyMap(), t0 - 10 * day), emptyMap())
        val pushed = BrokerOrder(BKS, t0, "SBER", OrderSide.BUY, 2, 300_000_000L, OrderStatus.FILLED, id = "x")
        val manual = pushed.copy(id = "manual_a_0", lots = 5)
        val p = Portfolio.compute(listOf(pushed, manual), now = t0 + 1, market = market)
        assertEquals(25L, p.positions.single().quantity)     // 2 лота × 10 + 5 штук
    }

    @Test
    fun `the total is shown in roubles once exchange rates are known`() {
        val usd = BrokerCashMove(BKS, t0, null, 41L, "USD", id = "u")
        val noRates = Portfolio.compute(listOf(deposit, usd), now = t0 + 1)
        assertNull(noRates.totalRubKopecks)                   // без курса не складываем
        val market = MarketQuotes.Cache(MarketQuotes.Snapshot(emptyMap(), mapOf("USD" to 81_500_000L), t0), emptyMap())
        val p = Portfolio.compute(listOf(deposit, usd), now = t0 + 1, market = market)
        assertEquals(1_000_000L + 3_342L, p.totalRubKopecks)  // 10 000 ₽ + 0,41 $ × 81,5
    }
}
