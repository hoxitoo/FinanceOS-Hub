package com.financeos.hub.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPatternsTest {

    /** The reported bug: "со счета 4*1139 на счет 4*3583" lost both masks (network-digit prefix). */
    @Test fun `extracts source and destination from network-prefixed account form`() {
        val body = "Перевод 15 000,00 RUB со счета 4*1139 на счет 4*3583"
        val r = TransferPatterns.detect(body)
        assertNotNull(r)
        assertTrue(r!!.outgoing)
        assertEquals(1_500_000L, r.amountKopecks)
        assertEquals("1139", r.cardMask)            // source (booked account)
        assertEquals("3583", r.counterpartyMask)    // destination (goal-linked account)
    }

    @Test fun `extracts destination from classic glyph form`() {
        val body = "Перевод 5 000 ₽ на карту *1234. Остаток: 10 000 ₽"
        val r = TransferPatterns.detect(body)
        assertNotNull(r)
        assertEquals("1234", r!!.counterpartyMask)
        assertEquals(1_000_000L, r.balanceKopecks)
    }

    @Test fun `extracts destination when only bare digits follow`() {
        val body = "Перечисление 2 000 руб на счёт 5678"
        val r = TransferPatterns.detect(body)
        assertNotNull(r)
        assertEquals("5678", r!!.counterpartyMask)
    }

    @Test fun `incoming transfer is detected and not marked outgoing`() {
        val body = "Зачисление перевода 7 500,00 RUB от ИВАН И."
        val r = TransferPatterns.detect(body)
        assertNotNull(r)
        assertFalse(r!!.outgoing)
        assertEquals(750_000L, r.amountKopecks)
    }

    @Test fun `non-transfer text returns null`() {
        assertNull(TransferPatterns.detect("Покупка 1 500,00 RUB. ПЯТЁРОЧКА. Остаток: 5 000 ₽"))
    }

    /**
     * Reported bug: a T-Bank marketing push ("…бесплатными переводами") booked a phantom 163 000 ₽
     * transfer because "переводАМИ" matched the "Перевод" keyword by substring. The stem lookahead
     * must reject the inflected marketing word while still matching a real "Перевод 5 000 ₽".
     */
    @Test fun `marketing word 'переводами' is not detected as a transfer`() {
        val body = "Одобрили кредитку. Получите карту с лимитом 163 000 ₽, " +
            "кэшбэком до 30%, рассрочками и бесплатными переводами"
        assertNull(TransferPatterns.detect(body))
    }

    @Test fun `inflected 'переводов' marketing word is not detected`() {
        assertNull(TransferPatterns.detect("Без комиссии для переводов до 100 000 ₽ в месяц"))
    }

    @Test fun `real outgoing transfer still detected after boundary tightening`() {
        val r = TransferPatterns.detect("Перевод 5 000 ₽ выполнен")
        assertNotNull(r)
        assertTrue(r!!.outgoing)
        assertEquals(500_000L, r.amountKopecks)
    }

    @Test fun `caller-supplied own card mask overrides auto extraction`() {
        val body = "Перевод 1 000 ₽ на карту *3583"
        val r = TransferPatterns.detect(body, ownCardMask = "1139")
        assertNotNull(r)
        assertEquals("1139", r!!.cardMask)
        assertEquals("3583", r.counterpartyMask)
    }
}
