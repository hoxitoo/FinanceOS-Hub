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

    // Destination card / account last-4: "на карту *1234", "на счёт •• 1234", "на карту 1234",
    // and the card-network-prefixed form "на счёт 4*3583" (a leading network digit before the
    // masking glyph). The optional `(?:\d\s*)?` consumes that prefix digit; regex backtracking
    // still lets a bare "на счёт 1234" capture all four digits.
    private val DEST_MASK = Regex(
        """на\s+(?:карту|счёт|счет|карте|счёте|счете)\s*(?:\d\s*)?[*•·]{0,2}\s*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    // Amount somewhere in the body, with optional currency suffix.
    // Whitespace class covers regular space plus NBSP (U+00A0) and narrow NBSP (U+202F) separators.
    // Allow 1 or 2 decimal places — some Alfa pushes emit "-468,7 ₽" (one digit).
    private val AMOUNT = Regex(
        "([\\d][\\d\\s\\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*(?:RUB|₽|руб|р)",
        RegexOption.IGNORE_CASE,
    )

    // Source card mask in common Russian push/SMS formats:
    //   "со счёта 4*1139" / "с карты *1139" (transfer source — listed FIRST so it wins as the
    //   leftmost match over a trailing destination mask), VISA/MIR + 4 digits (Sberbank),
    //   Карта *NNNN or Карта NNNN (Tbank/Alfa SMS), ••NNNN / *NNNN at end of line (Alfa push).
    private val SOURCE_CARD = Regex(
        "(?:со?\\s+(?:счёта|счета|карты|карте)\\s*(?:\\d\\s*)?[*•·]{0,2}\\s*(\\d{4})" +
            "|(?:VISA|MIR|МИР|MC|MASTERCARD)\\s*(\\d{4})" +
            "|Карта\\s+[*•·]{0,2}\\s*(\\d{4})" +
            "|[*•·]{1,2}\\s*(\\d{4})\\s*$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    // Post-operation balance: "Остаток: 16 000 ₽", "Доступно: 5000,00 RUB", "Баланс: 1 234р"
    private val BALANCE = Regex(
        "(?:Остаток|Доступно|Баланс):?\\s*([\\d][\\d\\s\\u00A0\\u202F]*(?:[.,]\\d{1,2})?)",
        RegexOption.IGNORE_CASE,
    )

    data class Result(
        val amountKopecks: Long,
        val outgoing: Boolean,
        val counterpartyMask: String?,
        val cardMask: String?,
        val balanceKopecks: Long? = null,
    )

    /**
     * Returns a TRANSFER [Result] if [body] clearly describes a transfer, else null.
     * [ownCardMask] overrides the auto-extracted source card; pass it when the bank parser
     * already knows the source card from a bank-specific pattern.
     */
    fun detect(body: String, ownCardMask: String? = null): Result? {
        val incoming = INCOMING.containsMatchIn(body)
        val outgoing = !incoming && OUTGOING.containsMatchIn(body)
        if (!incoming && !outgoing) return null

        val amt = AMOUNT.find(body)?.groupValues?.getOrNull(1)?.let { AmountParser.toKopecks(it) } ?: 0L
        if (amt <= 0L) return null

        val dest = DEST_MASK.find(body)?.groupValues?.getOrNull(1)
        // Use caller-supplied mask, else try common bank push/SMS card-mask patterns.
        val resolvedMask = ownCardMask ?: SOURCE_CARD.find(body)?.let { m ->
            m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        }
        val balance = BALANCE.find(body)?.groupValues?.getOrNull(1)
            ?.let { AmountParser.toKopecks(it) }?.takeIf { it >= 0L }
        return Result(
            amountKopecks    = amt,
            outgoing         = outgoing,
            counterpartyMask = dest,
            cardMask         = resolvedMask,
            balanceKopecks   = balance,
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
        balanceKopecks   = r.balanceKopecks,
        timestamp        = timestampMillis,
        bankId           = bankId,
        rawSms           = body,
        smsId            = smsId,
        counterpartyMask = r.counterpartyMask,
        outgoing         = r.outgoing,
    )
}
