package com.financeos.hub.core.analytics

import com.financeos.hub.core.analytics.LifetimeStats.Entry
import com.financeos.hub.core.analytics.LifetimeStats.Horizon
import com.financeos.hub.core.analytics.LifetimeStats.Step
import com.financeos.hub.core.database.entities.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * «Всего потрачено, всего заработано» — за годы. Экран сводит одну историю в пять разных видов
 * (итоги, кривые, бары, доли, источники), и все они обязаны сходиться между собой.
 */
class LifetimeStatsTest {

    private val zone  = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 26)

    private fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun spend(
        amount  : Long,
        date    : LocalDate,
        category: String? = "cat_food",
        party   : String? = null,
        currency: String = "RUB",
    ) = Entry(at(date), -amount, TransactionType.EXPENSE, currency, category, party)

    private fun earn(
        amount  : Long,
        date    : LocalDate,
        category: String? = "cat_salary",
        party   : String? = null,
        currency: String = "RUB",
    ) = Entry(at(date), amount, TransactionType.INCOME, currency, category, party)

    private fun transfer(amount: Long, date: LocalDate) =
        Entry(at(date), -amount, TransactionType.TRANSFER, "RUB", null, null)

    private fun compute(entries: List<Entry>, horizon: Horizon = Horizon.ALL, step: Step = Step.MONTH) =
        LifetimeStats.compute(entries, horizon, step, today, zone)

    // ── Итоги ───────────────────────────────────────────────────────────────────

    @Test
    fun `transfers are neither spent nor earned`() {
        // Пополнение копилки — перекладывание, а не трата. Посчитай его — «потрачено» выросло бы
        // на каждый перевод на накопительный счёт.
        val r = compute(listOf(
            spend(1_000_00, today),
            earn(5_000_00, today),
            transfer(50_000_00, today),
        ))
        val rub = r.totals.single()
        assertEquals(1_000_00L, rub.spent)
        assertEquals(5_000_00L, rub.earned)
        assertEquals(4_000_00L, rub.net)
    }

    @Test
    fun `currencies are never added together`() {
        val r = compute(listOf(
            spend(1_000_00, today),
            spend(20_00, today, currency = "USD"),
        ))
        assertEquals(listOf("RUB", "USD"), r.totals.map { it.currency })
        assertEquals(1_000_00L, r.totals.first().spent)
        // Графики и доли — в рублях: долларовая строка не должна попасть в рублёвую сумму.
        assertEquals(1_000_00L, r.spentByCategory.sumOf { it.kopecks })
    }

    @Test
    fun `the rouble leads even when another currency has a larger turnover`() {
        val r = compute(listOf(
            spend(10_00, today),
            spend(5_000_00, today, currency = "KGS"),
        ))
        assertEquals("RUB", r.totals.first().currency)
    }

    // ── Окно ────────────────────────────────────────────────────────────────────

    @Test
    fun `a year means the last twelve months`() {
        val r = compute(
            listOf(
                spend(1_00, today.minusYears(1).plusDays(1)),   // внутри
                spend(2_00, today.minusYears(1)),               // ровно год назад — уже вне
                spend(4_00, today.minusYears(3)),
            ),
            horizon = Horizon.YEAR,
        )
        assertEquals(1_00L, r.totals.single().spent)
    }

    @Test
    fun `all time takes everything`() {
        val r = compute(listOf(spend(1_00, today.minusYears(15)), spend(2_00, today)))
        assertEquals(3_00L, r.totals.single().spent)
        assertEquals(today.minusYears(15), r.firstDate)
    }

    @Test
    fun `a future-dated row is an input error, not a forecast`() {
        val r = compute(listOf(spend(1_00, today), spend(9_00, today.plusDays(3))))
        assertEquals(1_00L, r.totals.single().spent)
    }

    @Test
    fun `nothing in the window gives an empty result`() {
        val r = compute(listOf(spend(1_00, today.minusYears(5))), horizon = Horizon.YEAR)
        assertTrue(r.isEmpty)
        assertNull(r.firstDate)
        assertTrue(r.curve.isEmpty())
    }

    // ── Нарастающий итог ────────────────────────────────────────────────────────

    @Test
    fun `the curve accumulates and keeps empty months`() {
        // Месяц без операций — горизонтальный участок. Выбросить его значило бы сжать время и
        // показать рост круче, чем он был.
        val r = compute(listOf(
            spend(100_00, LocalDate.of(2026, 7, 10)),
            spend(50_00, LocalDate.of(2026, 9, 5)),
            earn(300_00, LocalDate.of(2026, 7, 1)),
        ))
        assertEquals(
            listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)),
            r.curve.map { it.periodStart },
        )
        assertEquals(listOf(100_00L, 100_00L, 150_00L), r.curve.map { it.spent })
        assertEquals(listOf(300_00L, 300_00L, 300_00L), r.curve.map { it.earned })
    }

    @Test
    fun `the last point equals the totals`() {
        // Кривая и цифры над ней — один и тот же ответ. Разойдись они — человек перестал бы
        // верить обоим.
        val entries = listOf(
            spend(123_00, LocalDate.of(2020, 3, 1)),
            spend(77_00, LocalDate.of(2025, 11, 1)),
            earn(1_000_00, LocalDate.of(2023, 6, 1)),
        )
        Step.entries.forEach { step ->
            val r = compute(entries, step = step)
            assertEquals(r.totals.single().spent, r.curve.last().spent)
            assertEquals(r.totals.single().earned, r.curve.last().earned)
        }
    }

    @Test
    fun `buckets align to the calendar`() {
        assertEquals(LocalDate.of(2026, 7, 1), LifetimeStats.bucketStart(LocalDate.of(2026, 9, 26), Step.HALF_YEAR))
        assertEquals(LocalDate.of(2026, 1, 1), LifetimeStats.bucketStart(LocalDate.of(2026, 6, 30), Step.HALF_YEAR))
        assertEquals(LocalDate.of(2026, 1, 1), LifetimeStats.bucketStart(LocalDate.of(2026, 9, 26), Step.YEAR))
        assertEquals(LocalDate.of(2026, 1, 1), LifetimeStats.bucketStart(LocalDate.of(2027, 5, 1), Step.TWO_YEARS))
        assertEquals(LocalDate.of(2026, 9, 1), LifetimeStats.bucketStart(LocalDate.of(2026, 9, 26), Step.MONTH))
    }

    // ── Годы и доли ─────────────────────────────────────────────────────────────

    @Test
    fun `a year bar is made of its categories`() {
        val r = compute(listOf(
            spend(300_00, LocalDate.of(2025, 2, 1), category = "cat_food"),
            spend(100_00, LocalDate.of(2025, 5, 1), category = "cat_transport"),
            spend(200_00, LocalDate.of(2025, 8, 1), category = "cat_food"),
            spend(50_00, LocalDate.of(2026, 1, 1), category = "cat_food"),
        ))
        assertEquals(listOf(2025, 2026), r.spentByYear.map { it.year })
        val y2025 = r.spentByYear.first()
        assertEquals(600_00L, y2025.total)
        assertEquals(listOf("cat_food", "cat_transport"), y2025.segments.map { it.categoryId })
        assertEquals(y2025.total, y2025.segments.sumOf { it.kopecks })
    }

    @Test
    fun `income has its own bars and shares`() {
        val r = compute(listOf(
            earn(100_00, LocalDate.of(2025, 1, 1), category = "cat_salary"),
            earn(30_00, LocalDate.of(2025, 2, 1), category = "cat_cashback"),
            spend(10_00, LocalDate.of(2025, 2, 1)),
        ))
        assertEquals(130_00L, r.earnedByYear.single().total)
        assertEquals(listOf("cat_salary", "cat_cashback"), r.earnedByCategory.map { it.categoryId })
        assertEquals(10_00L, r.spentByCategory.sumOf { it.kopecks })
    }

    // ── Источники ───────────────────────────────────────────────────────────────

    @Test
    fun `one merchant under different bank strings is one source`() {
        // «RECR GOOGLE *ChatGPT, 855-…» и «ChatGPT» — один получатель; в списке «кому ушли деньги»
        // он обязан быть одной строкой, а не двумя с половиной суммы каждая.
        val r = compute(listOf(
            spend(20_00, LocalDate.of(2026, 7, 1), party = "RECR GOOGLE *ChatGPT, 855-836-3987"),
            spend(20_00, LocalDate.of(2026, 8, 1), party = "ChatGPT"),
            spend(5_00, LocalDate.of(2026, 8, 2), party = "Пятёрочка"),
        ))
        val top = r.spentSources.first()
        assertEquals("ChatGPT", top.label)
        assertEquals(40_00L, top.kopecks)
        assertEquals(2, top.count)
        assertEquals(2, r.spentSources.size)
    }

    @Test
    fun `an unnamed income falls back to its category`() {
        // Зарплата приходит «Зачислением» без имени — источником служит категория.
        val r = compute(listOf(
            earn(100_00, LocalDate.of(2026, 7, 1), party = null, category = "cat_salary"),
            earn(100_00, LocalDate.of(2026, 8, 1), party = null, category = "cat_salary"),
        ))
        val src = r.earnedSources.single()
        assertNull(src.label)
        assertEquals("cat_salary", src.categoryId)
        assertEquals(200_00L, src.kopecks)
    }

    @Test
    fun `sources are ordered by amount`() {
        val r = compute(listOf(
            spend(5_00, today, party = "Кофейня"),
            spend(500_00, today, party = "Аренда"),
            spend(50_00, today, party = "Пятёрочка"),
        ))
        assertEquals(listOf("Аренда", "Пятёрочка", "Кофейня"), r.spentSources.map { it.label })
    }

    // ── Замечания ревью ─────────────────────────────────────────────────────────

    @Test
    fun `the tile and the screen agree on future-dated rows`() {
        // Трата, по ошибке введённая следующим месяцем: экран «Всё время» её отбрасывает, и плитка,
        // которая этот экран открывает, обязана показать ту же цифру.
        val entries = listOf(spend(1_000_00, today), spend(50_000_00, today.plusMonths(1)))
        val tile   = LifetimeStats.lifetimeTotals(entries, today, zone).single()
        val screen = compute(entries).totals.single()
        assertEquals(screen.spent, tile.spent)
        assertEquals(1_000_00L, tile.spent)
    }

    @Test
    fun `a history without roubles is charted in its own currency`() {
        // МБанк: одни сомы. Жёсткий рубль дал бы плитку «0 ₽» и экран без единого графика.
        val r = compute(listOf(
            spend(5_000_00, LocalDate.of(2026, 8, 1), currency = "KGS"),
            spend(20_00, LocalDate.of(2026, 8, 2), currency = "USD"),
        ))
        assertEquals("KGS", r.currency)
        assertEquals(5_000_00L, r.spentByCategory.sumOf { it.kopecks })
        assertTrue(r.curve.isNotEmpty())
    }

    @Test
    fun `the first date is where the charts start`() {
        // Долларовая подписка 2019 года не должна сдвигать «с какого числа» у рублёвых графиков.
        val r = compute(listOf(
            spend(20_00, LocalDate.of(2019, 3, 3), currency = "USD"),
            spend(1_000_00, LocalDate.of(2021, 1, 10)),
        ))
        assertEquals(LocalDate.of(2021, 1, 10), r.firstDate)
        assertEquals(LocalDate.of(2021, 1, 1), r.curve.first().periodStart)
    }

    @Test
    fun `an uncategorised expense is Other, as on the categories tab`() {
        // Иначе одни и те же деньги лежали бы в двух долях: «Без категории» здесь и «Другое» там.
        val row = com.financeos.hub.core.database.entities.TransactionEntity(
            id = "t", accountId = null, categoryId = null, type = TransactionType.EXPENSE,
            source = com.financeos.hub.core.database.entities.TransactionSource.PUSH,
            amountKopecks = -100_00, merchant = null, description = null,
            timestamp = at(today), smsId = null,
        )
        val entry = LifetimeStats.entriesOf(listOf(row)).single()
        assertEquals(LifetimeStats.OTHER_CATEGORY, entry.categoryId)
    }

    @Test
    fun `changing the step reuses the same base`() {
        // Шаг пересчитывает только кривую; итог кривой на любом шаге — те же итоги.
        val entries = listOf(
            spend(123_00, LocalDate.of(2020, 3, 1)),
            earn(1_000_00, LocalDate.of(2024, 6, 1)),
        )
        val base = LifetimeStats.computeBase(entries, Horizon.ALL, today, zone)
        Step.entries.forEach { step ->
            val curve = LifetimeStats.curveOf(base, step)
            assertEquals(base.result.totals.single().spent, curve.last().spent)
            assertEquals(compute(entries, step = step).curve, curve)
        }
    }
}
