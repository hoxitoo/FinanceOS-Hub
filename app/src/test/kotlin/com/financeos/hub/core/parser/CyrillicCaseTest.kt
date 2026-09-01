package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.banks.AlfabankParser
import com.financeos.hub.core.parser.banks.SberbankParser
import com.financeos.hub.core.parser.banks.TbankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регистр кириллицы в разборе сообщений.
 *
 * `RegexOption.IGNORE_CASE` на JVM — это `Pattern.CASE_INSENSITIVE`, который по документации
 * сворачивает регистр **только US-ASCII**. Проверено на JDK 21: «Покупка» НЕ совпадает с «ПОКУПКА»,
 * хотя «Purchase» с «PURCHASE» совпадает. Для приложения, читающего русские СМС, это молчаливая
 * потеря операции: банк присылает служебное слово капсом — парсер возвращает `null`, ошибки нет,
 * лога нет, в истории просто нет платежа.
 *
 * Тексты ниже — РЕАЛЬНЫЕ форматы из тестов соответствующих банков, в которых изменён только регистр
 * служебных слов. Так падение этого файла сразу говорит «дело в регистре», а не в самом формате.
 */
class CyrillicCaseTest {

    private val ts = 1_718_700_000_000L

    @Test fun `Sberbank expense survives uppercase keywords`() {
        // Исходник: "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
        val tx = SberbankParser()
            .parse("900", "VISA1234 18.06.25 12:34 ОПЛАТА 1 500р МАГАЗИН БАЛАНС: 12 345,67р", ts)
        assertNotNull("капслок не должен терять покупку", tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150_000L, tx.amountKopecks)
    }

    @Test fun `Sberbank income survives uppercase keywords`() {
        // Исходник: "VISA1234 18.06.25 10:00 Зачисление 50 000р"
        val tx = SberbankParser().parse("900", "VISA1234 18.06.25 10:00 ЗАЧИСЛЕНИЕ 50 000р", ts)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(5_000_000L, tx.amountKopecks)
    }

    @Test fun `Sberbank push strips an uppercase operation verb`() {
        // Исходник — подтверждённый на устройстве пуш по кредитке:
        // "Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703"
        // Оставленная приставка сделала бы продавцом «ПОКУПКА DNS», под который не подходит
        // ни одно правило категоризации.
        val tx = SberbankParser()
            .parse("900", "ПОКУПКА DNS 18 699 ₽ — БАЛАНС: 411 301 ₽ Счёт карты МИР •• 6703", ts)
        assertNotNull(tx)
        assertEquals("DNS", tx!!.merchant)
        assertEquals(18_699_00L, tx.amountKopecks)
        assertEquals("6703", tx.cardMask)
    }

    @Test fun `T-Bank parses lowercase keywords`() {
        // Исходник: "Оплата 1500,00 RUB. Кафе Урюк. Карта *1234. Баланс: 5000,00 RUB"
        val tx = TbankParser()
            .parse("TINKOFF", "оплата 1500,00 RUB. Кафе Урюк. карта *1234. баланс: 5000,00 RUB", ts)
        assertNotNull("нижний регистр не должен терять покупку", tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test fun `Alfa-Bank parses uppercase keywords`() {
        // Исходник: "Покупка. Карта *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. Доступно: 10000.00 RUB"
        val tx = AlfabankParser().parse(
            "Alfa-Bank",
            "ПОКУПКА. КАРТА *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. ДОСТУПНО: 10000.00 RUB",
            ts,
        )
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test fun `transfer keywords fold case too`() {
        // Иначе «ПЕРЕВОД» проходил бы мимо TransferPatterns и становился обычным расходом — а
        // перевод между своими счетами не должен менять нетто-капитал.
        assertNotNull(TransferPatterns.detect("ПЕРЕВОД 5 000 ₽ на карту *1234"))
        assertNotNull(TransferPatterns.detect("перевод 5 000 ₽ на карту *1234"))
    }

    @Test fun `promo filter catches uppercase marketing`() {
        // Обратная сторона той же ошибки: рекламный пуш как раз и приходит с криком в заголовке,
        // и не свернув регистр, фильтр пропустил бы его в разбор как настоящую операцию.
        assertTrue(PromoFilter.isPromo("ВАМ ОДОБРЕН КРЕДИТНЫЙ ЛИМИТ 163 000 ₽"))
        assertTrue(PromoFilter.isPromo("КЭШБЭК ДО 30% В ЛЮБИМЫХ КАТЕГОРИЯХ"))
    }
}
