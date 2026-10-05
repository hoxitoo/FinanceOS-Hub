package com.financeos.hub.core.invest

/**
 * Группа бумаги для экрана, как у БКС: «Валюта / Акции / Облигации / Фонды / Внебиржевые».
 *
 * Пуш брокера тип бумаги НЕ сообщает («4760 лотов LQDT»), а котировок из сети у приложения нет.
 * Поэтому тип определяется по тикеру — небольшим справочником и узнаваемыми формами, и только там,
 * где ошибка маловероятна. Чего не узнали — в «Прочее»: честная куча лучше неверной полки.
 *
 * Список фондов — фонды денежного рынка и популярные БПИФ Мосбиржи. Новый фонд — новая строка здесь
 * и в тесте. Котировки из сети (бэклог) дадут тип точно, и тогда справочник станет запасным.
 */
enum class SecurityGroup(val title: String) {
    CURRENCY("Валюта"),
    SHARES("Акции"),
    BONDS("Облигации"),
    FUNDS("Фонды"),
    OTC("Внебиржевые активы"),
    OTHER("Прочее"),
}

object SecurityGroups {

    /** Фонды Мосбиржи: денежный рынок, золото, индексы. Тикеры — как их печатает брокер. */
    private val FUNDS = setOf(
        // Денежный рынок
        "LQDT", "TMON", "SBMM", "AKMM", "GPBM", "BCSD", "AMNR", "CASH", "MONY", "SCLI",
        // Золото и сырьё
        "GOLD", "TGLD", "SBGD", "AKGD", "VTBG",
        // Индексы и смешанные
        "EQMX", "TMOS", "SBMX", "AKME", "TRUR", "TBRU", "SBRB", "SBGB", "AKMB", "OBLG", "INFL",
        "TDIV", "DIVD", "MKBD", "RCMX", "TEUR", "TUSD", "AKMP", "BOND", "SBCB", "SBHI", "SBWS",
        "TPAY", "GROD", "SBSC", "AKFB", "TLCB", "TBEU", "TOFZ", "TITR", "TRND", "TSPX", "AKUP",
        "AKQU", "AKSP", "SBRI", "SBPS", "SBDS", "SBSP", "SBMB", "SBRS", "BCSB", "BCSG", "BCSR",
    )

    /**
     * Валютные инструменты биржи: «USD000UTSTOM», «CNYRUB_TOM», «EUR_RUB__TOM», голые коды и
     * «USD000SMALL» / «CNY000SMALL» — так БКС называет остаток валюты на счёте (центы и фэни, которые
     * лотом не купишь).
     */
    private val CURRENCY = Regex("""^(?:USD|EUR|CNY|HKD|GBP|CHF|JPY|KZT|TRY|AED|BYN|AMD|KGS)(?:(?:000UTS|000000|RUB|_RUB)[_A-Z]*TO[MD]|000SMALL)$""")
    private val CURRENCY_CODES = setOf("USD", "EUR", "CNY", "HKD", "GBP", "CHF", "JPY", "KZT", "TRY", "AED", "BYN", "AMD", "KGS")

    /** ОФЗ «SU26238RMFS4» и облигации с ISIN «RU000A105C93». */
    private val BONDS = Regex("""^(?:SU\d{5}[A-Z]{4}\d|RU000[A-Z0-9]{7})$""")

    /** Бумаги, которыми торгуют вне биржи: «3800_HK» (так её пишет БКС). */
    private val OTC = Regex("""^\d{3,5}_[A-Z]{2}$""")

    /**
     * Акция Мосбиржи — 4 латинские буквы, у привилегированной — «P» пятой: SBER, SBERP, GAZP.
     * Это ЕДИНСТВЕННАЯ догадка в справочнике: у фондов тоже четыре буквы, и фонд, которого нет в
     * [FUNDS], ляжет в «Акции». Цифры при этом верны — неверна только полка; лечится строкой в [FUNDS].
     */
    private val SHARES = Regex("""^[A-Z]{4}P?$""")

    fun of(ticker: String): SecurityGroup {
        val t = ticker.trim().uppercase()
        return when {
            t in FUNDS                                -> SecurityGroup.FUNDS
            t in CURRENCY_CODES || CURRENCY.matches(t) -> SecurityGroup.CURRENCY
            BONDS.matches(t)                          -> SecurityGroup.BONDS
            OTC.matches(t)                            -> SecurityGroup.OTC
            SHARES.matches(t)                         -> SecurityGroup.SHARES
            else                                      -> SecurityGroup.OTHER
        }
    }

    /**
     * Валюта, которую ОБОЗНАЧАЕТ тикер: «USD000SMALL» → «USD», «CNYRUB_TOM» → «CNY», «USD» → «USD».
     * `null` — тикер не валютный. Нужна ручному вводу: валюта на счёте — это деньги в своей валюте
     * (дробная сумма, «0,41 $»), а не бумага в штуках.
     */
    fun cashCurrency(ticker: String): String? {
        val t = ticker.trim().uppercase()
        if (t in CURRENCY_CODES) return t
        if (!CURRENCY.matches(t)) return null
        return t.take(3)
    }

    /** Название валюты денег на счёте, как в приложении брокера. */
    fun currencyName(code: String): String = when (code.uppercase()) {
        "RUB" -> "Российский рубль"
        "USD" -> "Доллар США"
        "EUR" -> "Евро"
        "CNY" -> "Китайский юань"
        "HKD" -> "Гонконгский доллар"
        "GBP" -> "Фунт стерлингов"
        "CHF" -> "Швейцарский франк"
        "KZT" -> "Казахстанский тенге"
        "KGS" -> "Киргизский сом"
        else  -> code.uppercase()
    }
}
