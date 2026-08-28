package com.financeos.hub.core.analytics

import com.financeos.hub.core.analytics.SubscriptionDetector.Charge
import com.financeos.hub.core.analytics.SubscriptionDetector.Evidence
import com.financeos.hub.core.analytics.SubscriptionDetector.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Экран подписок раньше показывал «Продукты», «Покупки» и «Букмекер» как регулярные расходы и не
 * показывал настоящую подписку на ChatGPT. Оба случая закреплены здесь тестами: это не абстрактные
 * проверки эвристики, а ровно то, что человек увидел на своём телефоне.
 */
class SubscriptionDetectorTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L      // произвольная «сейчас»

    private fun charge(
        daysAgo : Long,
        amount  : Long,
        merchant: String? = "Netflix",
        currency: String  = "RUB",
        category: String? = null,
    ) = Charge(
        timestamp     = now - daysAgo * day,
        amountKopecks = amount,
        currency      = currency,
        merchant      = merchant,
        description   = null,
        categoryId    = category,
    )

    // ── То, что раньше попадало и не должно ──────────────────────────────────────

    @Test
    fun `groceries are not a subscription`() {
        // Разные магазины, разные суммы, много раз в месяц — то, что раньше показывалось как
        // «Продукты ~7 050 ₽ в месяц».
        val charges = listOf(
            charge(2,  114_99, "Пятёрочка"),
            charge(5,  892_40, "Магнит"),
            charge(9,  231_00, "Пятёрочка"),
            charge(14, 1_540_10, "Лента"),
            charge(21, 76_50, "Пятёрочка"),
            charge(33, 2_010_00, "Магнит"),
        )
        assertTrue(SubscriptionDetector.detect(charges, now).isEmpty())
    }

    @Test
    fun `same merchant with wildly different amounts is not a subscription`() {
        // Букмекер: продавец один, ритм даже похож на месячный, но суммы гуляют в разы.
        val charges = listOf(
            charge(5,  3_300_00, "FONBET"),
            charge(35, 24_000_00, "FONBET"),
            charge(66, 800_00, "FONBET"),
        )
        assertTrue(SubscriptionDetector.detect(charges, now).isEmpty())
    }

    @Test
    fun `two charges alone are not enough to claim a rhythm`() {
        val charges = listOf(charge(3, 599_00), charge(33, 599_00))
        assertTrue(SubscriptionDetector.detect(charges, now).isEmpty())
    }

    // ── То, что должно попадать ──────────────────────────────────────────────────

    @Test
    fun `three even monthly charges are a subscription`() {
        val charges = listOf(charge(4, 599_00), charge(34, 599_00), charge(64, 599_00))
        val subs = SubscriptionDetector.detect(charges, now)

        assertEquals(1, subs.size)
        val sub = subs.single()
        assertEquals("Netflix", sub.title)
        assertEquals(Period.Monthly, sub.period)
        assertEquals(Evidence.Regular, sub.evidence)
        assertEquals(599_00L, sub.typicalKopecks)
        assertEquals(599_00L, sub.monthlyKopecks)
        assertEquals(3, sub.chargeCount)
        assertNotNull(sub.nextExpectedAt)
    }

    @Test
    fun `a price rise inside the tolerance keeps the subscription together`() {
        val charges = listOf(charge(4, 699_00), charge(34, 599_00), charge(64, 599_00))
        assertEquals(1, SubscriptionDetector.detect(charges, now).size)
    }

    @Test
    fun `a yearly charge is normalised to a month`() {
        val charges = listOf(
            charge(10,  6_000_00, "JetBrains"),
            charge(375, 6_000_00, "JetBrains"),
            charge(740, 6_000_00, "JetBrains"),
        )
        val sub = SubscriptionDetector.detect(charges, now).single()
        assertEquals(Period.Yearly, sub.period)
        assertEquals(6_000_00L, sub.typicalKopecks)
        assertEquals(500_00L, sub.monthlyKopecks)     // 6000 / 12
    }

    @Test
    fun `a weekly charge is scaled up, not down`() {
        val charges = listOf(charge(1, 100_00, "Кофейня"), charge(8, 100_00, "Кофейня"), charge(15, 100_00, "Кофейня"))
        val sub = SubscriptionDetector.detect(charges, now).single()
        assertEquals(Period.Weekly, sub.period)
        assertTrue(sub.monthlyKopecks > sub.typicalKopecks)
    }

    // ── Категория «Подписки» — прямое слово пользователя ─────────────────────────

    @Test
    fun `a single charge labelled as a subscription still shows up`() {
        // Ровно случай ChatGPT: списание пока одно, ритма нет, но человек отнёс его к «Подпискам».
        val charges = listOf(
            charge(0, 19_99, "RECR GOOGLE *ChatGPT, 855-836-3987", "USD", "cat_subscription"),
        )
        val sub = SubscriptionDetector.detect(charges, now).single()
        assertEquals(Evidence.Labelled, sub.evidence)
        assertNull("период по одному списанию неизвестен", sub.period)
        assertEquals("USD", sub.currency)
        assertEquals(19_99L, sub.monthlyKopecks)
        assertNull(sub.nextExpectedAt)
    }

    @Test
    fun `a labelled charge without a rhythm is never called overdue`() {
        val charges = listOf(charge(400, 500_00, "Сервер", "RUB", "cat_subscription"))
        assertFalse(SubscriptionDetector.detect(charges, now).single().isMissed)
    }

    @Test
    fun `rhythm outranks the label when both are present`() {
        val charges = listOf(
            charge(4,  599_00, "Яндекс Плюс", "RUB", "cat_subscription"),
            charge(34, 599_00, "Яндекс Плюс", "RUB", "cat_subscription"),
            charge(64, 599_00, "Яндекс Плюс", "RUB", "cat_subscription"),
        )
        assertEquals(Evidence.Regular, SubscriptionDetector.detect(charges, now).single().evidence)
    }

    // ── Валюты ──────────────────────────────────────────────────────────────────

    @Test
    fun `currencies never merge into one row`() {
        // 19,99 $ и 19,99 ₽ — это разные деньги. Раньше они складывались как одинаковые копейки.
        val charges = listOf(
            charge(4,  19_99, "Сервис", "USD"), charge(34, 19_99, "Сервис", "USD"), charge(64, 19_99, "Сервис", "USD"),
            charge(4,  19_99, "Сервис", "RUB"), charge(34, 19_99, "Сервис", "RUB"), charge(64, 19_99, "Сервис", "RUB"),
        )
        val subs = SubscriptionDetector.detect(charges, now)
        assertEquals(2, subs.size)
        assertEquals(setOf("USD", "RUB"), subs.map { it.currency }.toSet())
    }

    // ── Приведение названия ─────────────────────────────────────────────────────

    @Test
    fun `a changing reference number does not split one subscription into three`() {
        // Банк дописывает номер операции. Без очистки это три разных продавца и ни одной подписки.
        val charges = listOf(
            charge(4,  1_999_00, "RECR GOOGLE *ChatGPT, 855-836-3987"),
            charge(34, 1_999_00, "RECR GOOGLE *ChatGPT, 855-836-4021"),
            charge(64, 1_999_00, "RECR GOOGLE *ChatGPT, 855-836-9134"),
        )
        val sub = SubscriptionDetector.detect(charges, now).single()
        assertEquals(Period.Monthly, sub.period)
        assertEquals(3, sub.chargeCount)
    }

    @Test
    fun `normalise strips long digit runs and punctuation but keeps short numbers`() {
        assertEquals("recr google chatgpt", SubscriptionDetector.normalise("RECR GOOGLE *ChatGPT, 855-836-3987"))
        assertEquals("тинькофф 22", SubscriptionDetector.normalise("Тинькофф 22"))
    }

    // ── Пропущенное списание ────────────────────────────────────────────────────

    @Test
    fun `a monthly charge that stopped is reported as missed`() {
        val charges = listOf(charge(70, 599_00), charge(100, 599_00), charge(130, 599_00))
        assertTrue(SubscriptionDetector.detect(charges, now).single().isMissed)
    }

    @Test
    fun `a charge that arrived on time is not missed`() {
        val charges = listOf(charge(2, 599_00), charge(32, 599_00), charge(62, 599_00))
        assertFalse(SubscriptionDetector.detect(charges, now).single().isMissed)
    }

    // ── Границы ─────────────────────────────────────────────────────────────────

    @Test
    fun `no charges, no subscriptions`() {
        assertTrue(SubscriptionDetector.detect(emptyList(), now).isEmpty())
    }

    @Test
    fun `a nameless charge is skipped rather than grouped with other nameless ones`() {
        val charges = listOf(
            Charge(now, 100_00, "RUB", null, null, null),
            Charge(now - 30 * day, 100_00, "RUB", null, null, null),
            Charge(now - 60 * day, 100_00, "RUB", null, null, null),
        )
        assertTrue(SubscriptionDetector.detect(charges, now).isEmpty())
    }
}
