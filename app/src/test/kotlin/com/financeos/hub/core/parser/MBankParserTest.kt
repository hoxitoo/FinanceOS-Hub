package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.MBankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MBankParserTest {
    private val parser = MBankParser()
    private val ts = 1_718_700_000_000L

    @Test fun `parses USD purchase push`() {
        val body = "MBANK Покупка: 22 USD\nGOOGLE *Claude by Anth, 855-836-3987\nКарта: *6461\nДоступно: 11.96 USD"
        val tx = parser.parse("MBANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(2_200L, tx.amountKopecks)               // $22.00
        assertEquals("GOOGLE *Claude by Anth, 855-836-3987", tx.merchant)
        assertEquals("6461", tx.cardMask)
        assertEquals(1_196L, tx.balanceKopecks)              // $11.96 available
    }

    @Test fun `does not pick balance as amount`() {
        // The "Доступно" balance must never be read as the transaction amount.
        val body = "MBANK Покупка: 22 USD\nGOOGLE\nКарта: *6461\nДоступно: 11.96 USD"
        val tx = parser.parse("MBANK", body, ts)
        assertEquals(2_200L, tx!!.amountKopecks)
    }

    @Test fun `parses KGS amount with thousands separator`() {
        val body = "MBANK Покупка: 1 234,50 KGS\nFOOD CITY\nКарта: *6461\nДоступно: 500 KGS"
        val tx = parser.parse("MBANK", body, ts)
        assertNotNull(tx)
        assertEquals(123_450L, tx!!.amountKopecks)
        assertEquals("FOOD CITY", tx.merchant)
    }

    @Test fun `parses income`() {
        val body = "MBANK Пополнение: 100 USD\nperevod\nКарта: *6461\nДоступно: 111.96 USD"
        val tx = parser.parse("MBANK", body, ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(10_000L, tx.amountKopecks)
    }

    @Test fun `parses collapsed single-line push`() {
        // When only EXTRA_TEXT's first line arrives, still classify + read the amount.
        val tx = parser.parse("MBANK", "MBANK Покупка: 22 USD", ts)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(2_200L, tx.amountKopecks)
    }

    @Test fun `canHandle matches sender`() {
        assert(parser.canHandle("MBANK"))
        assert(parser.canHandle("МБАНК"))
        assert(!parser.canHandle("SBERBANK"))
    }

    @Test fun `does not parse promotional push`() {
        assertNull(parser.parse("MBANK", "Откройте вклад в MBANK под 18%!", ts))
    }
}
