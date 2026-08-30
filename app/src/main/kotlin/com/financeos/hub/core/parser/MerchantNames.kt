package com.financeos.hub.core.parser

/**
 * Превращение того, что прислал банк, в название, которое можно читать.
 *
 * Банк присылает не имя продавца, а строку биллинга: кто провёл платёж, что купили, телефон
 * поддержки, номер операции, форма собственности. В истории это выглядит так:
 *
 * ```
 * RECR GOOGLE *ChatGPT, 855-836-3987
 * VTS OPENAI *CHATGPT SUBSC, +14158799686
 * HEYGEN TECHNOLOGY INC., +12133166526
 * Оплата телефона Оплата телефона 9194828615 на 550,00 RUB
 * ```
 *
 * Человеку нужно «ChatGPT». Отсюда две разные операции над одной строкой, и их важно не путать:
 *
 *  - [display] — что показать. Максимально коротко, с узнаваемым названием сервиса.
 *  - [groupKey] — по чему считать, что это ОДНО и то же списание. Здесь короткость вредна:
 *    у человека две подписки ChatGPT, оплаченные через разных посредников (19,99 через Google
 *    Play и 22,40 напрямую в OpenAI), и схлопнуть их в одну строку значило бы занизить его
 *    расходы вдвое. Поэтому ключ хранит и сервис, и посредника: `chatgpt|google` ≠ `chatgpt|openai`.
 *
 * Обратная сторона той же монеты — обрезка. Банк присылает то «GOOGLE *Claude by An», то
 * «GOOGLE *Claude by Anth»: строки разной длины, один и тот же сервис. Ключ по распознанному
 * бренду («claude») их объединяет, а посимвольное сравнение — никогда бы не объединило.
 *
 * Исходный текст НЕ переписывается: он лежит в самой операции, виден в её карточке и по нему
 * ищет поиск. Чистка происходит только при показе, поэтому она задним числом чинит и старые
 * записи, и не требует ни миграции, ни риска потерять данные.
 */
object MerchantNames {

    /**
     * Сервис, который списывает деньги. Проверяется ПЕРВЫМ: в строке «OPENAI *CHATGPT» есть и
     * посредник, и продукт, а показать нужно продукт.
     *
     * Список намеренно короткий — только то, что реально встречается в выписках и что банк пишет
     * неузнаваемо. Обычные магазины в него не попадают: «Пятёрочка» и так читается.
     */
    private val BRANDS: Map<String, String> = linkedMapOf(
        "chatgpt"      to "ChatGPT",
        "claude"       to "Claude",
        "anthropic"    to "Claude",
        "midjourney"   to "Midjourney",
        "heygen"       to "HeyGen",
        "klingai"      to "KlingAI",
        "kling ai"     to "KlingAI",
        "elevenlabs"   to "ElevenLabs",
        "perplexity"   to "Perplexity",
        "github"       to "GitHub",
        "jetbrains"    to "JetBrains",
        "figma"        to "Figma",
        "canva"        to "Canva",
        "adobe"        to "Adobe",
        "notion"       to "Notion",
        "dropbox"      to "Dropbox",
        "icloud"       to "iCloud",
        "youtube"      to "YouTube",
        "netflix"      to "Netflix",
        "spotify"      to "Spotify",
        "duolingo"     to "Duolingo",
        "aeza"         to "Aeza",
        "аеза"         to "Aeza",
        "hetzner"      to "Hetzner",
        "digitalocean" to "DigitalOcean",
    )

