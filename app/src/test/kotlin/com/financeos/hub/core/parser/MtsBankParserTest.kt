package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.MtsBankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MtsBankParserTest {
    private val parser = MtsBankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses standard expense`() {
        val body = "Покупка 1500.00 RUB по карте *1234. Магазин ПЯТЁРОЧКА. Баланс: 5000.00 RUB"
        val tx = parser.parse("MTSB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("ПЯТЁРОЧКА", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(500_000L, tx.balanceKopecks)
    }

    @Test fun `parses Оплата variant`() {
        val body = "Оплата 350.00 RUB по карте *9876. Торговец КОФЕ. Доступно: 9650.00 RUB"
        val tx = parser.parse("МТС БАНК", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(35_000L, tx.amountKopecks)
    }

    @Test fun `parses income (Зачисление)`() {
        val body = "Зачисление 20000.00 RUB на карту *1234"
        val tx = parser.parse("MTSB", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(2_000_000L, tx.amountKopecks)
        assertEquals("1234", tx.cardMask)
    }

    @Test fun `canHandle matches senders`() {
        assert(parser.canHandle("MTSBANK"))
        assert(parser.canHandle("МТС БАНК"))
        assert(parser.canHandle("МТС-БАНК"))
        assert(parser.canHandle("MTSB"))
        assert(!parser.canHandle("900"))  // that's Sberbank
    }

    @Test fun `returns null for promo SMS`() {
        assertNull(parser.parse("MTSB", "Специальное предложение! Скидка 30% на кредит.", ts))
    }

    @Test fun `smsId is stable`() {
        val body = "Покупка 1500.00 RUB по карте *1234. Магазин ПЯТЁРОЧКА. Баланс: 5000.00 RUB"
        assertEquals(
            parser.parse("MTSB", body, ts)!!.smsId,
            parser.parse("MTSB", body, ts)!!.smsId,
        )
    }

    @Test fun `bankId is mtsbank`() {
        assertEquals("mtsbank", parser.bankId)
    }
}
