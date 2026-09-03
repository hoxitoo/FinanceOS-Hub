package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.AlfabankParser
import com.financeos.hub.core.parser.banks.SberbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пуши, снятые с реального устройства и НЕ разобранные приложением.
 *
 * Тексты собраны так же, как их склеивает `PushNotificationListener`: заголовок, затем текст, через
 * пробел. Менять их нельзя — в этом весь смысл файла: он фиксирует форматы, которые банк
 * действительно присылает, а не те, которые удобно разбирать.
 *
 * Все три дефекта здесь одного рода — молчаливые. Приложение не падало и ничего не писало в лог:
 * операция либо не появлялась вовсе, либо появлялась с ОБРАТНЫМ знаком, что хуже — деньги, которые
 * пришли, уменьшали остаток.
 */
class RealPushFormatsTest {

    private val ts = 1_756_000_000_000L
    private val alfa = AlfabankParser()
    private val sber = SberbankParser()

    // ── Входящий перевод по СБП: слова между «Перевод» и «от» ────────────────────

    @Test
    fun `Alfa incoming SBP transfer in RUR is parsed as incoming`() {
        // Две ошибки разом: «RUR» (устаревший код рубля) не был валютой, поэтому сумма не
        // находилась и перевод пропадал целиком; а по слову «СБП» он же считался ИСХОДЯЩИМ.
        val body = "Уведомление Перевод на сумму 8000.00 RUR из Кошелек ЦУПИС " +
            "(Мобильная карта) от Андрей Л. по СБП."
        val tx = alfa.parse("Альфа-Банк", body, ts)

        assertNotNull("входящий перевод по СБП не должен теряться", tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(800_000L, tx.amountKopecks)
        assertFalse("деньги ПРИШЛИ — перевод входящий", tx.outgoing)
    }

    @Test
    fun `Sberbank incoming SBP transfer keeps its direction and card`() {
        // «Перевод по СБП от …»: между стеблем и «от» стоит «по СБП», поэтому старое `Перевод\s+от`
        // не срабатывало, и приход +1 200 ₽ записывался как уход.
        val body = "Перевод по СБП от АНДРЕЙ ВЛАДИМИРОВИЧ Л. + 1 200 ₽ — " +
            "Счёт карты VISA •• 3387 \"Перевод денежных средств\""
        val tx = sber.parse("900", body, ts)

        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(120_000L, tx.amountKopecks)
        assertFalse("приход не может быть исходящим переводом", tx.outgoing)
        // Без маски операция не привязывается ни к какому счёту и повисает в истории.
        assertEquals("3387", tx.cardMask)
    }

    // ── Перевод между своими счетами ─────────────────────────────────────────────

    @Test
    fun `Alfa transfer between own accounts keeps both masks`() {
        val body = "-7 000 ₽. Перевод проведен Перевод 7 000,00 RUB со счета 4*1139 " +
            "на счет 4*3583 проведен успешно"
        val tx = alfa.parse("Альфа-Банк", body, ts)

        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(700_000L, tx.amountKopecks)
        assertTrue("деньги ушли со счёта 1139", tx.outgoing)
        assertEquals("1139", tx.cardMask)
        // Вторая нога перевода собирается по этой маске — без неё встречный счёт не пополнится.
        assertEquals("3583", tx.counterpartyMask)
    }

    // ── Списание по счёту, а не по карте ─────────────────────────────────────────

    @Test
    fun `Alfa debit from an account number is an expense with its payee`() {
        // «408*01139» — номер счёта: после маски БОЛЬШЕ четырёх цифр, поэтому обычный поиск маски
        // карты здесь не работает и значащим остатком считаются последние четыре.
        val body = "-200 ₽ Списание со счета 408*01139; Сумма: 200,00 RUB; " +
            "Получатель платежа BKS Mir Investitsiy; 2 сентября 08:56"
        val tx = alfa.parse("Альфа-Банк", body, ts)

        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(20_000L, tx.amountKopecks)
        assertEquals("BKS Mir Investitsiy", tx.merchant)
        assertEquals("1139", tx.cardMask)
    }

    // ── Границы: что НЕ должно измениться ────────────────────────────────────────

    @Test
    fun `an outgoing transfer without the word from stays outgoing`() {
        // Правило «Перевод … от …» намеренно требует отдельного слова «от». Если бы оно ловило
        // приставку, «Отправлен перевод» стал бы входящим — то есть уход денег увеличивал бы
        // остаток.
        val r = TransferPatterns.detect("Отправлен перевод 3 000 р на счёт 1234")
        assertNotNull(r)
        assertTrue(r!!.outgoing)
    }

    @Test
    fun `marketing copy is still not a transfer`() {
        // Расширенное правило не должно вернуть тот самый фантомный перевод на 163 000 ₽.
        assertNull(TransferPatterns.detect("Кэшбэк и бесплатными переводами до 30%"))
    }
}
