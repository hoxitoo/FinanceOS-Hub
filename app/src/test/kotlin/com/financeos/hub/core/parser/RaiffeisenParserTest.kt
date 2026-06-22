package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.RaiffeisenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RaiffeisenParserTest {
    private val parser = RaiffeisenParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses verbose expense (format 1)`() {
        val body = "Оплата по карте *1234 на 1 500.00 ₽ в МАГАЗИН. Доступно: 5 000.00 ₽"
        val tx = parser.parse("RAIFFEISEN", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
        assertEquals("МАГАЗИН", tx.merchant)
        assertEquals("1234", tx.cardMask)
        assertEquals(500_000L, tx.balanceKopecks)
    }

    @Test fun `parses expense format 2 (карта, покупка, магазин)`() {
        val body = "карта *5678, покупка на сумму 2 300,00 RUB, магазин КОФЕЙНЯ, доступно 7 700,00 RUB"
        val tx = parser.parse("RAIF", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(230_000L, tx.amountKopecks)
        assertEquals("5678", tx.cardMask)
    }

    @Test fun `parses income (Пополнение)`() {
        val body = "Пополнение счёта *1234 на 10 000.00 ₽"
        val tx = parser.parse("RAIFFEISEN", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1_000_000L, tx.amountKopecks)
        assertEquals("1234", tx.cardMask)
    }

    @Test fun `canHandle matches senders`() {
        assert(parser.canHandle("RAIFFEISEN"))
        assert(parser.canHandle("RAIF"))
        assert(parser.canHandle("РАЙФФАЙЗЕН"))
        assert(!parser.canHandle("SBERBANK"))
    }

    @Test fun `returns null for OTP SMS`() {
        assertNull(parser.parse("RAIFFEISEN", "Код подтверждения: 112233. Не сообщайте никому.", ts))
    }

    @Test fun `smsId is stable across identical parses`() {
        val body = "Оплата по карте *1234 на 1 500.00 ₽ в МАГАЗИН. Доступно: 5 000.00 ₽"
        assertEquals(
            parser.parse("RAIFFEISEN", body, ts)!!.smsId,
            parser.parse("RAIFFEISEN", body, ts)!!.smsId,
        )
    }

    @Test fun `bankId is raiffeisen`() {
        assertEquals("raiffeisen", parser.bankId)
    }
}
