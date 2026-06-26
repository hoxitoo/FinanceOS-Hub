package com.financeos.hub.core.parser

/**
 * Rejects promotional / marketing bank pushes before any [BankParser] runs.
 *
 * Banks flood users with offer notifications ("Одобрили кредитку — получите карту с лимитом
 * 163 000 ₽, кэшбэком до 30%, бесплатными переводами"). These carry a money-like amount and often
 * a transaction-keyword *substring* (e.g. "переводами" contains "перевод"), so a naive parser books
 * them as real operations — a 163 000 ₽ phantom transfer, in the reported case.
 *
 * The markers below appear ONLY in marketing copy and never in a genuine operation notification
 * (which instead says Покупка/Оплата/Списание/Зачисление + Карта *NNNN + Остаток/Доступно). A single
 * match rejects the whole message. Kept deliberately narrow so a real transaction that merely mentions
 * "кэшбэк 150 ₽" or "доступный лимит" is not dropped.
 */
object PromoFilter {

    private val MARKERS = Regex(
        listOf(
            "одобрил[аи]\\s+(?:кредитк|карт|кредит|заявк)",
            "одобрен[аоы]?\\s+(?:кредитк|карт|кредит)",
            "получите\\s+карт",
            "оформите\\s+карт",
            "оформить\\s+карт",
            "карт[уы]\\s+с\\s+лимитом",
            "с\\s+лимитом\\s+\\d",
            "кэшб[эе]к\\w*\\s+до",
            "кешб[эе]к\\w*\\s+до",
            "до\\s+\\d{1,3}\\s*%",            // "кэшбэком до 30%"
            "\\d{1,3}\\s*%\\s+годовых",
            "беспроцентн",
            "бесплатн\\w*\\s+перевод",        // "бесплатными переводами"
            "специальн\\w*\\s+предложени",
            "выгодн\\w*\\s+предложени",
            "приведи\\s+друг",
            "подключите\\b",
            "успейте\\b",
            "вклад\\w*\\s+под\\s+\\d",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    fun isPromo(body: String): Boolean = MARKERS.containsMatchIn(body)
}
