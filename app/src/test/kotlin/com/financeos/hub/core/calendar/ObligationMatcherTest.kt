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
        accountId     = account,
        // Обязательство существует с момента якоря: иначе сработала бы отсечка «нет дат до
        // появления», и тесты проверяли бы её, а не правила сопоставления.
        createdAt     = due.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    private fun tx(
        amount  : Long,
        date    : LocalDate,
        account : String? = null,
        type    : TransactionType = TransactionType.EXPENSE,
        currency: String = "RUB",
        deleted : Boolean = false,
        id      : String = "t1",
        category: String? = null,
    ) = TransactionEntity(
        id            = id,
        accountId     = account,
        categoryId    = category,
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
    fun `the transaction nearest the due date wins, not the newest`() {
        // Список приходит по убыванию времени. Взяв первую подходящую, недельное обязательство
        // закрывалось бы платежом СЛЕДУЮЩЕЙ недели, а свой собственный период после этого не
        // закрылся бы уже никогда.
        val later   = tx(-35_000_00L, due.plusDays(6), id = "later")
        val onTime  = tx(-35_000_00L, due,             id = "onTime")
        val matches = ObligationMatcher.match(
            payments     = listOf(rent()),
            dueDates     = mapOf("rent" to due),
            transactions = listOf(later, onTime),
            zone         = zone,
        )
        assertEquals("onTime", matches.single().primary.id)
    }

    @Test
    fun `a rejected transaction is never matched again`() {
        // Иначе «Отвязать» — кнопка, после которой всё возвращается на место: сборщик находит ту же
        // операцию на следующем же проходе.
        val one     = tx(-35_000_00L, due, id = "t1")
        val matches = ObligationMatcher.match(
            payments     = listOf(rent().copy(rejectedTxId = "t1")),
            dueDates     = mapOf("rent" to due),
            transactions = listOf(one),
            zone         = zone,
        )
        assertTrue(matches.isEmpty())
    }

    // ── Какие даты вообще предлагаются к закрытию ────────────────────────────────

    @Test
    fun `the oldest unsettled occurrence comes first, not the nearest future one`() {
        // Аренду не платили два месяца. Взяв ближайшую будущую дату, отметка перепрыгнула бы через
        // неоплаченные месяцы, и «Свободно» перестало бы вычитать реальный долг.
        val today = LocalDate.of(2026, 5, 21)
        val dues  = ObligationMatcher.openDueDates(listOf(rent()), today, zone, lookbackDays = 90)
        assertEquals(LocalDate.of(2026, 3, 20), dues["rent"])
    }

    @Test
    fun `a settled period is not offered again`() {
        val today   = LocalDate.of(2026, 5, 21)
        val settled = rent().copy(
            matchedThrough = LocalDate.of(2026, 4, 20).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val dues = ObligationMatcher.openDueDates(listOf(settled), today, zone, lookbackDays = 90)
        assertEquals(LocalDate.of(2026, 5, 20), dues["rent"])
    }

    @Test
    fun `nothing is offered while the date is still far ahead`() {
        // 20 мая ещё далеко: списать раньше срока можно, но не на месяц вперёд. Предложив эту дату,
        // мы позволили бы случайной покупке закрыть будущий месяц.
        val today = LocalDate.of(2026, 4, 25)
        val paid  = rent().copy(
            matchedThrough = LocalDate.of(2026, 4, 20).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        assertTrue(ObligationMatcher.openDueDates(listOf(paid), today, zone).isEmpty())
    }

    @Test
    fun `the search does not reach back past the lookback window`() {
        // Обязательство не сопоставлялось три года. Без ограничения оно предложило бы дату 2026
        // года, и «закрывать» пришлось бы месяц за месяцем всю историю. Берём только то, что
        // человек ещё может узнать: последние 60 дней.
        val today = LocalDate.of(2029, 3, 20)
        val dues  = ObligationMatcher.openDueDates(listOf(rent()), today, zone, lookbackDays = 60)
        assertEquals(LocalDate.of(2029, 1, 20), dues["rent"])
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

    // ── Счёт, оплаченный по частям ──────────────────────────────────────────────

    @Test
    fun `two payments on one day close a bill neither of them covers alone`() {
        // Реальный случай: объявлено «телефон и интернет 2 000», в тот же день ушли 550 и 1 500.
        // По отдельности ни одна не похожа на счёт, вместе — это он и есть. Раньше обязательство
        // висело просроченным, хотя деньги заплачены.
        val bill = rent(amount = 2_000_00L)
        val matches = ObligationMatcher.match(
            payments     = listOf(bill),
            dueDates     = mapOf(bill.id to due),
            transactions = listOf(
                tx(-550_00L,   due, id = "phone"),
                tx(-1_500_00L, due, id = "internet"),
            ),
            zone = zone,
        )
        assertEquals(1, matches.size)
        assertEquals(setOf("phone", "internet"), matches.single().transactions.map { it.id }.toSet())
        // Основной считается самая крупная часть — её показывает карточка события.
        assertEquals("internet", matches.single().primary.id)
    }

    @Test
    fun `parts spread across different days are not one bill`() {
        // Счёт оплачивают за один присест. Разрешив складывать траты всей недели, мы получили бы
        // ту самую жадность: любые две покупки рано или поздно сложатся в нужную сумму.
        val bill = rent(amount = 2_000_00L)
        val matches = ObligationMatcher.match(
            payments     = listOf(bill),
            dueDates     = mapOf(bill.id to due),
            transactions = listOf(
                tx(-550_00L,   due,              id = "phone"),
                tx(-1_500_00L, due.plusDays(2),  id = "internet"),
            ),
            zone = zone,
        )
        assertTrue("разные дни — это не один счёт", matches.isEmpty())
    }

    @Test
    fun `crumbs cannot top a sum up to the target`() {
        // 1 600 + 300 + 100 = ровно 2 000, но две последние части — мелочь. Без порога доли любая
        // сумма добирается случайными покупками дня, и счёт «закрывается» чем попало.
        // 1 600 в одиночку в допуск ±15 % не укладывается, так что остаться должно НИЧЕГО.
        val bill = rent(amount = 2_000_00L)
        val matches = ObligationMatcher.match(
            payments     = listOf(bill),
            dueDates     = mapOf(bill.id to due),
            transactions = listOf(
                tx(-1_600_00L, due, id = "big"),
                tx(-300_00L,   due, id = "crumb1"),
                tx(-100_00L,   due, id = "crumb2"),
            ),
            zone = zone,
        )
        assertTrue("мелочь не должна добивать сумму", matches.isEmpty())
    }

    @Test
    fun `parts with conflicting categories are two different expenses`() {
        val bill = rent(amount = 2_000_00L)
        val matches = ObligationMatcher.match(
            payments     = listOf(bill),
            dueDates     = mapOf(bill.id to due),
            transactions = listOf(
                tx(-550_00L,   due, id = "food",  category = "cat_food"),
                tx(-1_500_00L, due, id = "shoes", category = "cat_shopping"),
            ),
            zone = zone,
        )
        assertTrue("две разные категории — это две траты, а не счёт", matches.isEmpty())
    }

    @Test
    fun `the obligation's own category rules the parts out`() {
        val bill = rent(amount = 2_000_00L).copy(categoryId = "cat_telecom")
        val matches = ObligationMatcher.match(
            payments     = listOf(bill),
            dueDates     = mapOf(bill.id to due),
            transactions = listOf(
                tx(-550_00L,   due, id = "a", category = "cat_food"),
                tx(-1_500_00L, due, id = "b", category = "cat_food"),
            ),
            zone = zone,
        )
        assertTrue("обязательство объявлено в другой категории", matches.isEmpty())
    }

    @Test
    fun `a single exact payment wins over any combination`() {
        // Если счёт закрывается одной операцией, складывать соседние незачем.
        val bill = rent(amount = 2_000_00L)
        val matches = ObligationMatcher.match(
            payments     = listOf(bill),
            dueDates     = mapOf(bill.id to due),
            transactions = listOf(
                tx(-2_000_00L, due, id = "exact"),
                tx(-900_00L,   due, id = "half1"),
                tx(-1_100_00L, due, id = "half2"),
            ),
            zone = zone,
        )
        assertEquals(listOf("exact"), matches.single().transactions.map { it.id })
    }

    @Test
    fun `a part used by one obligation cannot close another`() {
        // Иначе один платёж закрыл бы два счёта сразу, и «Свободно» выросло бы вдвое.
        val first  = rent(amount = 2_000_00L)
        val second = rent(amount = 2_000_00L).copy(id = "rent2")
        val matches = ObligationMatcher.match(
            payments     = listOf(first, second),
            dueDates     = mapOf(first.id to due, second.id to due),
            transactions = listOf(
                tx(-550_00L,   due, id = "phone"),
                tx(-1_500_00L, due, id = "internet"),
            ),
            zone = zone,
        )
        assertEquals("обе части ушли первому обязательству", 1, matches.size)
    }
}
