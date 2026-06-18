package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.TbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TbankParserTest {
    private val parser = TbankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses expense with card and balance`() {
        val body = "Оплата 1500,00 RUB. Кафе Урюк. Карта *1234. Баланс: 5000,00 RUB"
        val tx = parser.parse("TINKOFF", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("Кафе Урюк", tx.merchant)
        assertEquals("1234", tx.cardMask)
    }

    @Test fun `parses expense with ruble sign`() {
        val body = "Оплата 349,99 ₽. Пятёрочка. Карта *5678. Доступно: 12000,00 ₽"
        val tx = parser.parse("TBANK", body, ts)
        assertNotNull(tx)
        assertEquals(34_999L, tx!!.amountKopecks)
        assertEquals("Пятёрочка", tx.merchant)
    }

    @Test fun `parses income`() {
        val body = "Пополнение 10000,00 RUB. Карта *1234. Баланс: 15000,00 RUB"
        val tx = parser.parse("TINKOFF", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
    }

    @Test fun `canHandle matches sender patterns`() {
        assert(parser.canHandle("TINKOFF"))
        assert(parser.canHandle("TBANK"))
        assert(parser.canHandle("Т-БАНК"))
        assert(!parser.canHandle("SBERBANK"))
    }

    @Test fun `does not parse unrelated SMS`() {
        assertNull(parser.parse("TINKOFF", "Добро пожаловать в Т-Банк!", ts))
    }

    @Test fun `smsId dedup works`() {
        val body = "Оплата 1500,00 RUB. Кафе Урюк. Карта *1234. Баланс: 5000,00 RUB"
        assertEquals(
            parser.parse("TINKOFF", body, ts)!!.smsId,
            parser.parse("TINKOFF", body, ts)!!.smsId,
        )
    }
}
