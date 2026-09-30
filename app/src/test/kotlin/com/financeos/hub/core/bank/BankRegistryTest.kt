package com.financeos.hub.core.bank

import com.financeos.hub.core.parser.banks.AlfabankParser
import com.financeos.hub.core.parser.banks.GazprombankParser
import com.financeos.hub.core.parser.banks.MBankParser
import com.financeos.hub.core.parser.banks.MkbParser
import com.financeos.hub.core.parser.banks.MtsBankParser
import com.financeos.hub.core.parser.banks.OtkritieParser
import com.financeos.hub.core.parser.banks.PostaBankParser
import com.financeos.hub.core.parser.banks.RaiffeisenParser
import com.financeos.hub.core.parser.banks.RosbankParser
import com.financeos.hub.core.parser.banks.RosselkhozParser
import com.financeos.hub.core.parser.banks.SberbankParser
import com.financeos.hub.core.parser.banks.TbankParser
import com.financeos.hub.core.parser.banks.VtbParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Реестр банков заменил четыре копии. Главная проверка — что для существующих имён счетов НИЧЕГО
 * не поменялось: прежние функции скопированы сюда дословно и сравниваются с реестром.
 */
class BankRegistryTest {

    // ── Прежние реализации, дословно (до реестра) ─────────────────────────────────────────────

    private val WHITE = 0xFFFFFFFF
    private val DARK  = 0xFF14181F

    /** `bankBrand` → (фон, цвет текста). */
    private fun legacyBrand(bank: String): Pair<Long, Long> {
        val b = bank.lowercase()
        return when {
            "сбер" in b || "sber" in b -> 0xFF1A9F29 to WHITE
            "т-банк" in b || "т банк" in b || "тинь" in b || "tinkoff" in b || "tbank" in b -> 0xFFFFDD2D to DARK
            "втб" in b || "vtb" in b -> 0xFF009FDF to WHITE
            "альфа" in b || "alfa" in b || "alpha" in b -> 0xFFEF3124 to WHITE
            "газпром" in b || "gazprom" in b || "гпб" in b -> 0xFF1F4C92 to WHITE
            "райф" in b || "raiff" in b -> 0xFFFEE600 to DARK
            "росбанк" in b || "rosbank" in b -> 0xFFC8102E to WHITE
            "открыт" in b || "otkritie" in b -> 0xFF00AEEF to WHITE
            "мтс" in b || "mts" in b -> 0xFFE30611 to WHITE
            "почта" in b || "posta" in b || "post bank" in b -> 0xFF1A468C to WHITE
            "россельхоз" in b || "рсхб" in b || "rshb" in b -> 0xFF006B3F to WHITE
            "мбанк" in b || "mbank" in b || "кыргыз" in b -> 0xFF0076BE to WHITE
            "мкб" in b || "mkb" in b || "московский кредитный" in b -> 0xFF002D74 to WHITE
            "цифра" in b || "cifra" in b -> 0xFF5E35B1 to WHITE
            else -> 0xFF3A4358 to WHITE
        }
    }

    /** `BankSymbolBadge` → буква. */
    private fun legacyLetter(bank: String): String {
        val b = bank.lowercase()
        return when {
            "альфа" in b || "alfa" in b || "alpha" in b -> "А"
            "сбер" in b || "sber" in b -> "С"
            "т-банк" in b || "тинь" in b || "tbank" in b || "tinkoff" in b -> "Т"
            "втб" in b || "vtb" in b -> "В"
            "газпром" in b || "гпб" in b -> "Г"
            "мбанк" in b || "mbank" in b || "кыргыз" in b || "kicb" in b -> "М"
            "мтс" in b -> "М"
            "почта" in b || "posta" in b -> "П"
            "россельхоз" in b || "рсхб" in b -> "Р"
            "росбанк" in b || "rosbank" in b -> "Р"
            "открыт" in b || "otkritie" in b -> "О"
            "райф" in b || "raiff" in b -> "Р"
            "мкб" in b || "mkb" in b || "московский кредитный" in b -> "М"
            "цифра" in b || "cifra" in b -> "Ц"
            else -> bank.firstOrNull()?.uppercase() ?: "?"
        }
    }

    /** `AccountLinker.BANK_KEYWORDS`. */
    private val legacyLinkKeywords: Map<String, List<String>> = mapOf(
        "alfabank"       to listOf("альфа", "alfa"),
        "sberbank"       to listOf("сбер", "sber"),
        "tbank"          to listOf("т-банк", "тинькофф", "tinkoff", "тинк", "tbank"),
        "vtb"            to listOf("втб", "vtb"),
        "gazprombank"    to listOf("газпром", "gazprom"),
        "raiffeisen"     to listOf("райф", "raiff"),
        "rosbank"        to listOf("росбанк", "rosbank"),
        "otkritie"       to listOf("открыт", "otkrit"),
        "mtsbank"        to listOf("мтс банк", "mts bank", "мтсб"),
        "postabank"      to listOf("почта банк", "pochta", "pochtabank"),
        "rosselkhozbank" to listOf("россельхоз", "рсхб", "rshb"),
        "mbank"          to listOf("mbank", "мбанк", "m bank"),
        "mkb"            to listOf("мкб", "mkb", "московский кредитный"),
    )

