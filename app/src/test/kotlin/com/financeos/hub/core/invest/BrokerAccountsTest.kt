package com.financeos.hub.core.invest

import com.financeos.hub.core.invest.BrokerPushParser.BKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Счета брокера, переводы между ними и предупреждения о низком балансе (инвариант #47).
 * Последовательность — реальные пуши БКС от 1–2 октября.
 */
class BrokerAccountsTest {

    private fun at(min: Int) = 1_759_300_000_000L + min * 60_000L

    private val deposit  = BrokerCashMove(BKS, at(0), "580922/19-м", 1_000_000L, "RUB")
    private fun alert(min: Int, required: Long = 18_802L, dismissed: Boolean = false, contract: String = "3468071/25") =
        BrokerMarginAlert(BKS, at(min), contract, "Облигации", required, id = "a$min", dismissed = dismissed)
    private fun transfer(min: Int, amount: Long = 18_900L) =
        BrokerInternalTransfer(BKS, at(min), amount, "RUB", "580922/19-м", "3468071/25", null, "Облигации")

    @Test
    fun `a transfer between accounts does not change the money at the broker`() {
        val before = Portfolio.compute(listOf(deposit))
        val after  = Portfolio.compute(listOf(deposit, transfer(10)))
        assertEquals(before.summaries.single().totalKopecks, after.summaries.single().totalKopecks)
        assertEquals(1_000_000L, after.accounts.single().cashKopecks)
    }

    @Test
    fun `accounts are learned from pushes with their names`() {
        val r = Portfolio.compute(listOf(deposit, alert(5), transfer(6)))
        assertEquals(listOf("580922/19-м", "3468071/25 (Облигации)"), r.contracts.map { it.title })
        // Счетов два — в карточке брокера номер одного из них был бы неправдой.
        assertNull(r.accounts.single().contract)
        assertEquals(2, r.movements.size)
    }

    @Test
    fun `the real alert is covered by the transfer that followed it`() {
        val st = MarginAlerts.evaluate(listOf(deposit, alert(5), transfer(6))).single()
        assertTrue(st.isCovered)
        assertFalse(st.isOpen)
        assertEquals(0L, st.remainingKopecks)
    }

    @Test
    fun `money that came before the alert does not cover it`() {
        val st = MarginAlerts.evaluate(listOf(transfer(4), alert(5))).single()
        assertTrue(st.isOpen)
        assertEquals(18_802L, st.remainingKopecks)
    }

    @Test
    fun `a partial top-up leaves the rest open`() {
        val st = MarginAlerts.evaluate(listOf(alert(5), transfer(6, amount = 10_000L))).single()
        assertTrue(st.isOpen)
        assertEquals(8_802L, st.remainingKopecks)
    }

    @Test
    fun `money to another account does not cover it`() {
        val other = BrokerInternalTransfer(BKS, at(6), 50_000L, "RUB", "3468071/25", "580922/19-м")
        assertTrue(MarginAlerts.evaluate(listOf(alert(5), other)).single().isOpen)
    }

    @Test
    fun `a repeated alert is one card`() {
        val states = MarginAlerts.evaluate(listOf(alert(5), alert(7)))
        assertEquals(1, states.size)
        assertTrue(states.single().isOpen)
    }

    @Test
    fun `a repeat after a manual close opens the card again`() {
        // Брокер повторяет требование — значит, оно не оплачено; закрытие вручную его не отменяет.
        val states = MarginAlerts.evaluate(listOf(alert(5, dismissed = true), alert(600)))
        assertEquals(1, states.count { it.isOpen })
        assertEquals("a600", states.first { it.isOpen }.alert.id)
    }

    @Test
    fun `a newer alert with another amount replaces the older`() {
        val states = MarginAlerts.evaluate(listOf(alert(5), alert(60, required = 50_000L)))
        assertEquals(1, states.count { it.isOpen })
        assertEquals(50_000L, states.first { it.isOpen }.alert.requiredKopecks)
    }

    @Test
    fun `account numbers compare regardless of the number sign and case`() {
        assertEquals(contractKey("№580922/19-М"), contractKey("580922/19-м "))
        assertNull(contractKey("  "))
    }
}
