package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.OtkritieParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OtkritieParserTest {
    private val parser = OtkritieParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses standard expense`() {
        val body = "Карта *1234: Списание 1 500,00 RUB. МАГАЗИН. Остаток: 5 000,00 RUB"
        val tx = parser.parse("OTKRITIE", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(500_000L, tx.balanceKopecks)
    }

    @Test fun `parses Покупка variant`() {
        val body = "Карта *5555: Покупка 2 200,00 RUB. КАФЕ. Доступно: 8 800,00 RUB"
        val tx = parser.parse("ОТКРЫТИЕ", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(220_000L, tx.amountKopecks)
    }

    @Test fun `parses income (Зачисление)`() {
        val body = "Карта *1234: Зачисление 10 000,00 RUB"
        val tx = parser.parse("OTKRITIE", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
    }

    @Test fun `canHandle matches senders`() {
        assert(parser.canHandle("OTKRITIE"))
        assert(parser.canHandle("ОТКРЫТИЕ"))
        assert(parser.canHandle("OPENBANK"))
        assert(parser.canHandle("ФКО"))
        assert(!parser.canHandle("SBERBANK"))
    }

    @Test fun `returns null for OTP`() {
        assertNull(parser.parse("OTKRITIE", "Код: 553311. Срок действия 5 минут.", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "Карта *1234: Списание 1 500,00 RUB. МАГАЗИН. Остаток: 5 000,00 RUB"
        assertEquals(
            parser.parse("OTKRITIE", body, ts)!!.smsId,
            parser.parse("OTKRITIE", body, ts)!!.smsId,
        )
    }

    @Test fun `bankId is otkritie`() {
        assertEquals("otkritie", parser.bankId)
    }
}
