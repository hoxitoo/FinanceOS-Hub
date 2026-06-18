package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.AlfabankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlfabankParserTest {
    private val parser = AlfabankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses expense`() {
        val body = "Покупка. Карта *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. Доступно: 10000.00 RUB"
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
    }

    @Test fun `parses expense with Баланс`() {
        val body = "Оплата. Карта *5678. 18.06.2025 10:00:00. 299.90 RUB. COFFEE. Баланс: 5000.10 RUB"
        val tx = parser.parse("ALFA", body, ts)
        assertNotNull(tx)
        assertEquals(29_990L, tx!!.amountKopecks)
        assertEquals("COFFEE", tx.merchant)
    }

    @Test fun `parses income`() {
        val body = "Зачисление. Карта *1234. 10000.00 RUB."
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
    }

    @Test fun `canHandle matches sender`() {
        assert(parser.canHandle("ALFABANK"))
        assert(parser.canHandle("ALFA"))
        assert(parser.canHandle("АЛЬФА"))
        assert(!parser.canHandle("TINKOFF"))
    }

    @Test fun `does not parse promotional SMS`() {
        assertNull(parser.parse("ALFA", "Специальное предложение для вас!", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "Покупка. Карта *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. Доступно: 10000.00 RUB"
        assertEquals(
            parser.parse("ALFABANK", body, ts)!!.smsId,
            parser.parse("ALFABANK", body, ts)!!.smsId,
        )
    }
}
