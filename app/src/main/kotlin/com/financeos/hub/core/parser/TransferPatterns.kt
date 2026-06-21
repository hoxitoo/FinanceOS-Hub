package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType

/**
 * Shared, conservative recognition of Russian bank "transfer" wording.
 *
 * A transfer (перевод/перечисление/СБП …) is neither an expense nor income: it moves money
 * between accounts. We only classify as TRANSFER when a transfer keyword is clearly present,
 * otherwise the caller falls through to its existing expense/income regexes.
 *
 * The destination card last-4 is captured opportunistically ("на карту *1234" / "на счёт •• 1234");
 * when the SMS format does not expose it, [Result.counterpartyMask] is null and keyword routing is used.
 */
object TransferPatterns {

    // Keywords that signal an OUTGOING transfer (money leaving this account).
    private val OUTGOING = Regex(
        """(?:Перевод|Перевели|Перечисление|Отправлен\s+перевод|Перевод\s+СБП|СБП|Списание[^.]*перевод)""",
        RegexOption.IGNORE_CASE,
    )

    // Keywords that signal an INCOMING transfer (money arriving on this account).
    private val INCOMING = Regex(
        """(?:Зачисление\s+перевода|Поступление[^.]*перевод|Перевод\s+от|Входящий\s+перевод|Вам\s+перевод)""",
        RegexOption.IGNORE_CASE,
    )

    // Destination card / account last-4: "на карту *1234", "на счёт •• 1234", "на карту 1234".
    private val DEST_MASK = Regex(
        """на\s+(?:карту|счёт|счет|карте|счёте|счете)\s*[*•·]{0,2}\s*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    // Amount somewhere in the body, with optional currency suffix.
    // Whitespace class covers regular space plus NBSP (U+00A0) and narrow NBSP (U+202F) separators.
    private val AMOUNT = Regex(
        "([\\d][\\d\\s\\u00A0\\u202F]*(?:[.,]\\d{2})?)\\s*(?:RUB|₽|руб|р)",
        RegexOption.IGNORE_CASE,
    )

    data class Result(
        val amountKopecks: Long,
        val outgoing: Boolean,
        val counterpartyMask: String?,
        val cardMask: String?,
    )

    /**
     * Returns a TRANSFER [Result] if [body] clearly describes a transfer, else null.
     * [ownCardMask] is the source card last-4 if the caller already extracted it.
     */
    fun detect(body: String, ownCardMask: String? = null): Result? {
        val incoming = INCOMING.containsMatchIn(body)
        val outgoing = !incoming && OUTGOING.containsMatchIn(body)
        if (!incoming && !outgoing) return null

        val amt = AMOUNT.find(body)?.groupValues?.getOrNull(1)?.let { AmountParser.toKopecks(it) } ?: 0L
        if (amt <= 0L) return null

        val dest = DEST_MASK.find(body)?.groupValues?.getOrNull(1)
        return Result(
            amountKopecks    = amt,
            outgoing         = outgoing,
            counterpartyMask = dest,
            cardMask         = ownCardMask,
        )
    }

    /** Convenience: build a TRANSFER ParsedTransaction from a [detect] result. */
    fun toParsed(
        r: Result,
        bankId: String,
        body: String,
        smsId: String,
        timestampMillis: Long,
    ): ParsedTransaction = ParsedTransaction(
        type             = TransactionType.TRANSFER,
        amountKopecks    = r.amountKopecks,
        merchant         = if (r.outgoing) "Перевод" else "Перевод (входящий)",
        cardMask         = r.cardMask,
        balanceKopecks   = null,
        timestamp        = timestampMillis,
        bankId           = bankId,
        rawSms           = body,
        smsId            = smsId,
        counterpartyMask = r.counterpartyMask,
        outgoing         = r.outgoing,
    )
}
