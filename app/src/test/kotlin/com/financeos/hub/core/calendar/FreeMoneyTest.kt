package com.financeos.hub.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * «Свободно» — число, под которое человек принимает решение в магазине. Ошибка в большую сторону
 * дороже ошибки в меньшую, поэтому здесь проверяется прежде всего, что лишнее НЕ прибавляется.
 */
class FreeMoneyTest {

    private val today   = LocalDate.of(2026, 8, 30)
    private val horizon = LocalDate.of(2026, 9, 5)

    private fun event(
        date     : LocalDate,
        amount   : Long,
        direction: EventDirection = EventDirection.OUT,
        currency : String = "RUB",
        affects  : Boolean = true,
        settled  : Boolean = false,
        kind     : EventKind = EventKind.PLANNED,
    ) = CalendarEvent(
        id            = "e${date}_$amount",
        date          = date,
        title         = "событие",
        amountKopecks = amount,
        currency      = currency,
        direction     = direction,
        kind          = kind,
        confidence    = EventConfidence.DECLARED,
        affectsFree   = affects,
        settled       = settled,
    )

    private fun compute(events: List<CalendarEvent>, onAccounts: Long = 32_760_00L, reserve: Long = 12_000_00L) =
        FreeMoney.compute("RUB", onAccounts, events, reserve, today, horizon)

    // ── Арифметика, которую экран показывает построчно ──────────────────────────

    @Test
    fun `free money is accounts minus obligations minus reserve`() {
        val r = compute(
            listOf(
                event(LocalDate.of(2026, 8, 31), 3_500_00L),
                event(LocalDate.of(2026, 9, 3), 4_780_00L),
            )
        )
        assertEquals(8_280_00L, r.obligationsKopecks)
        assertEquals(32_760_00L - 8_280_00L - 12_000_00L, r.freeKopecks)
    }

    @Test
    fun `no obligations means accounts minus reserve`() {
        assertEquals(32_760_00L - 12_000_00L, compute(emptyList()).freeKopecks)
    }

    @Test
    fun `free money can go negative and is not clamped`() {
        // Показать «0 ₽» вместо «−5 000 ₽» значило бы скрыть ровно то, ради чего экран сделан.
        val r = compute(listOf(event(horizon, 30_000_00L)))
        assertEquals(32_760_00L - 30_000_00L - 12_000_00L, r.freeKopecks)
    }

    // ── Что НЕ вычитается и НЕ прибавляется ─────────────────────────────────────

    @Test
    fun `expected income is reported but never added`() {
        val r = compute(listOf(event(LocalDate.of(2026, 9, 4), 80_000_00L, EventDirection.IN)))
        assertEquals(80_000_00L, r.expectedIncomeKopecks)
        // Незаработанная зарплата не делает деньги тратимыми.
        assertEquals(32_760_00L - 12_000_00L, r.freeKopecks)
    }

    @Test
    fun `a deadline is not a payment`() {
        // Конец беспроцентного периода и срок цели дат не двигают денег.
        val r = compute(
            listOf(
                event(LocalDate.of(2026, 9, 1), 49_492_00L, affects = false, kind = EventKind.CREDIT_GRACE),
                event(LocalDate.of(2026, 9, 2), 140_000_00L, affects = false, kind = EventKind.GOAL),
            )
        )
        assertEquals(0L, r.obligationsKopecks)
    }

    @Test
    fun `an obligation already paid stops being subtracted`() {
        val r = compute(listOf(event(LocalDate.of(2026, 8, 31), 35_000_00L, settled = true)))
        assertEquals(0L, r.obligationsKopecks)
    }

    @Test
    fun `events beyond the horizon are out of scope`() {
        val r = compute(listOf(event(horizon.plusDays(1), 50_000_00L)))
        assertEquals(0L, r.obligationsKopecks)
    }

    @Test
    fun `an overdue obligation is still owed`() {
        // Дата прошла, а деньги никуда не делись — вычитаем.
        val r = compute(listOf(event(today.minusDays(3), 5_000_00L)))
        assertEquals(5_000_00L, r.obligationsKopecks)
    }

