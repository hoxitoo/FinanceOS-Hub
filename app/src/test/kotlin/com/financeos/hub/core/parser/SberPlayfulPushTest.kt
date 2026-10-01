package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.SberbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Реальные пуши Сбера из выгрузки тестера (скриншоты, 1 октября). Склеены как в
 * `PushNotificationListener`: заголовок, затем текст. Менять тексты нельзя — в этом их ценность.
 *
 * До правки: «DODO PIZZA PERM-5 1 034 ₽» записывалось как 51 034 ₽, «R15173685 90 ₽» — как
 * 1 517 368 590 ₽; оплата по СБП в магазине — переводом; «Выплата процентов + 77,23» — тратой;
 * перевод в другой банк — покупкой; название магазина — вместе с игривым заголовком.
 */
class SberPlayfulPushTest {

    private val engine = ParserEngine(setOf(SberbankParser()))
    private val ts     = 1_759_300_000_000L

    private fun parse(body: String): ParsedTransaction {
        val p = engine.parse("900", body, ts)
        assertNotNull("пуш не должен пропасть: $body", p)
        return p!!
    }

    // ── Сумма не склеивается с цифрами в конце названия ─────────────────────────

    @Test
    fun `trailing digits of the merchant do not glue to the amount`() {
        val dodo = parse("Шикарный перекус в DODO PIZZA PERM-5 1 034 ₽ — В запасе: 1 121,07 ₽ Счёт карты МИР •• 1238")
        assertEquals(1_034_00L, dodo.amountKopecks)
        assertEquals("DODO PIZZA PERM-5", dodo.merchant)
        assertEquals("1238", dodo.cardMask)
        assertEquals(1_121_07L, dodo.balanceKopecks)

        val r = parse("За кулинарные шедевры в R15173685 90 ₽ — В запасе: 764,90 ₽ Счёт карты МИР •• 8937")
        assertEquals(90_00L, r.amountKopecks)
        assertEquals("R15173685", r.merchant)
        assertEquals(TransactionType.EXPENSE, r.type)
    }

    @Test
    fun `an amount written without grouping still parses`() {
        // Строгий шаблон не находит «10000» — прежний путь подхватывает, пуш не теряется.
        val p = parse("Покупка Лента 10000 ₽ В запасе: 1 000 ₽ Карта •1234")
        assertEquals(10_000_00L, p.amountKopecks)
        // С копейками: строгий шаблон не должен начать сумму после запятой («00» → ноль → пуш
        // пропал бы, «67» → неверная сумма).
        assertEquals(1_500_00L, parse("Покупка Лента 1500,00 ₽ В запасе: 1 000 ₽ Карта •1234").amountKopecks)
        assertEquals(12_345_67L, parse("Покупка Лента 12345,67 ₽ В запасе: 1 000 ₽ Карта •1234").amountKopecks)
        assertEquals(99_90L, parse("Покупка Лента 99.90 ₽ В запасе: 1 000 ₽ Карта •1234").amountKopecks)
    }

    // ── Игривый заголовок не становится названием ───────────────────────────────

    @Test
    fun `the playful title is stripped, the shop name stays`() {
        assertEquals("Пятёрочка", parse(
            "Такой вайб! Раскошелиться в Пятёрочка 2 658,65 ₽ — В запасе: 448,42 ₽ Счёт карты МИР •• 8937").merchant)
        assertEquals("Пятёрочка", parse(
            "Псс, это покупка в Пятёрочка 235,96 ₽ — В запасе: 1 887,51 ₽ Счёт карты МИР •• 8937").merchant)
        assertEquals("Блинная сковородка", parse(
            "За кулинарные шедевры в Блинная сковородка 303 ₽ — В запасе: 13 320,25 ₽ Счёт карты МИР •• 9334").merchant)
    }

    @Test
    fun `ordinary titles keep the old behaviour`() {
        assertEquals("DNS", parse("Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703").merchant)
        assertEquals("Пятёрочка", parse("Пятёрочка 1 240 ₽ В запасе: 8 001,89 ₽ Карта •1234").merchant)
        // « в » внутри обычного названия без игривой фразы перед ним — не разделитель.
        assertEquals("Кофе в зёрнах", parse("Кофе в зёрнах 500 ₽ В запасе: 1 000 ₽ Карта •1234").merchant)
        // Плюс, приклеенный к названию, — не знак прихода.
        val prime = parse("СберПрайм+ 399 ₽ В запасе: 1 000 ₽ Карта •1234")
        assertEquals(TransactionType.EXPENSE, prime.type)
        assertEquals("СберПрайм+", prime.merchant)
    }

