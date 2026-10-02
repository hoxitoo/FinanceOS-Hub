package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Группы бумаг по тикеру — как разделы в приложении БКС. Тикеры — с экрана пользователя. */
class SecurityGroupsTest {

    @Test
    fun `tickers from the real BKS portfolio land in the same groups`() {
        assertEquals(SecurityGroup.FUNDS, SecurityGroups.of("LQDT"))       // «БПИФ Ликвидность» → Фонды
        assertEquals(SecurityGroup.OTC, SecurityGroups.of("3800_HK"))      // → Внебиржевые активы
    }

    @Test
    fun `currency instruments, bonds and shares`() {
        listOf("USD000UTSTOM", "CNYRUB_TOM", "EUR_RUB__TOM", "CNY").forEach {
            assertEquals(it, SecurityGroup.CURRENCY, SecurityGroups.of(it))
        }
        assertEquals(SecurityGroup.BONDS, SecurityGroups.of("SU26238RMFS4"))
        assertEquals(SecurityGroup.BONDS, SecurityGroups.of("RU000A105C93"))
        listOf("SBER", "SBERP", "GAZP", "sber").forEach { assertEquals(it, SecurityGroup.SHARES, SecurityGroups.of(it)) }
    }

    @Test
    fun `an unrecognised ticker goes to Other, not to a guessed shelf`() {
        assertEquals(SecurityGroup.OTHER, SecurityGroups.of("XYZ12"))
        assertEquals(SecurityGroup.OTHER, SecurityGroups.of("ABC"))
    }

    @Test
    fun `portfolio groups put cash under Currency and keep the BKS order`() {
        val ts = 1_759_300_000_000L
        val r = Portfolio.compute(listOf(
            BrokerCashMove(BKS, ts, "580922/19-м", 1_000_000L, "RUB"),
            BrokerOrder(BKS, ts + 1, "LQDT", OrderSide.BUY, 4760, 2_098_500L, OrderStatus.FILLED),
            BrokerOrder(BKS, ts + 2, "SBER", OrderSide.BUY, 1, 300_000_000L, OrderStatus.FILLED),
        ))
        assertEquals(listOf(SecurityGroup.CURRENCY, SecurityGroup.SHARES, SecurityGroup.FUNDS), r.groups.map { it.group })
        val currency = r.groups.first()
        assertTrue(currency.positions.isEmpty())
        assertEquals("RUB", currency.cash.single().currency)
        // Деньги группы «Валюта» — те же, что «деньги» в итоге: 10 000 − 9 988,86 − 3 000.
        assertEquals(r.summaries.single().cashKopecks, currency.valueByCurrency["RUB"])
        assertEquals(998_886L, r.groups.last().valueByCurrency["RUB"])
    }
}
