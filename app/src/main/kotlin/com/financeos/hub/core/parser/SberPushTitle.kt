package com.financeos.hub.core.parser

/**
 * Название магазина из заголовка пуша Сбера — в том числе из «игривого».
 *
 * Обычный пуш начинается словом операции: «Покупка DNS 18 699 ₽». Но Сбер умеет и по-другому:
 * «Котан, покупка в Пермский транспорт 43 ₽», «Такой вайб! Раскошелиться в Пятёрочка 2 658,65 ₽»,
 * «Ловкость лапок и оплата по СБП удалась в AVPERM_SBP 760,32 ₽». Раньше названием становился весь
 * текст до суммы, и один «Пермский транспорт» жил в истории под десятком имён: поиск, группировка,
 * подписки и категории разваливались.
 *
 * Правило: если в начале нет слова операции, а перед «в / из / прошла» стоит игривая фраза (эмодзи,
 * «!», запятая или узнаваемое слово вроде «покупка», «трата», «денежки»), название — то, что после
 * разделителя. Нет разделителя — то, что после последнего «! » / «. ». Ничего не узнали — текст
 * остаётся как был: лишнее слово в названии лучше потерянного магазина.
 *
 * Обычные пуши («Покупка DNS», «Пятёрочка 1 240 ₽») идут ровно прежним путём.
 */
object SberPushTitle {

    // Разделитель между фразой и магазином: «… в Пятёрочка», «… из Социальный», «оплата прошла СберСтрахование».
    private val SEPARATOR = ciRegex("""\s(?:в|во|из)\s+|(?<![\p{L}])прошл[аи]\s+""")

    // Последняя фраза, закрытая «!» / «.» с пробелом: «С покупочкой! Магнит».
    private val SENTENCE_END = Regex("""[!.?]\s+""")

    // Слова игривых заголовков Сбера — собраны из реальной выгрузки тестера (около сорока вариантов).
    private val PLAYFUL = ciRegex(
        """покуп|купил|закуп|трат|денеж|деньг|рубл|монетк|кошел|перекус|обед|шедевр|кулинар|""" +
            """гастро|вкусн|блюд|вайб|раскошел|транжир|разгул|обнов|нужн|оплат|котан|кото|выбор|""" +
            """ням|вау|вуаля|улёт|йуху|псс""")

    private val INCOME_PLUS = Regex("""\s+\+$""")

    /**
     * @param beforeAmount текст пуша до суммы.
     * @param opPrefix     слово операции в начале («Покупка», «Оплата»…), как в разборщике.
     * @param incomeNames  известные доходы («Зачисление пенсии» → «Пенсия»).
     */
    fun merchant(beforeAmount: String, opPrefix: Regex, incomeNames: List<Pair<Regex, String>>): String? {
        val raw = beforeAmount.trim().trim('.', ',', ';', '-', '—', '–', ' ')
            .replace(INCOME_PLUS, "")
            .trim()
        if (raw.isBlank()) return null

        incomeNames.firstOrNull { (re, _) -> re.containsMatchIn(raw) }?.let { return it.second }

        // Обычный пуш: слово операции в начале — прежнее поведение, один в один.
        if (opPrefix.containsMatchIn(raw)) {
            return raw.replace(opPrefix, "").trim().takeIf { it.isNotBlank() }
        }
        return playful(raw) ?: raw
    }

    /** Название из игривого заголовка или `null`, если это не он. */
    internal fun playful(raw: String): String? {
        SEPARATOR.find(raw)?.let { m ->
            val prefix = raw.substring(0, m.range.first)
            val rest   = clean(raw.substring(m.range.last + 1))
            if (isPlayful(prefix) && rest != null) return rest
        }
        SENTENCE_END.findAll(raw).lastOrNull()?.let { m ->
            val prefix = raw.substring(0, m.range.first)
            val rest   = clean(raw.substring(m.range.last + 1))
            if (isPlayful(prefix) && rest != null) return rest
        }
        return null
    }

    private fun isPlayful(prefix: String): Boolean =
        prefix.any { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ".,-—–'\"" } ||
            prefix.contains('!') || prefix.contains(',') ||
            PLAYFUL.containsMatchIn(prefix)

    /** Убирает эмодзи и знаки по краям; пустое — null. */
    private fun clean(s: String): String? =
        s.trim().trimStart { !it.isLetterOrDigit() }.trimEnd { !it.isLetterOrDigit() && it != ')' }
            .takeIf { it.isNotBlank() }

    // «Альфа-Банк», «Яндекс Банк», «Т-Банк», «Банк Открытие» — отдельным словом. «Сбербанк» слитно
    // не ловится намеренно, «Банкомат» — тоже (буква после).
    private val BANK = ciRegex("""(?<![\p{L}])(?:банк|bank)(?![\p{L}])""")

    /** Деньги ушли в банк, а не в магазин — это перевод. */
    fun isBank(merchant: String?): Boolean = merchant != null && BANK.containsMatchIn(merchant)
}
