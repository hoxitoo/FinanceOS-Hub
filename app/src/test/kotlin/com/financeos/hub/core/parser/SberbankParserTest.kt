package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.SberbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SberbankParserTest {
    private val parser = SberbankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses RU expense with balance`() {
        val body = "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
        val tx = parser.parse("900", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(1_234_567L, tx.balanceKopecks)
    }

    @Test fun `parses RU expense decimal amount`() {
        val body = "VISA5678 18.06.25 09:00 Оплата 349,99р KFC Баланс: 5 000,00р"
        val tx = parser.parse("900", body, ts)
        assertNotNull(tx)
        assertEquals(34_999L, tx!!.amountKopecks)
        assertEquals("KFC", tx.merchant)
    }

    @Test fun `parses RU income`() {
        val body = "VISA1234 18.06.25 10:00 Зачисление 50 000р"
        val tx = parser.parse("SBERBANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(5_000_000L, tx.amountKopecks)
    }

    @Test fun `does not parse unrelated SMS`() {
        val body = "Ваш код подтверждения: 123456"
        val tx = parser.parse("900", body, ts)
        assertNull(tx)
    }

    @Test fun `canHandle matches sender patterns`() {
        assert(parser.canHandle("900"))
        assert(parser.canHandle("SBERBANK"))
        assert(parser.canHandle("СберБанк"))
        assert(!parser.canHandle("TINKOFF"))
    }

    @Test fun `smsId is deterministic`() {
        val body = "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
        val tx1 = parser.parse("900", body, ts)
        val tx2 = parser.parse("900", body, ts)
        assertEquals(tx1!!.smsId, tx2!!.smsId)
    }
}