    /** Имена счетов, какими их вводят люди: полные, короткие, латиницей, капсом, с уточнением. */
    private val names = listOf(
        "Сбербанк", "Сбер", "SBER", "Сбер Кредитка", "СберКарта",
        "Т-Банк", "Т Банк", "Тинькофф", "Tinkoff Black", "TBank",
        "ВТБ", "втб зарплатная", "VTB",
        "Альфа-Банк", "Альфа", "Alfa", "Alpha bank",
        "Газпромбанк", "ГПБ", "Gazprombank",
        "Райффайзенбанк", "Райф", "Raiffeisen",
        "Росбанк", "Rosbank",
        "Банк Открытие", "Открытие", "Otkritie",
        "МТС Банк", "МТС", "MTS Bank",
        "Почта Банк", "Почта", "Posta",
        "Россельхозбанк", "РСХБ",
        "МБанк", "Mbank", "Кыргызстан",
        "МКБ", "MKB", "Московский кредитный банк",
        "Цифра Банк", "Цифра", "Cifra",
        "Совкомбанк", "Промсвязьбанк", "Озон Банк", "Наличные", "Другой", "Копилка",
    )

    // ── Поведение не изменилось ───────────────────────────────────────────────────────────────

    @Test
    fun `brand colours are exactly the same as before`() {
        for (name in names) {
            val spec = BankRegistry.find(name)
            val now  = (spec?.brandArgb ?: BankRegistry.UNKNOWN_ARGB) to
                (if (spec?.lightBrand == true) DARK else WHITE)
            assertEquals(name, legacyBrand(name), now)
        }
    }

    @Test
    fun `badge letters are the same as before`() {
        // Два латинских имени — единственное расхождение, и оба в пользу реестра: прежний значок не
        // знал «gazprom» и находил в «Gazprombank» подстроку «mbank» (буква МБанка), а у «MTS Bank»
        // брал первую букву — латинскую «M» вместо кириллической.
        val fixed = setOf("Gazprombank", "MTS Bank")
        for (name in names - fixed) {
            assertEquals(name, legacyLetter(name), BankRegistry.letterFor(name))
        }
        assertEquals("М", legacyLetter("Gazprombank"))
        assertEquals("Г", BankRegistry.letterFor("Gazprombank"))
        assertEquals("М", BankRegistry.letterFor("MTS Bank"))
    }

    @Test
    fun `link keywords are the same as before`() {
        for ((id, keywords) in legacyLinkKeywords) {
            // Россельхоз — единственный ключ, который меняется: см. тест ниже.
            val parserId = if (id == "rosselkhozbank") "rosselkhoz" else id
            assertEquals(id, keywords, BankRegistry.byParserId(parserId)?.linkKeywords)
        }
    }

    @Test
    fun `rosselkhoz is found by the id its parser actually reports`() {
        // Разборщик называет себя «rosselkhoz», таблица ждала «rosselkhozbank» — привязка без маски
        // для РСХБ не работала никогда. Теперь ключ берётся у самого разборщика.
        assertEquals("rosselkhoz", RosselkhozParser().bankId)
        assertNotNull(BankRegistry.byParserId(RosselkhozParser().bankId))
    }

    // ── Реестр согласован сам с собой ─────────────────────────────────────────────────────────

    @Test
    fun `every parser has a registry entry`() {
        val parsers = listOf(
            SberbankParser(), TbankParser(), VtbParser(), AlfabankParser(), GazprombankParser(),
            RaiffeisenParser(), RosbankParser(), OtkritieParser(), MtsBankParser(), PostaBankParser(),
            RosselkhozParser(), MBankParser(), MkbParser(),
        )
        for (p in parsers) {
            val spec = BankRegistry.byParserId(p.bankId)
            assertNotNull(p.bankId, spec)
            assertTrue(p.bankId, spec!!.linkKeywords.isNotEmpty())
        }
    }

    @Test
    fun `every bank resolves to itself by its own name`() {
        // Ловит неверный порядок: «Газпромбанк» содержит «мбанк» и без верного порядка стал бы МБанком.
        for (spec in BankRegistry.all) {
            assertEquals(spec.displayName, spec, BankRegistry.find(spec.displayName))
        }
    }

    @Test
    fun `picker keeps the old eight first and lists every bank once`() {
        val picked = BankRegistry.picker.map { it.displayName }
        assertEquals(
            listOf("Сбербанк", "Т-Банк", "ВТБ", "Альфа-Банк", "Газпромбанк", "МБанк", "МКБ", "Цифра Банк"),
            picked.take(8),
        )
        assertEquals(BankRegistry.all.map { it.displayName }.toSet(), picked.toSet())
        assertEquals(picked.size, picked.toSet().size)
    }

    @Test
    fun `unknown bank falls back to its first letter`() {
        assertNull(BankRegistry.find("Наличные"))
        assertEquals("Н", BankRegistry.letterFor("наличные"))
        assertEquals("?", BankRegistry.letterFor(""))
    }
}
