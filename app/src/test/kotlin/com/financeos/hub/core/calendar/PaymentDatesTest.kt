package com.financeos.hub.core.calendar

import com.financeos.hub.core.database.entities.PaymentSchedule
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Даты обязательств. Главный тест здесь — про аренду 31-го числа: наивная реализация уводит её на
 * 28-е навсегда после первого февраля, и заметить это можно только через год.
 */
class PaymentDatesTest {

    private val zone = ZoneId.of("UTC")

    private fun payment(
        anchor  : LocalDate,
        schedule: PaymentSchedule = PaymentSchedule.MONTHLY,
        day     : Int? = null,
        // Обязательство не описывает время до своего появления, поэтому в тестах про даты оно
        // «рождается» вместе с якорем. Оставив здесь `now` по умолчанию, мы бы проверяли отсечку
        // по дате создания, а не расчёт повторений.
        bornOn  : LocalDate = anchor,
    ) = PlannedPaymentEntity(
        id            = "p1",
        title         = "Аренда",
        amountKopecks = 35_000_00L,
        schedule      = schedule,
        anchorDate    = anchor.atStartOfDay(zone).toInstant().toEpochMilli(),
        dayOfMonth    = day,
        createdAt     = bornOn.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    // ── Отсечка по дате появления ───────────────────────────────────────────────

    @Test
    fun `an obligation has no dates before it existed`() {
        // Подтвердив подписку сегодня, человек не просил следить за прошлым месяцем. Без отсечки
        // старые периоды всплывали как «ПРОСРОЧЕНО» — долг, которого нет.
        val p = payment(LocalDate.of(2026, 1, 15), bornOn = LocalDate.of(2026, 3, 10))
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), zone)
        assertEquals(listOf(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15)), dates)
    }

    // ── Та самая ловушка ────────────────────────────────────────────────────────

    @Test
    fun `the 31st survives February and comes back in March`() {
        val p = payment(LocalDate.of(2026, 1, 31), day = 31)
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1), zone)

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),   // подрезано длиной месяца
                LocalDate.of(2026, 3, 31),   // и ВЕРНУЛОСЬ на 31-е, а не осталось на 28-м
                LocalDate.of(2026, 4, 30),
            ),
            dates,
        )
    }

    @Test
    fun `the 29th of February in a leap year is not lost`() {
        val p = payment(LocalDate.of(2028, 1, 29), day = 29)
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1), zone)
        assertEquals(listOf(LocalDate.of(2028, 2, 29)), dates)
    }

    @Test
    fun `without an explicit day the anchor day is used`() {
        val p = payment(LocalDate.of(2026, 1, 15))
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20), zone)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15)),
            dates,
        )
    }

    // ── Остальные ритмы ─────────────────────────────────────────────────────────

    @Test
    fun `weekly steps by seven days and ignores the day of month`() {
        val p = payment(LocalDate.of(2026, 1, 29), PaymentSchedule.WEEKLY, day = 31)
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 20), zone)
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 5),
                LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 19),
            ),
            dates,
        )
    }

    @Test
    fun `quarterly steps by three months`() {
        val p = payment(LocalDate.of(2026, 1, 10), PaymentSchedule.QUARTERLY)
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), zone)
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 10, 10),
            ),
            dates,
        )
    }

    @Test
    fun `a one-off payment happens once or not at all`() {
        val p = payment(LocalDate.of(2026, 3, 5), PaymentSchedule.ONCE)
        assertEquals(
            listOf(LocalDate.of(2026, 3, 5)),
            PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), zone),
        )
        assertTrue(
            PaymentDates.occurrencesIn(p, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 12, 31), zone).isEmpty()
        )
    }

    // ── Границы окна ────────────────────────────────────────────────────────────

    @Test
    fun `the window is inclusive on both ends`() {
        val p = payment(LocalDate.of(2026, 1, 10))
        val dates = PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), zone)
        assertEquals(listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10)), dates)
    }

    @Test
    fun `an inverted window yields nothing instead of looping`() {
        val p = payment(LocalDate.of(2026, 1, 10))
        assertTrue(
            PaymentDates.occurrencesIn(p, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1), zone).isEmpty()
        )
    }

    @Test
    fun `a payment that starts later than the window is not shown early`() {
        val p = payment(LocalDate.of(2027, 1, 10))
        assertTrue(
            PaymentDates.occurrencesIn(p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), zone).isEmpty()
        )
    }

    // ── Ближайшая дата ──────────────────────────────────────────────────────────

    @Test
    fun `next occurrence skips everything already past`() {
        val p = payment(LocalDate.of(2026, 1, 20))
        assertEquals(
            LocalDate.of(2026, 5, 20),
            PaymentDates.nextOccurrence(p, LocalDate.of(2026, 5, 1), zone),
        )
    }

    @Test
    fun `next occurrence returns the day itself, not the one after`() {
        val p = payment(LocalDate.of(2026, 1, 20))
        assertEquals(
            LocalDate.of(2026, 3, 20),
            PaymentDates.nextOccurrence(p, LocalDate.of(2026, 3, 20), zone),
        )
    }

    @Test
    fun `a one-off payment in the past has no next occurrence`() {
        val p = payment(LocalDate.of(2026, 1, 5), PaymentSchedule.ONCE)
        assertNull(PaymentDates.nextOccurrence(p, LocalDate.of(2026, 2, 1), zone))
    }
}