    /**
     * Границы слова для поиска бренда. Без них «Стимул» опознавался бы как Steam, а любой продавец,
     * внутри названия которого случайно оказался кусок бренда, — как подписка.
     *
     * `\p{L}` вместо `\w`: `\w` в Java только латиница, и после кириллической буквы границы слова
     * не бывает — ровно та же ловушка, из-за которой в парсерах переводов пришлось перейти на
     * явные lookahead'ы.
     */
    private val BRAND_PATTERNS: List<Pair<Regex, String>> = BRANDS.map { (token, name) ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(token) + "(?![\\p{L}\\p{N}])") to name
    }

    /**
     * Кто провёл платёж. В [display] не показывается — человеку всё равно, через кого списали, —
     * но в [groupKey] входит: через разных посредников платят за разные подписки.
     */
    private val PROCESSORS: Map<String, String> = linkedMapOf(
        "google play"     to "google",
        "google"          to "google",
        "apple.com/bill"  to "apple",
        "itunes"          to "apple",
        "apple"           to "apple",
        "paypal"          to "paypal",
        "openai"          to "openai",
        "stripe"          to "stripe",
        "yandex"          to "yandex",
        "юmoney"          to "yoomoney",
        "yoomoney"        to "yoomoney",
    )

    /** Служебные слова биллинга. Смысла не несут, а сравнивать строки мешают. */
    private val NOISE = setOf(
        "vts", "recr", "recur", "recurring", "subsc", "subscription", "sub",
        "payment", "pmt", "purchase", "pos", "ecom",
        "inc", "llc", "ltd", "corp", "co", "gmbh", "technology", "technologies",
        "ооо", "оао", "зао", "пао", "ип",
    )

    /** Телефон поддержки в хвосте: «, 855-836-3987», «, +14158799686». */
    private val PHONE_TAIL = Regex("""[,;]?\s*\+?\d[\d\s\-()]{6,}\s*$""")

    /**
     * Российский хвост «… на 550,00 RUB».
     *
     * Кириллические куски записаны классами обоих регистров вручную. `IGNORE_CASE` в Java по
     * умолчанию складывает регистр ТОЛЬКО у латиницы, поэтому на «НА 550,00 РУБ» флаг бы не помог,
     * а «RUB» он приводит к общему виду корректно — его и оставляем на латинские коды валют.
     */
    private val AMOUNT_TAIL = Regex(
        """\s+[нН][аА]\s+[\d\s.,]+\s*(RUB|USD|EUR|KGS|[рР][уУ][бБ]\.?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val SEPARATORS = Regex("""[\s,;:*#/\\_+()\[\]«»"']+""")
    private val DIGIT_RUN  = Regex("""\d{3,}""")

    /**
     * Название для показа. `null` на входе — `null` на выходе: подставлять «Без названия» здесь
     * нельзя, у вызывающего может быть свой запасной вариант (например, имя категории).
     */
    fun display(raw: String?): String? {
        val source = raw?.trim().orEmpty()
        if (source.isBlank()) return null

        brandOf(source)?.let { return it }

        val cleaned = strip(source)
        return cleaned.ifBlank { source }
    }

    /**
     * Ключ «это одно и то же списание»: сервис + посредник + и то и другое в нижнем регистре.
     * `null`, если опознавать нечего.
     */
    fun groupKey(raw: String?): String? {
        val source    = raw?.trim().orEmpty()
        if (source.isBlank()) return null
        val processor = processorOf(source).orEmpty()
        val brand     = brandOf(source)?.lowercase()

        if (brand == null) {
            val plain = strip(source).lowercase().replace(SEPARATORS, " ").trim()
            return if (plain.isBlank()) null else "$plain||$processor"
        }

        // Уточнение — первое слово, оставшееся после того, как убрали бренд и посредника, обрезанное
        // до четырёх букв. Оно разделяет РАЗНЫЕ продукты одного бренда («ADOBE *CREATIVE CLOUD» и
        // «ADOBE *ACROBAT» — две подписки, а не одна) и при этом переживает обрезку названия банком:
        // «Claude by An» и «Claude by Anth» дают одно и то же «by».
        val qualifier = strip(source).lowercase()
            .split(SEPARATORS)
            .firstOrNull { w ->
                w.isNotBlank() && w != brand && w != processor &&
                    BRANDS.keys.none { it == w } && PROCESSORS.keys.none { it == w }
            }
            ?.take(4)
            .orEmpty()

        return "$brand|$qualifier|$processor"
    }

    /**
     * Регистр снимается ЗАРАНЕЕ, а не флагом `IGNORE_CASE`: в Java он приводит к одному регистру
     * только латиницу, и кириллический бренд («Аеза» против образца «аеза») с ним бы не совпал.
     * `String.lowercase()` знает про Unicode и делает это правильно.
     */
    private fun brandOf(source: String): String? {
        val hay = source.lowercase()
        return BRAND_PATTERNS.firstOrNull { (re, _) -> re.containsMatchIn(hay) }?.second
    }

    private fun processorOf(source: String): String? {
        val hay = source.lowercase()
        return PROCESSORS.entries.firstOrNull { (token, _) -> hay.contains(token) }?.value
    }

    /**
     * Чистка строки, у которой не нашлось известного бренда: снять хвосты, выбросить служебные
     * слова, схлопнуть повтор.
     */
    private fun strip(source: String): String {
        var s = source
        s = PHONE_TAIL.replace(s, "")
        s = AMOUNT_TAIL.replace(s, "")
        // Номер операции, терминала, лицевого счёта: три и больше цифр подряд смысла не несут, а
        // строку загромождают. Двузначные оставляем — в них он бывает («Пятёрочка 12»).
        s = DIGIT_RUN.replace(s, " ")

        val kept = s.split(SEPARATORS)
            // После вырезания длинных чисел от телефона остаются огрызки пунктуации: «8-800-250»
            // превращается в «8- - -». Обрезаем их по краям слова и выбрасываем то, в чём не
            // осталось ни буквы, ни цифры.
            .map { it.trim('.', ',', '-', '–', '—', '·') }
            .filter { w ->
                val bare = w.lowercase()
                bare.isNotEmpty() && bare !in NOISE &&
                    w.any(Char::isLetterOrDigit) &&
                    // Отдельно стоящее число — это номер магазина или терминала, а не название.
                    !w.all(Char::isDigit)
            }
        return dedupeRepeatedPhrase(kept).joinToString(" ").trim(' ', '.', ',', '-')
    }

    /**
     * «Оплата телефона Оплата телефона 919…» → «Оплата телефона».
     *
     * Некоторые банки склеивают заголовок уведомления с его же текстом, и в истории появляется
     * фраза, повторённая дважды подряд. Ищем самый длинный повтор начала строки — так «Оплата
     * телефона Оплата телефона» схлопывается целиком, а не наполовину.
     */
    private fun dedupeRepeatedPhrase(words: List<String>): List<String> {
        for (len in words.size / 2 downTo 1) {
            if (words.subList(0, len) == words.subList(len, len * 2)) {
                return words.subList(0, len) + words.drop(len * 2)
            }
        }
        return words
    }
}
