package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.VtbParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VtbParserTest {
    private val parser = VtbParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses expense`() {
        val body = "ВТБ. Карта *1234. Покупка 2500,00 руб. МАГАЗИН 18.06.25 в 12:34. Баланс 9500,00 руб."
        val tx = parser.parse("VTB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(250_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(950_000L, tx.balanceKopecks)
    }

    @Test fun `parses income`() {
        val body = "ВТБ. Карта *5678. Зачисление 20000,00 руб."
        val tx = parser.parse("ВТБ", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(2_000_000L, tx.amountKopecks)
    }

    @Test fun `canHandle matches sender`() {
        assert(parser.canHandle("VTB"))
        assert(parser.canHandle("ВТБ"))
        assert(!parser.canHandle("SBERBANK"))
    }

    @Test fun `does not parse OTP SMS`() {
        assertNull(parser.parse("ВТБ", "Код для входа: 5566. Не сообщайте никому.", ts))
    }

    @Test fun `parses Оплата variant`() {
        val body = "ВТБ. Карта *9999. Оплата 500,00 руб. КАФЕ 18.06.25 в 09:00. Баланс 4500,00 руб."
        val tx = parser.parse("VTB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(50_000L, tx.amountKopecks)
    }

    @Test fun `smsId is stable`() {
        val body = "ВТБ. Карта *1234. Покупка 2500,00 руб. МАГАЗИН 18.06.25 в 12:34. Баланс 9500,00 руб."
        assertEquals(
            parser.parse("VTB", body, ts)!!.smsId,
            parser.parse("VTB", body, ts)!!.smsId,
        )
    }
}
