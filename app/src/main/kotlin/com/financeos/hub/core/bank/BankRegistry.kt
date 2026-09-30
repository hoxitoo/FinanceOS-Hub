package com.financeos.hub.core.bank

/**
 * Один банк — одна запись. Раньше имя, цвет, буква и ключевые слова банка жили в четырёх местах
 * (`bankBrand`, `BankSymbolBadge`, выбор банка в `AddAccountSheet`, `AccountLinker.BANK_KEYWORDS`),
 * и новый банк неизбежно забывали в одном из них — МКБ и Цифра так и не попали в выбор банка.
 *
 * Слоёв сопоставления ДВА, и они намеренно разные:
 * - [aliases] — чтобы УЗНАТЬ банк в свободном имени счёта для оформления (цвет, буква). Ошибка
 *   здесь стоит одного цвета, поэтому ключи широкие.
 * - [linkKeywords] — чтобы ПРИВЯЗАТЬ операцию без маски карты к счёту. Ошибка здесь кладёт деньги
 *   на чужой счёт, поэтому ключи узкие («мтс банк», а не «мтс»). Слить их с [aliases] значит
 *   расширить привязку: см. инвариант #2 и [com.financeos.hub.core.account.AccountLinker].
 *
 * Цвет хранится числом ARGB, а не `Color`: `core/` не зависит от Compose и проверяется на JVM.
 */
data class BankSpec(
    /** `BankParser.bankId` банка; `null` — у банка нет разборщика (Цифра). */
    val parserId    : String?,
    /** Имя в выборе банка — и то, что сохраняется в `AccountEntity.bank`. Менять нельзя. */
    val displayName : String,
    /** Буква на значке карточки банка. */
    val letter      : String,
    /** Подстроки (в нижнем регистре) для узнавания банка в имени счёта. */
    val aliases     : List<String>,
    /** Подстроки (в нижнем регистре) для привязки операции без маски к счёту. */
    val linkKeywords: List<String>,
    /** Фирменный цвет, ARGB. */
    val brandArgb   : Long,
    /** Фирменный цвет светлый — текст на нём тёмный. */
    val lightBrand  : Boolean = false,
)

object BankRegistry {

    /**
     * ПОРЯДОК ЗНАЧИМ: имя счёта сверяется сверху вниз, побеждает первое совпадение. «Газпромбанк»
     * содержит «мбанк», поэтому Газпромбанк обязан стоять выше МБанка. Порядок повторяет прежний
     * `bankBrand`, и на нём держится тест `every bank resolves to itself`.
     */
    val all: List<BankSpec> = listOf(
        BankSpec("sberbank", "Сбербанк", "С",
            aliases      = listOf("сбер", "sber"),
            linkKeywords = listOf("сбер", "sber"),
            brandArgb    = 0xFF1A9F29),
        BankSpec("tbank", "Т-Банк", "Т",
            aliases      = listOf("т-банк", "т банк", "тинь", "tinkoff", "tbank"),
            linkKeywords = listOf("т-банк", "тинькофф", "tinkoff", "тинк", "tbank"),
            brandArgb    = 0xFFFFDD2D, lightBrand = true),
        BankSpec("vtb", "ВТБ", "В",
            aliases      = listOf("втб", "vtb"),
            linkKeywords = listOf("втб", "vtb"),
            brandArgb    = 0xFF009FDF),
        BankSpec("alfabank", "Альфа-Банк", "А",
            aliases      = listOf("альфа", "alfa", "alpha"),
            linkKeywords = listOf("альфа", "alfa"),
            brandArgb    = 0xFFEF3124),
        BankSpec("gazprombank", "Газпромбанк", "Г",
            aliases      = listOf("газпром", "gazprom", "гпб"),
            linkKeywords = listOf("газпром", "gazprom"),
            brandArgb    = 0xFF1F4C92),
        BankSpec("raiffeisen", "Райффайзенбанк", "Р",
            aliases      = listOf("райф", "raiff"),
            linkKeywords = listOf("райф", "raiff"),
            brandArgb    = 0xFFFEE600, lightBrand = true),
        BankSpec("rosbank", "Росбанк", "Р",
            aliases      = listOf("росбанк", "rosbank"),
            linkKeywords = listOf("росбанк", "rosbank"),
            brandArgb    = 0xFFC8102E),
        BankSpec("otkritie", "Банк Открытие", "О",
            aliases      = listOf("открыт", "otkritie"),
            linkKeywords = listOf("открыт", "otkrit"),
            brandArgb    = 0xFF00AEEF),
        BankSpec("mtsbank", "МТС Банк", "М",
            aliases      = listOf("мтс", "mts"),
            linkKeywords = listOf("мтс банк", "mts bank", "мтсб"),
            brandArgb    = 0xFFE30611),
        BankSpec("postabank", "Почта Банк", "П",
            aliases      = listOf("почта", "posta", "post bank"),
            linkKeywords = listOf("почта банк", "pochta", "pochtabank"),
            brandArgb    = 0xFF1A468C),
        // Разборщик называет себя «rosselkhoz», а прежняя таблица привязки ждала «rosselkhozbank»:
        // операция РСХБ без маски не привязывалась к счёту никогда. Одна запись на банк это чинит.
        BankSpec("rosselkhoz", "Россельхозбанк", "Р",
            aliases      = listOf("россельхоз", "рсхб", "rshb"),
            linkKeywords = listOf("россельхоз", "рсхб", "rshb"),
            brandArgb    = 0xFF006B3F),
        BankSpec("mbank", "МБанк", "М",
            aliases      = listOf("мбанк", "mbank", "кыргыз"),
            linkKeywords = listOf("mbank", "мбанк", "m bank"),
            brandArgb    = 0xFF0076BE),
        BankSpec("mkb", "МКБ", "М",
            aliases      = listOf("мкб", "mkb", "московский кредитный"),
            linkKeywords = listOf("мкб", "mkb", "московский кредитный"),
            brandArgb    = 0xFF002D74),
        BankSpec(null, "Цифра Банк", "Ц",
            aliases      = listOf("цифра", "cifra"),
            linkKeywords = emptyList(),
            brandArgb    = 0xFF5E35B1),
    )

    /** Цвет для банка, которого нет в реестре. */
    const val UNKNOWN_ARGB: Long = 0xFF3A4358

    /**
     * Порядок чипов в выборе банка. Первые восемь — прежний список в прежнем порядке (к нему
     * привыкли руки), дальше банки, которые раньше можно было добавить только через «Другой».
     */
    val picker: List<BankSpec> = listOf(
        "Сбербанк", "Т-Банк", "ВТБ", "Альфа-Банк", "Газпромбанк", "МБанк", "МКБ", "Цифра Банк",
        "Райффайзенбанк", "Росбанк", "Банк Открытие", "МТС Банк", "Почта Банк", "Россельхозбанк",
    ).map { name -> all.first { it.displayName == name } }

    /** Узнаёт банк по свободному имени счёта («Сбер», «SBER», «Сбербанк» — один банк). */
    fun find(bankName: String): BankSpec? {
        val b = bankName.lowercase()
        return all.firstOrNull { spec -> spec.aliases.any { it in b } }
    }

    /** Запись по `BankParser.bankId`. */
    fun byParserId(parserId: String): BankSpec? {
        val id = parserId.lowercase()
        return all.firstOrNull { it.parserId == id }
    }

    /** Буква значка: из реестра, для незнакомого банка — первая буква имени. */
    fun letterFor(bankName: String): String =
        find(bankName)?.letter ?: bankName.firstOrNull()?.uppercase() ?: "?"
}
