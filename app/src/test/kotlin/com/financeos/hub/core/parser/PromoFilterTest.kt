package com.financeos.hub.core.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromoFilterTest {

    @Test fun `T-Bank credit card offer is rejected as promo`() {
        val body = "Одобрили кредитку. Получите карту с лимитом 163 000 ₽, " +
            "кэшбэком до 30%, рассрочками и бесплатными переводами"
        assertTrue(PromoFilter.isPromo(body))
    }

    @Test fun `cashback rate offer is rejected`() {
        assertTrue(PromoFilter.isPromo("Кэшбэк до 30% в любимых категориях весь месяц"))
    }

    @Test fun `deposit rate offer is rejected`() {
        assertTrue(PromoFilter.isPromo("Вклад под 18% годовых — успейте оформить"))
    }

    @Test fun `referral offer is rejected`() {
        assertTrue(PromoFilter.isPromo("Приведи друга и получи 1 000 ₽"))
    }

    // --- Real operation notifications must pass through (NOT promo) ---

    @Test fun `real purchase passes`() {
        assertFalse(PromoFilter.isPromo("Покупка 1 500,00 ₽. ПЯТЁРОЧКА. Остаток: 5 000 ₽; ··2548"))
    }

    @Test fun `real transfer passes`() {
        assertFalse(PromoFilter.isPromo("Перевод 5 000 ₽ на карту *1234. Остаток: 10 000 ₽"))
    }

    @Test fun `real cashback credit passes`() {
        // A genuine cashback CREDIT ("Кэшбэк 150 ₽") must not be mistaken for the "кэшбэк до …%" offer.
        assertFalse(PromoFilter.isPromo("Кэшбэк 150 ₽ зачислен. Остаток: 5 150 ₽"))
    }

    @Test fun `credit-card purchase mentioning available limit passes`() {
        // "доступный лимит" in a real purchase must not match the "карту с лимитом" offer phrase.
        assertFalse(PromoFilter.isPromo("Покупка 1 200 ₽. Доступный лимит 48 800 ₽"))
    }

    @Test fun `salary credit passes`() {
        assertFalse(PromoFilter.isPromo("Зачисление 80 000,00 ₽. Зарплата. Остаток: 92 000 ₽"))
    }
}
