package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.PostaBankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PostaBankParserTest {
    private val parser = PostaBankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses standard expense`() {
        val body = "Почта Банк: Покупка 1 500.00 руб. по карте *1234 в МАГАЗИН. Доступно: 5 000.00 руб."
        val tx = parser.parse("POSTABANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(500_000L, tx.balanceKopecks)
    }

    @Test fun `parses expense with Оплата keyword`() {
        val body = "Почта Банк: Оплата 800.00 руб. по карте *5678 у АПТЕКА. Остаток: 4 200.00 руб."
        val tx = parser.parse("ПОЧТА БАНК", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(80_000L, tx.amountKopecks)
    }

    @Test fun `parses income (Зачисление)`() {
        val body = "Почта Банк: Зачисление 10 000.00 руб. на карту *1234"
        val tx = parser.parse("POSTABANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
        assertEquals("1234", tx.cardMask)
    }

    @Test fun `canHandle matches senders`() {
        assert(parser.canHandle("POSTABANK"))
        assert(parser.canHandle("POSTBANK"))
        assert(parser.canHandle("ПОЧТА БАНК"))
        assert(parser.canHandle("ПОЧТАБАНК"))
        assert(!parser.canHandle("ALFABANK"))
    }

    @Test fun `returns null for non-transaction SMS`() {
        assertNull(parser.parse("POSTABANK", "Ваш PIN успешно изменён.", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "Почта Банк: Покупка 1 500.00 руб. по карте *1234 в МАГАЗИН. Доступно: 5 000.00 руб."
        assertEquals(
            parser.parse("POSTABANK", body, ts)!!.smsId,
            parser.parse("POSTABANK", body, ts)!!.smsId,
        )
    }

    @Test fun `bankId is postabank`() {
        assertEquals("postabank", parser.bankId)
    }
}