    // ── Валюты ──────────────────────────────────────────────────────────────────

    @Test
    fun `foreign obligations are kept apart, not converted and not dropped`() {
        // Курса у приложения нет. Молча выбросить долларовую подписку — завысить свободные деньги.
        val r = compute(
            listOf(
                event(LocalDate.of(2026, 9, 1), 3_500_00L),
                event(LocalDate.of(2026, 9, 2), 19_99L, currency = "USD"),
                event(LocalDate.of(2026, 9, 3), 22_40L, currency = "USD"),
            )
        )
        assertEquals(3_500_00L, r.obligationsKopecks)
        assertEquals(mapOf("USD" to 42_39L), r.foreignObligations)
    }

    // ── Дневной ориентир ────────────────────────────────────────────────────────

    @Test
    fun `daily allowance divides what is free by the days left, today included`() {
        val r = compute(emptyList())            // 30 августа → 5 сентября = 7 дней
        assertEquals(7, r.daysLeft)
        assertEquals((32_760_00L - 12_000_00L) / 7, r.dailyAllowanceKopecks)
    }

    @Test
    fun `there is no daily allowance when nothing is free`() {
        // «Тратьте по −400 ₽ в день» — не совет.
        val r = compute(listOf(event(horizon, 30_000_00L)))
        assertNull(r.dailyAllowanceKopecks)
    }

    @Test
    fun `a horizon in the past still leaves one day, never zero`() {
        val r = FreeMoney.compute("RUB", 10_000_00L, emptyList(), 0L, today, today.minusDays(5))
        assertEquals(1, r.daysLeft)
        assertEquals(10_000_00L, r.dailyAllowanceKopecks)
    }

    // ── Горизонт по умолчанию ───────────────────────────────────────────────────

    @Test
    fun `the horizon is the next expected income`() {
        val salary = event(LocalDate.of(2026, 9, 7), 80_000_00L, EventDirection.IN)
        val rent   = event(LocalDate.of(2026, 9, 1), 35_000_00L)
        assertEquals(
            LocalDate.of(2026, 9, 7),
            FreeMoney.defaultHorizon(listOf(rent, salary), today),
        )
    }

    @Test
    fun `an income already matched does not collapse the horizon`() {
        // Зарплату сопоставили за пару дней до срока. Горизонт, схлопнутый на её дату, выбросил бы
        // из расчёта весь остаток месяца — и «Свободно» показало бы завышенное число сразу после
        // получки, когда на счёте максимум и тратить хочется больше всего.
        val paidSalary = event(LocalDate.of(2026, 9, 7), 80_000_00L, EventDirection.IN, settled = true)
        assertEquals(
            LocalDate.of(2026, 8, 31),
            FreeMoney.defaultHorizon(listOf(paidSalary), today),
        )
    }

    @Test
    fun `the obligation count matches the sum it explains`() {
        // Считанные порознь, число и сумма разъезжаются: «учтено 2 платежа на 0 ₽».
        val result = FreeMoney.compute(
            currency          = "RUB",
            onAccountsKopecks = 100_000_00L,
            events            = listOf(
                event(LocalDate.of(2026, 9, 1), 35_000_00L),
                event(LocalDate.of(2026, 9, 2), 10_00L, currency = "USD"),
                event(LocalDate.of(2026, 9, 3), 5_000_00L, affects = false),
            ),
            reserveKopecks    = 0L,
            today             = today,
            horizon           = LocalDate.of(2026, 9, 30),
        )
        assertEquals(1, result.obligationCount)
        assertEquals(35_000_00L, result.obligationsKopecks)
    }

    @Test
    fun `without expected income the horizon is the end of the month`() {
        assertEquals(
            LocalDate.of(2026, 8, 31),
            FreeMoney.defaultHorizon(listOf(event(LocalDate.of(2026, 8, 31), 100_00L)), today),
        )
    }
}