    // ── Оплата по СБП в магазине — покупка ──────────────────────────────────────

    @Test
    fun `an SBP payment in a shop is a purchase, not a transfer`() {
        val p = parse("Ловкость лапок и оплата по СБП удалась в AVPERM_SBP 760,32 ₽ — В запасе: 6 298,54 ₽ Счёт карты МИР •• 1238")
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals(760_32L, p.amountKopecks)
        assertEquals("AVPERM_SBP", p.merchant)
        assertEquals("1238", p.cardMask)
    }

    @Test
    fun `an incoming SBP transfer is still an incoming transfer`() {
        val p = parse("Перевод по СБП от АНДРЕЙ ВЛАДИМИРОВИЧ Л. + 1 200 ₽ — Счёт карты VISA •• 3387 \"Перевод денежных средств\"")
        assertEquals(TransactionType.TRANSFER, p.type)
        assertFalse(p.outgoing)
    }

    // ── Приход со знаком «+» ────────────────────────────────────────────────────

    @Test
    fun `plus-signed credits are income with a readable name`() {
        val interest = parse("Выплата процентов + 77,23 ₽ — Баланс: 10 213,38 ₽ Накопительный счет •• 4958")
        assertEquals(TransactionType.INCOME, interest.type)
        assertEquals(77_23L, interest.amountKopecks)
        assertEquals("Проценты", interest.merchant)
        assertEquals("4958", interest.cardMask)

        val pension = parse("Зачисление пенсии + 56 014,24 ₽ — Баланс: 56 059,12 ₽ Пенсионный+ •• 8752")
        assertEquals(TransactionType.INCOME, pension.type)
        assertEquals(56_014_24L, pension.amountKopecks)
        assertEquals("Пенсия", pension.merchant)
        assertEquals("8752", pension.cardMask)

        val deposit = parse("Зачисление средств + 179 ₽ — Баланс: 100 590,46 ₽ Накопительный счет •• 0471")
        assertEquals(TransactionType.INCOME, deposit.type)
        assertEquals("Зачисление", deposit.merchant)
    }

    // ── Деньги в другой банк — перевод ──────────────────────────────────────────

    @Test
    fun `money sent to another bank is an outgoing transfer`() {
        val yandex = parse("✔️ Денежки уже в Яндекс Банк 300 ₽ — В запасе: 106,73 ₽ Счёт карты МИР •• 8937")
        assertEquals(TransactionType.TRANSFER, yandex.type)
        assertTrue(yandex.outgoing)
        assertEquals(300_00L, yandex.amountKopecks)
        assertEquals("Яндекс Банк", yandex.merchant)

        val alfa = parse("🎉 Йуху! Деньги отправились в Альфа-Банк 6 000 ₽ — В запасе: 34 050,05 ₽ Плат. счёт •• 4102")
        assertEquals(TransactionType.TRANSFER, alfa.type)
        assertTrue(alfa.outgoing)
        assertEquals(6_000_00L, alfa.amountKopecks)
        assertEquals("Альфа-Банк", alfa.merchant)
        assertEquals("4102", alfa.cardMask)
    }

    @Test
    fun `a bank word glued into a name is not a bank`() {
        assertFalse(SberPushTitle.isBank("Сбербанк Онлайн"))
        assertFalse(SberPushTitle.isBank("Банкомат"))
        assertTrue(SberPushTitle.isBank("Т-Банк"))
        assertTrue(SberPushTitle.isBank("Банк Открытие"))
    }

    @Test
    fun `a plain payment to a bank stays an expense`() {
        // Платёж по кредиту в другом банке — трата, и она закрывает обязательство в календаре.
        // Переводом считается только «деньги ушли в …» из игривого заголовка.
        val loan = parse("Оплата Почта Банк 5 000 ₽ Баланс: 10 000 ₽ Карта •1234")
        assertEquals(TransactionType.EXPENSE, loan.type)
        assertEquals("Почта Банк", loan.merchant)
    }
}
