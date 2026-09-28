package com.financeos.hub.core.analytics

import com.financeos.hub.core.analytics.LifetimeStats.Entry
import com.financeos.hub.core.analytics.MonthOverMonth.Window
import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * «Месяц к месяцу»: каждый месяц против своего предыдущего, через границу года, полный с полным.
 */
class MonthOverMonthTest {

    private val zone  = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 28)

    private fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun spend(amount: Long, date: LocalDate, category: String? = "cat_food", currency: String = "RUB") =
        Entry(at(date), -amount, TransactionType.EXPENSE, currency, category, null)

    private fun earn(amount: Long, date: LocalDate, category: String? = "cat_salary") =
        Entry(at(date), amount, TransactionType.INCOME, "RUB", category, null)

    private fun compute(entries: List<Entry>, day: LocalDate = today) = MonthOverMonth.compute(entries, day, zone)

    @Test
    fun `january is compared with december of the previous year`() {
        // Через границу года — главное требование: январь сравнивается с декабрём, а не с пустотой.
        val r = compute(
            listOf(spend(300_00, LocalDate.of(2025, 12, 10)), spend(450_00, LocalDate.of(2026, 1, 15))),
            day = LocalDate.of(2026, 3, 5),
        )
        val jan = r.spent.first { it.month == YearMonth.of(2026, 1) }
        assertEquals(450_00L, jan.kopecks)
        assertEquals(300_00L, jan.previous)
        assertEquals(50, jan.deltaPercent)
    }

    @Test
    fun `the first bar of the window has its own previous month`() {
        // Окно «год» заканчивается сентябрём 2026 и начинается октябрём 2025 — а сравнивать
        // октябрь надо с сентябрём 2025, который за краем окна.
        val r = compute(listOf(spend(100_00, LocalDate.of(2025, 9, 5)), spend(200_00, LocalDate.of(2025, 10, 5))))
        val bars = r.spentIn(Window.YEAR)
        assertEquals(12, bars.size)
        assertEquals(YearMonth.of(2025, 10), bars.first().month)
        assertEquals(100_00L, bars.first().previous)
        assertEquals(YearMonth.of(2026, 9), bars.last().month)
    }

    @Test
    fun `six months is the tail of the year`() {
        val r = compute(emptyList())
        val six = r.spentIn(Window.SIX)
        assertEquals(6, six.size)
        assertEquals(r.spentIn(Window.YEAR).takeLast(6), six)
        assertEquals(YearMonth.of(2026, 4), six.first().month)
    }

    @Test
    fun `the unfinished month is compared in full and marked`() {
        // Решение пользователя: полный месяц с полным, а незаконченный — помечен, не пересчитан.
        val r = compute(listOf(spend(300_00, LocalDate.of(2026, 8, 20)), spend(100_00, LocalDate.of(2026, 9, 3))))
        val sep = r.spent.last()
        assertFalse(sep.isComplete)
        assertEquals(100_00L, sep.kopecks)
        assertEquals(300_00L, sep.previous)
        assertTrue(r.spent.dropLast(1).all { it.isComplete })
        assertEquals(28, r.daysPassed)
        assertEquals(30, r.daysInMonth)
    }

    @Test
    fun `an empty month stays a zero bar`() {
        // Выбросить месяц без трат значило бы сравнить июль с маем и выдать это за «месяц к месяцу».
        val r = compute(listOf(spend(100_00, LocalDate.of(2026, 5, 5)), spend(200_00, LocalDate.of(2026, 7, 5))))
        val jun = r.spent.first { it.month == YearMonth.of(2026, 6) }
        val jul = r.spent.first { it.month == YearMonth.of(2026, 7) }
        assertEquals(0L, jun.kopecks)
        assertEquals(100_00L, jun.previous)
        assertEquals(0L, jul.previous)
        assertNull("рост с нуля в процентах ничего не сообщает", jul.deltaPercent)
    }

    @Test
    fun `transfers and future dates do not count`() {
        val r = compute(listOf(
            spend(100_00, LocalDate.of(2026, 9, 1)),
            Entry(at(LocalDate.of(2026, 9, 2)), -50_000_00, TransactionType.TRANSFER, "RUB", null, null),
            spend(9_000_00, LocalDate.of(2026, 9, 30)),   // послезавтра — ошибка ввода
        ))
        assertEquals(100_00L, r.spent.last().kopecks)
    }

    @Test
    fun `income is compared separately`() {
        val r = compute(listOf(earn(100_000_00, LocalDate.of(2026, 7, 5)), earn(120_000_00, LocalDate.of(2026, 8, 5))))
        val aug = r.earned.first { it.month == YearMonth.of(2026, 8) }
        assertEquals(20, aug.deltaPercent)
        assertTrue(r.spent.all { it.kopecks == 0L })
    }

    @Test
    fun `a category that vanished still shows in the breakdown`() {
        // Ушедшая трата — тоже причина разницы: «Такси было 5 000, стало 0» обязано быть видно.
        val r = compute(listOf(
            spend(5_000_00, LocalDate.of(2026, 7, 5), category = "cat_transport"),
            spend(1_000_00, LocalDate.of(2026, 8, 5), category = "cat_food"),
        ))
        val aug = r.spent.first { it.month == YearMonth.of(2026, 8) }
        val taxi = aug.categories.first { it.categoryId == "cat_transport" }
        assertEquals(0L, taxi.current)
        assertEquals(5_000_00L, taxi.previous)
        assertEquals("cat_transport", aug.categories.first().categoryId)   // крупнейшее изменение первым
    }

    @Test
    fun `a history without roubles is compared in its own currency`() {
        val r = compute(listOf(
            spend(5_000_00, LocalDate.of(2026, 8, 5), currency = "KGS"),
            spend(6_000_00, LocalDate.of(2026, 9, 5), currency = "KGS"),
        ))
        assertEquals("KGS", r.currency)
        assertEquals(6_000_00L, r.spent.last().kopecks)
    }

    @Test
    fun `one stray rouble purchase does not switch a som history to roubles`() {
        // Замечание ревью: «рубль, если он есть» переключало весь график на рубли из-за одной
        // случайной покупки, и все настоящие траты в сомах пропадали с экрана без следа.
        val r = compute(listOf(
            spend(5_000_00, LocalDate.of(2026, 7, 5), currency = "KGS"),
            spend(6_000_00, LocalDate.of(2026, 8, 5), currency = "KGS"),
            spend(7_000_00, LocalDate.of(2026, 9, 5), currency = "KGS"),
            spend(100_00, LocalDate.of(2026, 9, 6), currency = "RUB"),
        ))
        assertEquals("KGS", r.currency)
    }

    @Test
    fun `an even split between currencies keeps the rouble`() {
        val r = compute(listOf(
            spend(5_000_00, LocalDate.of(2026, 9, 5), currency = "KGS"),
            spend(100_00, LocalDate.of(2026, 9, 6), currency = "RUB"),
        ))
        assertEquals("RUB", r.currency)
    }
}
