package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.RosselkhozParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RosselkhozParserTest {
    private val parser = RosselkhozParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses expense format 1 (verbose)`() {
        val body = "RSHB: Покупка 1 500.00 руб по карте *1234. МАГАЗИН. Баланс: 5 000.00 руб."
        val tx = parser.parse("RSHB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(500_000L, tx.balanceKopecks)
    }

    @Test fun `parses expense format 2 (alternative layout)`() {
        val body = "РСХБ: Покупка на 2 300,00 р. Карта *5678. Магазин КОФЕЙНЯ. Баланс 7 700,00 р."
        val tx = parser.parse("РСХБ", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(230_000L, tx.amountKopecks)
        assertEquals("КОФЕЙНЯ", tx.merchant)
        assertEquals("5678", tx.cardMask)
        assertEquals(770_000L, tx.balanceKopecks)
    }

    @Test fun `parses income (Зачисление)`() {
        val body = "RSHB: Зачисление 10 000.00 руб на счет *1234"
        val tx = parser.parse("RSHB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
        assertEquals("1234", tx.cardMask)
    }

    @Test fun `canHandle matches senders`() {
        assert(parser.canHandle("RSHB"))
        assert(parser.canHandle("РСХБ"))
        assert(parser.canHandle("ROSSELKHOZ"))
        assert(parser.canHandle("РОССЕЛЬХОЗ"))
        assert(!parser.canHandle("GAZPROMBANK"))
    }

    @Test fun `returns null for service SMS`() {
        assertNull(parser.parse("RSHB", "Ваша заявка #112233 принята.", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "RSHB: Покупка 1 500.00 руб по карте *1234. МАГАЗИН. Баланс: 5 000.00 руб."
        assertEquals(
            parser.parse("RSHB", body, ts)!!.smsId,
            parser.parse("RSHB", body, ts)!!.smsId,
        )
    }

    @Test fun `bankId is rosselkhoz`() {
        assertEquals("rosselkhoz", parser.bankId)
    }
}
