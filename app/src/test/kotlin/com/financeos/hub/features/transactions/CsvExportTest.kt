package com.financeos.hub.features.transactions

import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {

    @Test
    fun `transfers are exported as transfers with their direction`() {
        assertEquals("Расход", csvTypeLabel(TransactionType.EXPENSE, -100))
        assertEquals("Доход", csvTypeLabel(TransactionType.INCOME, 100))
        assertEquals("Перевод (исходящий)", csvTypeLabel(TransactionType.TRANSFER, -600_000))
        assertEquals("Перевод (входящий)", csvTypeLabel(TransactionType.TRANSFER, 120_000))
    }

    @Test
    fun `amounts are plain decimals, never scientific notation`() {
        assertEquals("15173685.90", csvAmount(-1_517_368_590L))
        assertEquals("1034.00", csvAmount(-103_400L))
        assertEquals("77.23", csvAmount(7_723L))
        assertEquals("0.05", csvAmount(5L))
    }
}
