package com.financeos.hub.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пуши приложения БКС, снятые с устройства 1 октября. Склеены как в `PushNotificationListener`:
 * заголовок, затем текст. Менять тексты нельзя — в этом их ценность.
 */
class BrokerPushParserTest {

    private val ts = 1_759_300_000_000L

    @Test
    fun `deposit to the brokerage account`() {
        val e = BrokerPushParser.parse("Пополнение счета Вы пополнили счет №580922/19-м на 10 000 RUB", ts)
        assertTrue(e is BrokerCashMove)
        e as BrokerCashMove
        assertEquals(1_000_000L, e.amountKopecks)
        assertEquals("RUB", e.currency)
        assertEquals("580922/19-м", e.contract)
        assertEquals(BrokerPushParser.BKS, e.broker)
    }

    @Test
    fun `order placed, cancelled and filled`() {
        val active = BrokerPushParser.parse(
            "LQDT: заявка активна Лимитная заявка на покупку 4760 лотов LQDT по 2.0984", ts) as BrokerOrder
        assertEquals(OrderStatus.ACTIVE, active.status)
        assertEquals("LQDT", active.ticker)
        assertEquals(OrderSide.BUY, active.side)
        assertEquals(4760L, active.lots)
        assertEquals(2_098_400L, active.priceMicros)
        assertEquals("Лимитная", active.kind)

        val cancelled = BrokerPushParser.parse(
            "LQDT: заявка отменена Лимитная заявка на покупку 4760 лотов LQDT по 2.0984", ts) as BrokerOrder
        assertEquals(OrderStatus.CANCELLED, cancelled.status)

        val filled = BrokerPushParser.parse(
            "LQDT: заявка исполнена Лимитная заявка на покупку 4760 лотов LQDT по 2.0985", ts) as BrokerOrder
        assertEquals(OrderStatus.FILLED, filled.status)
        assertEquals(2_098_500L, filled.priceMicros)
    }

    @Test
    fun `header and body must name the same security`() {
        assertNull(BrokerPushParser.parse(
            "SBER: заявка исполнена Лимитная заявка на покупку 10 лотов LQDT по 2.0985", ts))
    }

    @Test
    fun `ordinary notifications are not broker events`() {
        listOf(
            "Пополнение счета",                                      // без суммы — не угадываем
            "-10 000 ₽ Списание со счета 408*01139; Сумма: 10 000,00 RUB; Получатель платежа BKS Mir Investitsiy",
            "Новое сообщение от Андрея",
            "Ваша заявка на кредит одобрена",
            // Новости и акции из того же приложения БКС (снято с устройства 1 октября). Слово «БКС» и
            // двоеточие после имени есть, но это не сделка и не деньги — разбирать нечего.
            "Новая публикация BCS_Platform: ⚡ БКС Мир инвестиций снова на iPhone — инструкция по установке",
        ).forEach { assertNull(it, BrokerPushParser.parse(it, ts)) }
    }

    @Test
    fun `price keeps exchange precision`() {
        assertEquals(2_098_500L, BrokerPushParser.priceMicros("2.0985"))
        assertEquals(250_120_000L, BrokerPushParser.priceMicros("250,12"))
        assertEquals(1_000_000L, BrokerPushParser.priceMicros("1"))
        assertNull(BrokerPushParser.priceMicros("0"))
    }
}
