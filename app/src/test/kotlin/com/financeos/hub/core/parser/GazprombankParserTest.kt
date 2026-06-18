package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.GazprombankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GazprombankParserTest {
    private val parser = GazprombankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses expense`() {
        val body = "GPB. Операция по карте *1234. Списание 2500,00 руб. МАГАЗИН. Баланс 7500,00 руб."
        val tx = parser.parse("GPB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(250_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(750_000L, tx.balanceKopecks)
    }

    @Test fun `parses Покупка variant`() {
        val body = "GPB. Операция по карте *5678. Покупка 349,99 руб. АПТЕКА. Баланс 10000,00 руб."
        val tx = parser.parse("GAZPROMBANK", body, ts)
        assertNotNull(tx)
        assertEquals(34_999L, tx!!.amountKopecks)
        assertEquals("АПТЕКА", tx.merchant)
    }

    @Test fun `parses income`() {
        val body = "GPB. Операция по карте *1234. Зачисление 20000,00 руб."
        val tx = parser.parse("GPB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(2_000_000L, tx.amountKopecks)
    }

    @Test fun `canHandle matches sender`() {
        assert(parser.canHandle("GPB"))
        assert(parser.canHandle("GAZPROMBANK"))
        assert(parser.canHandle("ГАЗПРОМБАНК"))
        assert(!parser.canHandle("VTB"))
    }

    @Test fun `does not parse notification SMS`() {
        assertNull(parser.parse("GPB", "Код: 7788. Для входа в личный кабинет.", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "GPB. Операция по карте *1234. Списание 2500,00 руб. МАГАЗИН. Баланс 7500,00 руб."
        assertEquals(
            parser.parse("GPB", body, ts)!!.smsId,
            parser.parse("GPB", body, ts)!!.smsId,
        )
    }
}
