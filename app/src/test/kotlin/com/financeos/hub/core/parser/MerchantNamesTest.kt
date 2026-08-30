package com.financeos.hub.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Все строки здесь взяты из настоящего экспорта — это те названия, которые человек видит у себя
 * в истории, а не выдуманные примеры. Если тест упадёт, значит сломалось то, что он видит.
 */
class MerchantNamesTest {

    // ── Что показываем ──────────────────────────────────────────────────────────

    @Test
    fun `chatgpt is shown as ChatGPT no matter who billed it`() {
        listOf(
            "RECR GOOGLE *ChatGPT, 855-836-3987",
            "GOOGLE *ChatGPT, 855-836-3987",
            "VTS RECR OPENAI *CHATGPT, +14158799686",
            "VTS OPENAI *CHATGPT SUBSC, +14158799686",
        ).forEach { raw ->
            assertEquals("не разобрано: $raw", "ChatGPT", MerchantNames.display(raw))
        }
    }

    @Test
    fun `a truncated brand still resolves`() {
        // Банк присылает строки разной длины — «by An» и «by Anth».
        assertEquals("Claude", MerchantNames.display("RECR GOOGLE *Claude by An, 855-836-3987"))
        assertEquals("Claude", MerchantNames.display("GOOGLE *Claude by Anth, 855-836-3987"))
    }

    @Test
    fun `corporate suffix and support phone are dropped`() {
        assertEquals("HeyGen", MerchantNames.display("HEYGEN TECHNOLOGY INC., +12133166526"))
        assertEquals("KlingAI", MerchantNames.display("KLINGAI.COM, +8653952609"))
    }

    @Test
    fun `a doubled phrase collapses and the account number goes`() {
        assertEquals(
            "Оплата телефона",
            MerchantNames.display("Оплата телефона Оплата телефона 9194828615 на 550,00 RUB"),
        )
    }

    @Test
    fun `an ordinary shop is left exactly as it came`() {
        // Чистка не должна трогать то, что и так читается.
        assertEquals("Транспорт Перми", MerchantNames.display("Транспорт Перми"))
        assertEquals("Пятёрочка", MerchantNames.display("Пятёрочка "))
        assertEquals("Куединский мясокомбинат", MerchantNames.display("Куединский мясокомбинат"))
        assertEquals("Блинная сковородка", MerchantNames.display("Блинная сковородка"))
    }

    @Test
    fun `nothing in means nothing out`() {
        assertNull(MerchantNames.display(null))
        assertNull(MerchantNames.display("   "))
        assertNull(MerchantNames.groupKey(null))
    }

    @Test
    fun `a name that is nothing but noise falls back to the original`() {
        // Лучше показать непонятное, чем пустую строку.
        assertEquals("INC", MerchantNames.display("INC"))
    }

    // ── По чему группируем ──────────────────────────────────────────────────────

    @Test
    fun `two ChatGPT subscriptions through different billers stay apart`() {
        // У человека их действительно две: 19,99 через Google Play и 22,40 напрямую в OpenAI.
        // Схлопнуть их в одну строку значило бы занизить расходы вдвое.
        val viaGoogle = MerchantNames.groupKey("RECR GOOGLE *ChatGPT, 855-836-3987")
        val viaOpenAi = MerchantNames.groupKey("VTS OPENAI *CHATGPT SUBSC, +14158799686")
        assertNotEquals(viaGoogle, viaOpenAi)
    }

    @Test
    fun `the same subscription through the same biller merges`() {
        assertEquals(
            MerchantNames.groupKey("RECR GOOGLE *ChatGPT, 855-836-3987"),
            MerchantNames.groupKey("GOOGLE *ChatGPT, 855-836-3987"),
        )
        assertEquals(
            MerchantNames.groupKey("VTS RECR OPENAI *CHATGPT, +14158799686"),
            MerchantNames.groupKey("VTS OPENAI *CHATGPT SUBSC, +14158799686"),
        )
    }

    @Test
    fun `truncation does not split one subscription in two`() {
        assertEquals(
            MerchantNames.groupKey("RECR GOOGLE *Claude by An, 855-836-3987"),
            MerchantNames.groupKey("GOOGLE *Claude by Anth, 855-836-3987"),
        )
    }

    @Test
    fun `a changing reference number does not create a new merchant`() {
        assertEquals(
            MerchantNames.groupKey("Пятёрочка 100234"),
            MerchantNames.groupKey("Пятёрочка 998877"),
        )
    }

    // ── Находки ревью: их легче внести обратно, чем заметить ────────────────────

    @Test
    fun `a brand is matched as a whole word, not as a substring`() {
        // «Canvas» содержит «canva». Без границ слова магазин холстов стал бы подпиской Canva,
        // а «Стимул» — играми в Steam.
        assertEquals("CANVAS PRINT SHOP", MerchantNames.display("CANVAS PRINT SHOP"))
        assertEquals("Canva", MerchantNames.display("CANVA *PRO"))
    }

    @Test
    fun `two products of one brand are two subscriptions`() {
        // Creative Cloud и Acrobat — разные подписки. Слить их в одну значит потерять обе:
        // суммы разные, проверка стабильности не пройдёт, и со экрана исчезнут обе строки.
        assertNotEquals(
            MerchantNames.groupKey("ADOBE *CREATIVE CLOUD"),
            MerchantNames.groupKey("ADOBE *ACROBAT"),
        )
    }

    @Test
    fun `cutting out a phone number does not leave punctuation behind`() {
        // «MTS 8-800-250-0890 MOSCOW»: после вырезания длинных чисел оставались огрызки «8- - -».
        assertEquals("MTS MOSCOW", MerchantNames.display("MTS 8-800-250-0890 MOSCOW"))
    }

    @Test
    fun `shops are left alone entirely`() {
        // Магазины и игры намеренно НЕ переименовываются: они и так читаются, а общий ключ по
        // бренду склеил бы разовые покупки в одну «подписку».
        assertEquals("Wildberries", MerchantNames.display("Wildberries"))
        assertEquals("пополнение стим", MerchantNames.display("пополнение стим"))
        assertNotEquals(
            MerchantNames.groupKey("покупка Wildberries"),
            MerchantNames.groupKey("Wildberries"),
        )
    }

    @Test
    fun `different shops keep different keys`() {
        assertNotEquals(
            MerchantNames.groupKey("Пятёрочка"),
            MerchantNames.groupKey("Магнит"),
        )
    }
}
