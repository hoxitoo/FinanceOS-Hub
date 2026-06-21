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

    @Test fun `parses push expense with single decimal digit`() {
        // Regression: "-468,7 ₽" has ONE decimal digit; the old fixed-2-digit regex dropped it.
        val body = "Альфа-Банк -468,7 ₽. Другое\nОстаток: 3 621,04 ₽; ••2548"
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(46_870L, tx.amountKopecks)          // 468.70 ₽
        assertEquals("Другое", tx.merchant)
        assertEquals("2548", tx.cardMask)
        assertEquals(362_104L, tx.balanceKopecks)        // 3 621.04 ₽
    }

    @Test fun `parses push income`() {
        val body = "Альфа-Банк +4 000 ₽. Поступление\nОстаток: 4 089,74 ₽; ••2548"
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(400_000L, tx.amountKopecks)
        assertEquals("2548", tx.cardMask)
        assertEquals(408_974L, tx.balanceKopecks)
    }

    @Test fun `parses push expense without balance or card`() {
        val tx = parser.parse("ALFABANK", "Альфа-Банк -468,7 ₽. Другое", ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(46_870L, tx.amountKopecks)
        assertEquals("Другое", tx.merchant)
        assertNull(tx.cardMask)
        assertNull(tx.balanceKopecks)
    }

    @Test fun `merchant ending in 4 digits is not mistaken for card mask`() {
        // Regression: a bare \d{4} end-anchor used to capture the merchant's trailing digits.
        val tx = parser.parse("ALFABANK", "Альфа-Банк -250 ₽. АЗС 2024", ts)
        assertNotNull(tx)
        assertEquals(25_000L, tx!!.amountKopecks)
        assertEquals("АЗС 2024", tx.merchant)
        assertNull(tx.cardMask)
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

    // Regression: outgoing P2P transfer push — card mask and balance must be extracted so
    // AccountLinker can resolve the account and syncBalance can update it on Dashboard.
    @Test fun `parses push transfer with card mask and balance`() {
        val body = "Альфа-Банк -5 000 ₽. Перевод Иван И. Остаток: 16 000 ₽; ••2548"
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(com.financeos.hub.core.database.entities.TransactionType.TRANSFER, tx!!.type)
        assertEquals(500_000L, tx.amountKopecks)
        assertEquals("2548", tx.cardMask)
        assertEquals(1_600_000L, tx.balanceKopecks)
        assertEquals(true, tx.outgoing)
    }

    @Test fun `parses push transfer with single decimal amount`() {
        val body = "Альфа-Банк -468,7 ₽. Перевод Остаток: 3 621,04 ₽; ••2548"
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(com.financeos.hub.core.database.entities.TransactionType.TRANSFER, tx!!.type)
        assertEquals(46_870L, tx.amountKopecks)
        assertEquals("2548", tx.cardMask)
        assertEquals(362_104L, tx.balanceKopecks)
    }

    @Test fun `parses push incoming transfer`() {
        val body = "Альфа-Банк +5 000 ₽. Перевод от Ивана И. Остаток: 21 000 ₽; ••2548"
        val tx = parser.parse("ALFABANK", body, ts)
        assertNotNull(tx)
        assertEquals(com.financeos.hub.core.database.entities.TransactionType.TRANSFER, tx!!.type)
        assertEquals(500_000L, tx.amountKopecks)
        assertEquals(false, tx.outgoing)
        assertEquals("2548", tx.cardMask)
        assertEquals(2_100_000L, tx.balanceKopecks)
    }
}
