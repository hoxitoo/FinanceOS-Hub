package com.financeos.hub.core.credit

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Момент получения напоминания — точка отсчёта для зачёта погашений (инвариант #38). Если он
 * уезжает вперёд при каждой повторной доставке, уже оплаченное требование воскресает, и человек
 * видит ровно тот дефект, ради которого зачёт и делался.
 */
class CreditNoticeSeenAtTest {

    private val first = 1_700_000_000_000L
    private val later = first + 3L * 24 * 60 * 60 * 1000
    private val due   = 1_701_000_000_000L

    @Test
    fun `a repeated delivery of the same demand keeps the original moment`() {
        // SMS и пуш об одном напоминании, либо повтор по мере приближения срока.
        assertEquals(
            first,
            noticeSeenAt(
                previousAmountKopecks = 989_84L, previousDueAt = due, previousSeenAt = first,
                amountKopecks = 989_84L, dueAt = due, now = later,
            ),
        )
    }

    @Test
    fun `a new amount starts the count again`() {
        // Новый расчётный период: платежи за прошлый банк уже учёл в этой цифре.
        assertEquals(
            later,
            noticeSeenAt(
                previousAmountKopecks = 989_84L, previousDueAt = due, previousSeenAt = first,
                amountKopecks = 1_450_00L, dueAt = due, now = later,
            ),
        )
    }

    @Test
    fun `a new deadline starts the count again`() {
        assertEquals(
            later,
            noticeSeenAt(
                previousAmountKopecks = 989_84L, previousDueAt = due, previousSeenAt = first,
                amountKopecks = 989_84L, dueAt = due + 30L * 24 * 60 * 60 * 1000, now = later,
            ),
        )
    }

    @Test
    fun `the first ever notice is seen now`() {
        assertEquals(
            later,
            noticeSeenAt(
                previousAmountKopecks = null, previousDueAt = null, previousSeenAt = null,
                amountKopecks = 989_84L, dueAt = due, now = later,
            ),
        )
    }

    @Test
    fun `a matching demand with no recorded moment falls back to now`() {
        // Строка из копии, сделанной до того, как момент начали писать: отсчитывать не от чего.
        assertEquals(
            later,
            noticeSeenAt(
                previousAmountKopecks = 989_84L, previousDueAt = due, previousSeenAt = null,
                amountKopecks = 989_84L, dueAt = due, now = later,
            ),
        )
    }
}
