package com.financeos.hub.core.pdf

import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests against the real Alfa-Bank "Операции по счету" PDF layout, whose text
 * extraction wraps each row across several physical lines and embeds informational
 * inner amounts/dates inside the description column.
 */
class PdfTransactionParserTest {

    private fun dateOf(ts: Long): LocalDate =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Expense in the account column is negative ("-2 500,00 RUR") → EXPENSE, sign respected. */
    @Test fun `parses signed expense from account column`() {
        val text = """
            Операции по счету
            Дата проводки Код операции Описание Сумма в валюте счета
            19.12.2025 C2119122250932187 Платеж C2119122250932187 в ООО НКО "МОБИЛЬНАЯ КАРТА"
            через Систему быстрых платежей. -2 500,00 RUR
        """.trimIndent()

        val txs = PdfTransactionParser.parse(text)
        assertEquals(1, txs.size)
        val tx = txs.first()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(250_000L, tx.amountKopecks)
        assertEquals(LocalDate.of(2025, 12, 19), dateOf(tx.timestampMillis))
    }

    /** Incoming transfer is positive (no sign) → INCOME. */
    @Test fun `parses unsigned income as income`() {
        val text = """
            19.12.2025 B011912251577348 Внутрибанковский перевод между счетами, ЛОБАНОВ А. В. 2 500,00 RUR
        """.trimIndent()

        val txs = PdfTransactionParser.parse(text)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.INCOME, txs.first().type)
        assertEquals(250_000L, txs.first().amountKopecks)
    }

    /**
     * Card row: the inner "на сумму: 40.00 RUR" (dot) and inner date "19.12.25" (2-digit year)
     * must be ignored; the posting amount "-40,00 RUR" (comma) and posting date win.
     */
    @Test fun `card row ignores inner amount and inner date`() {
        val text = """
            19.12.2025 CRD_4ET0T0 Операция по карте: 458443++++++6687, на сумму: 40.00 RUR,
            дата совершения операции: 17.12.25, место совершения операции:
            38830843iRUPermiTRANSPORT PERM TPP MCC4131 -40,00 RUR
        """.trimIndent()

        val txs = PdfTransactionParser.parse(text)
        assertEquals(1, txs.size)
        val tx = txs.first()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(4_000L, tx.amountKopecks)                       // 40,00 — not 40.00 inner
        assertEquals(LocalDate.of(2025, 12, 19), dateOf(tx.timestampMillis)) // 2025, not 2-digit 25
        assertEquals("TRANSPORT PERM TPP", tx.merchant)
    }

    /** Decimal kopecks survive the comma → dot conversion. */
    @Test fun `parses kopecks precisely`() {
        val text = "19.12.2025 CRD_7A30XS Операция по карте: 220015++++++2548, на сумму: 324.90 RUR, " +
            "место совершения операции: 30433121iRUPermiBEREG4 MCC5411 -324,90 RUR"
        val tx = PdfTransactionParser.parse(text).single()
        assertEquals(32_490L, tx.amountKopecks)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("BEREG4", tx.merchant)
    }

    /** A multi-row statement keeps every row and assigns correct signs. */
    @Test fun `parses a full multi-row block`() {
        val text = """
            Операции по счету
            Дата проводки Код операции Описание Сумма в валюте счета
            19.12.2025 C2119122250932187 Платеж через Систему быстрых платежей. -2 500,00 RUR
            19.12.2025 B011912251577348 Внутрибанковский перевод, ЛОБАНОВ А. В. 2 500,00 RUR
            19.12.2025 C1719122250529254 Перевод через Систему быстрых платежей. 10 000,00 RUR
            19.12.2025 C1619122250343637 Платеж через Систему быстрых платежей. -1 000,00 RUR
            Страница 1 из 46
        """.trimIndent()

        val txs = PdfTransactionParser.parse(text)
        assertEquals(4, txs.size)
        assertEquals(TransactionType.EXPENSE, txs[0].type)
        assertEquals(250_000L, txs[0].amountKopecks)
        assertEquals(TransactionType.INCOME, txs[1].type)
        assertEquals(TransactionType.INCOME, txs[2].type)
        assertEquals(1_000_000L, txs[2].amountKopecks)
        assertEquals(TransactionType.EXPENSE, txs[3].type)
    }

    /** Operation codes are unique, so re-importing the same statement yields no duplicates. */
    @Test fun `dedups by operation code within one parse`() {
        val text = """
            19.12.2025 CRD_4ET0T0 Операция по карте, место совершения операции: 38830843iRUPermiSHOP MCC4131 -40,00 RUR
            19.12.2025 CRD_4ET0T0 Операция по карте, место совершения операции: 38830843iRUPermiSHOP MCC4131 -40,00 RUR
        """.trimIndent()

        assertEquals(1, PdfTransactionParser.parse(text).size)
    }

    @Test fun `returns empty for non-statement text`() {
        val text = "Это просто текст без транзакций.\nАО «АЛЬФА-БАНК»\nalfabank.ru"
        assertTrue(PdfTransactionParser.parse(text).isEmpty())
    }
}
