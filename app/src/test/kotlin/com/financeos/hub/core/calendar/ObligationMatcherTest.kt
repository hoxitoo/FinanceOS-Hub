package com.financeos.hub.core.calendar

import com.financeos.hub.core.database.entities.PaymentDirection
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionSource
import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Сопоставление обязательства с операцией.
 *
 * Ошибка здесь портит «Свободно» — единственное число, под которое человек принимает решение. При
 * этом ошибки несимметричны: заниженная сумма делает осторожнее, завышенная — беднее. Поэтому
 * тесты в первую очередь проверяют, что лишнего НЕ засчитывается.
 */
class ObligationMatcherTest {

    private val zone = ZoneId.of("UTC")
    private val due  = LocalDate.of(2026, 3, 20)

    private fun rent(
        amount   : Long = 35_000_00L,
        account  : String? = null,
        currency : String = "RUB",
        direction: PaymentDirection = PaymentDirection.OUT,
    ) = PlannedPaymentEntity(
        id            = "rent",
        title         = "Аренда",
        amountKopecks = amount,
        currency      = currency,
        direction     = direction,
        anchorDate    = due.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    private fun tx(
        amount  : Long,
        date    : LocalDate,
        account : String? = null,
        type    : TransactionType = TransactionType.EXPENSE,
        currency: String = "RUB",
        deleted : Boolean = false,
        id      : String = "t1",
    ) = TransactionEntity(
        id            = id,
        accountId     = account,
        categoryId    = null,
        type          = type,
        source        = TransactionSource.MANUAL,
        amountKopecks = amount,
        merchant      = null,
        description   = null,
        timestamp     = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        smsId         = null,
        currency      = currency,
        isDeleted     = deleted,
    )

    // ── Что должно закрывать ────────────────────────────────────────────────────

    @Test
    fun `an exact payment on the day closes the obligation`() {
        assertTrue(ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due), zone))
    }

    @Test
    fun `a few days early or late still closes it`() {
        assertTrue(ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due.minusDays(5)), zone))
        assertTrue(ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due.plusDays(7)), zone))
    }

    @Test
    fun `a small change in the amount is tolerated`() {
        // Тариф подняли на 10 % — это тот же платёж.
        assertTrue(ObligationMatcher.fits(rent(), due, tx(-38_500_00L, due), zone))
    }

    @Test
    fun `an income obligation is closed by income`() {
        val salary = rent(amount = 80_000_00L, direction = PaymentDirection.IN)
        assertTrue(
            ObligationMatcher.fits(salary, due, tx(80_000_00L, due, type = TransactionType.INCOME), zone)
        )
    }

    // ── Что закрывать НЕ должно ─────────────────────────────────────────────────

    @Test
    fun `a payment too far outside the window does not close it`() {
        assertFalse(ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due.minusDays(6)), zone))
        assertFalse(ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due.plusDays(8)), zone))
    }

    @Test
    fun `a wildly different amount does not close it`() {
        // Случайная покупка не должна «оплатить» аренду и подбросить «Свободно» на 35 000.
        assertFalse(ObligationMatcher.fits(rent(), due, tx(-2_340_00L, due), zone))
        assertFalse(ObligationMatcher.fits(rent(), due, tx(-60_000_00L, due), zone))
    }

    @Test
    fun `a transfer between own accounts never closes an obligation`() {
        // Перевод не тратит деньги, а перекладывает.
        assertFalse(
            ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due, type = TransactionType.TRANSFER), zone)
        )
    }

    @Test
    fun `income does not close an outgoing obligation`() {
        assertFalse(
            ObligationMatcher.fits(rent(), due, tx(35_000_00L, due, type = TransactionType.INCOME), zone)
        )
    }

    @Test
    fun `a different currency does not close it`() {
        assertFalse(
            ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due, currency = "USD"), zone)
        )
    }

    @Test
    fun `a wrong account does not close an obligation tied to one`() {
        val tied = rent(account = "acc-1")
        assertFalse(ObligationMatcher.fits(tied, due, tx(-35_000_00L, due, account = "acc-2"), zone))
        assertTrue(ObligationMatcher.fits(tied, due, tx(-35_000_00L, due, account = "acc-1"), zone))
    }

    @Test
    fun `a deleted transaction does not close anything`() {
        assertFalse(ObligationMatcher.fits(rent(), due, tx(-35_000_00L, due, deleted = true), zone))
    }

    @Test
    fun `a zero-amount obligation is never matched`() {
        assertFalse(ObligationMatcher.fits(rent(amount = 0L), due, tx(0L, due), zone))
    }

    // ── Один платёж — одно обязательство ────────────────────────────────────────

    @Test
    fun `one transaction cannot close two obligations`() {
        val a = rent().copy(id = "a")
        val b = rent().copy(id = "b")
        val one = tx(-35_000_00L, due)

        val matches = ObligationMatcher.match(
            payments     = listOf(a, b),
            dueDates     = mapOf("a" to due, "b" to due),
            transactions = listOf(one),
            zone         = zone,
        )
        assertEquals(1, matches.size)
    }

    @Test
    fun `an obligation tied to an account gets the transaction first`() {
        // У привязанного условие строже; отдать операцию более общему обязательству значило бы
        // оставить строгое незакрытым навсегда.
        val loose = rent().copy(id = "loose")
        val tied  = rent(account = "acc-1").copy(id = "tied")
        val one   = tx(-35_000_00L, due, account = "acc-1")

        val matches = ObligationMatcher.match(
            payments     = listOf(loose, tied),
            dueDates     = mapOf("loose" to due, "tied" to due),
            transactions = listOf(one),
            zone         = zone,
        )
        assertEquals(1, matches.size)
        assertEquals("tied", matches.single().payment.id)
    }

    @Test
    fun `an obligation without a due date in scope is skipped`() {
        val matches = ObligationMatcher.match(
            payments     = listOf(rent()),
            dueDates     = emptyMap(),
            transactions = listOf(tx(-35_000_00L, due)),
            zone         = zone,
        )
        assertTrue(matches.isEmpty())
    }
}
