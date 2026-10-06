package com.financeos.hub.core.invest

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.invest.BrokerPushParser.BKS
import com.financeos.hub.core.invest.DepositLinks.WalletLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Склейка пополнений: перевод кошелька брокеру ↔ зачисление у брокера (#53). */
class DepositLinksTest {

    private val hour = 3_600_000L
    private val day  = 24 * hour
    /** 1 октября: Альфа списала 10 000 в 07:53, БКС написал о пополнении в 09:53. */
    private val alfaAt = 1_790_830_380_000L
    private val bksAt  = alfaAt + 2 * hour
    private val later  = bksAt + 10 * day

    private fun leg(id: String, at: Long, kopecks: Long = -1_000_000L, broker: String = BKS, cur: String = "RUB") =
        WalletLeg(id, at, kopecks, cur, broker, "Альфа-Банк •• 1139")

    private fun deposit(at: Long, kopecks: Long = 1_000_000L, id: String = "ru.broker.my_$at") =
        BrokerCashMove(BKS, at, "580922/19-м", kopecks, "RUB", id = id)

    @Test
    fun `the real October 1 deposit is one event seen from two ends`() {
        val dep = deposit(bksAt)
        val r = DepositLinks.link(listOf(leg("tx1", alfaAt)), listOf(dep), now = later)
        assertEquals("tx1", r.sourceOf[dep]?.txId)
        assertTrue(r.unmatched.isEmpty())
    }

    @Test
    fun `two equal deposits a week apart are two pairs, nearest first`() {
        val d1 = deposit(bksAt)
        val d2 = deposit(bksAt + 7 * day)
        val r = DepositLinks.link(
            listOf(leg("tx2", alfaAt + 7 * day), leg("tx1", alfaAt)),
            listOf(d1, d2), now = later + 7 * day,
        )
        assertEquals("tx1", r.sourceOf[d1]?.txId)
        assertEquals("tx2", r.sourceOf[d2]?.txId)
        assertTrue(r.unmatched.isEmpty())
    }

    @Test
    fun `a transfer the broker never confirmed is offered, not recorded`() {
        val tracked = deposit(bksAt)
        val lost = leg("tx9", alfaAt + 3 * day, kopecks = -500_000L)
        // Пока пуш ещё может склеиться (окно — трое суток), запись не предлагается: иначе записанное
        // пополнение заняло бы пару, а пришедший следом пуш посчитался бы вторым.
        assertTrue(DepositLinks.link(listOf(lost), listOf(tracked), now = alfaAt + 5 * day).unmatched.isEmpty())
        val r = DepositLinks.link(listOf(lost), listOf(tracked), now = alfaAt + 7 * day)
        assertEquals(listOf(lost), r.unmatched)
        assertTrue(r.sourceOf.isEmpty())
    }

    @Test
    fun `an asset entered by hand with an old date does not widen what is offered`() {
        // Ручной актив «куплен в прошлом году» — портфель от этого не вёлся с прошлого года.
        val oldManual = BrokerCashMove(BKS, alfaAt - 365 * day, null, 50_000_00L, "RUB", id = "manual_a_0")
        val tracked = deposit(bksAt)
        val beforeTracking = leg("y", alfaAt - 100 * day, kopecks = -7L)
        assertTrue(DepositLinks.link(listOf(beforeTracking), listOf(oldManual, tracked), now = later).unmatched.isEmpty())
    }

    @Test
    fun `nothing is offered while the broker push may still come, before tracking, or once dismissed`() {
        val tracked = deposit(bksAt)
        // Моложе суток — пуш брокера ещё может прийти.
        val fresh = leg("fresh", later - hour, kopecks = -1L)
        // Задолго до первого события брокера — портфель тогда не вёлся.
        val old = leg("old", alfaAt - 30 * day, kopecks = -20_000L)
        val dismissed = leg("no", alfaAt + 2 * day, kopecks = -2L)
        val r = DepositLinks.link(listOf(fresh, old, dismissed), listOf(tracked), setOf("no"), now = later)
        assertTrue(r.unmatched.isEmpty())
        // Без своих событий брокера предлагать не с чем.
        assertTrue(DepositLinks.link(listOf(dismissed), emptyList(), now = later).unmatched.isEmpty())
    }

    @Test
    fun `amount, currency, broker and window must all agree`() {
        val dep = deposit(bksAt)
        listOf(
            leg("a", alfaAt, kopecks = -999_999L),
            leg("b", alfaAt, cur = "USD"),
            leg("c", alfaAt, broker = "Финам"),
            leg("d", bksAt + 4 * day),
            leg("e", alfaAt, kopecks = 1_000_000L),     // пришло от брокера — не пара пополнению
        ).forEach { l ->
            assertTrue(l.txId, DepositLinks.link(listOf(l), listOf(dep), now = later).sourceOf.isEmpty())
        }
    }

    @Test
    fun `a withdrawal pairs with money arriving in the wallet`() {
        val out = deposit(bksAt, kopecks = -300_000L)
        val r = DepositLinks.link(listOf(leg("in", bksAt + hour, kopecks = 300_000L)), listOf(out), now = later)
        assertEquals("in", r.sourceOf[out]?.txId)
    }

    @Test
    fun `a recorded deposit takes the only known account and the opposite sign`() {
        val l = leg("tx", alfaAt)
        val one = listOf(Portfolio.Contract(BKS, "580922/19-м", null))
        val dep = DepositLinks.depositFor(l, one)
        assertEquals(1_000_000L, dep.amountKopecks)
        assertEquals("580922/19-м", dep.contract)
        assertEquals(alfaAt, dep.timestamp)
        // Счетов несколько — какой, неизвестно; угаданный хуже пустого.
        assertNull(DepositLinks.depositFor(l, one + Portfolio.Contract(BKS, "3468071/25", "Облигации")).contract)
        // Записанное пополнение склеивается со своим переводом — предложение уходит.
        val r = DepositLinks.link(listOf(l), listOf(dep.copy(id = "manual_x_0"), deposit(bksAt, id = "t")), now = later)
        assertTrue(r.unmatched.isEmpty())
    }

    @Test
    fun `a wallet row becomes a leg only when it is a transfer to a known broker`() {
        fun tx(type: TransactionType, cat: String?, merchant: String?) = TransactionEntity(
            id = "t", accountId = null, categoryId = cat, type = type, source = TransactionSource.PUSH,
            amountKopecks = -1_000_000L, merchant = merchant, description = null, timestamp = alfaAt,
            smsId = null, sourceMask = "1139",
        )
        val l = DepositLinks.legOf(tx(TransactionType.TRANSFER, "cat_invest", "BKS Mir Investitsiy"), emptyList())!!
        assertEquals(BKS, l.broker)
        assertEquals("•• 1139", l.source)
        assertNull(DepositLinks.legOf(tx(TransactionType.EXPENSE, "cat_other", "BKS Mir Investitsiy"), emptyList()))
        assertNull(DepositLinks.legOf(tx(TransactionType.TRANSFER, "cat_invest", "Иван И."), emptyList()))
    }
}
