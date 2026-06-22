package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.RosbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RosbankParserTest {
    private val parser = RosbankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses standard expense`() {
        val body = "Карта *1234: Покупка 1500.00 RUB МАГАЗИН 01.06.2025 15:30. Остаток: 5000.00 RUB"
        val tx = parser.parse("ROSBANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(500_000L, tx.balanceKopecks)
    }

    @Test fun `parses Оплата variant`() {
        val body = "Карта *9876: Оплата 750.00 RUB АПТЕКА 18.06.2025 10:00. Баланс: 3250.00 RUB"
        val tx = parser.parse("РОСБАНК", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(75_000L, tx.amountKopecks)
    }

    @Test fun `parses income (Зачисление)`() {
        val body = "Карта *5678: Зачисление 10000.00 RUB 01.06.2025 15:30"
        val tx = parser.parse("ROSBANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
        assertEquals("5678", tx.cardMask)
    }

    @Test fun `canHandle matches senders`() {
        assert(parser.canHandle("ROSBANK"))
        assert(parser.canHandle("РОСБАНК"))
        assert(!parser.canHandle("TINKOFF"))
    }

    @Test fun `returns null for non-bank SMS`() {
        assertNull(parser.parse("ROSBANK", "Ваш код: 8847. Не сообщайте его никому.", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "Карта *1234: Покупка 1500.00 RUB МАГАЗИН 01.06.2025 15:30. Остаток: 5000.00 RUB"
        assertEquals(
            parser.parse("ROSBANK", body, ts)!!.smsId,
            parser.parse("ROSBANK", body, ts)!!.smsId,
        )
    }

    @Test fun `bankId is rosbank`() {
        assertEquals("rosbank", parser.bankId)
    }
}
