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
    // "Забросили/Забросил деньги" is Sberbank's push wording for an inter-bank transfer.
    // The `(?![А-Яа-яёЁ])` tail anchors the stem so a marketing word does NOT match by substring:
    // "бесплатными переводАМИ" must not register as "Перевод" (that false-positive booked a phantom
    // 163 000 ₽ transfer from a T-Bank credit-card offer). `\s` after a stem already provides the
    // boundary; the lookahead covers the bare-noun and trailing-inflection cases.
    private val OUTGOING = ciRegex(
        """(?:Перевод(?![А-Яа-яёЁ])|Перевели(?![А-Яа-яёЁ])|Перечислен|Отправлен\s+перевод|Списание[^.]*перевод|Забросил[аи]?\s+деньги|(?<![А-Яа-яёЁ])СБП(?![А-Яа-яёЁ]))""")

    // Keywords that signal an INCOMING transfer (money arriving on this account).
    //
    // «Перевод … от …» намеренно допускает слова между стеблем и «от»: реальные пуши пишут
    // «Перевод по СБП от АНДРЕЙ ВЛАДИМИРОВИЧ Л.» и «Перевод на сумму 8000.00 RUR из Кошелек ЦУПИС
    // … от Андрей Л. по СБП». Оба входящие, оба раньше падали в OUTGOING по слову «СБП» — и
    // приходящие деньги записывались как уходящие, то есть с обратным знаком.
    //
    // Точки внутри разрешены (в тексте стоит «8000.00»), поэтому ограничителем служит длина и
    // запрет перевода строки, а не «до первой точки». `\s+от\s` требует ОТДЕЛЬНОГО слова «от»:
    // без пробелов оно поймало бы «отправлен», «отказ», «отчёт».
    private val INCOMING = ciRegex(
        """(?:Зачисление\s+перевода|Поступление[^.]*перевод|Перевод(?![А-Яа-яёЁ])[^\n]{0,80}?\s+от\s|Входящий\s+перевод|Вам\s+перевод)""")

    // Destination card / account last-4: "на карту *1234", "на счёт •• 1234", "на карту 1234",
    // and the card-network-prefixed form "на счёт 4*3583" (a leading network digit before the
    // masking glyph). The optional `(?:\d\s*)?` consumes that prefix digit; regex backtracking
    // still lets a bare "на счёт 1234" capture all four digits.
    private val DEST_MASK = ciRegex(
        """на\s+(?:карту|счёт|счет|карте|счёте|счете)\s*(?:\d\s*)?[*•·]{0,2}\s*(\d{4})""")

    // Amount somewhere in the body, with optional currency suffix.
    // Explicitly enumerate horizontal-whitespace separators (space, tab, NBSP U+00A0, narrow NBSP
    // U+202F) instead of using \s, which matches \n and would let the digit span bleed across
    // line boundaries in multiline push bodies → toDoubleOrNull returning null → transfer dropped.
    // Allow 1 or 2 decimal places — some Alfa pushes emit "-468,7 ₽" (one digit).
    // RUR — устаревший код рубля, но Альфа до сих пор шлёт им пуши по СБП («8000.00 RUR»).
    // Без него сумма не находилась вовсе, а `detect` при нулевой сумме возвращает null: перевод
    // молча пропадал целиком, а не приходил с неверным знаком.
    private val AMOUNT = ciRegex(
        "([\\d][\\d \\t\\u00A0\\u202F]*(?:[.,]\\d{1,2})?)[ \\t]*(?:RUB|RUR|₽|руб|р)")

    // Source card mask in common Russian push/SMS formats:
    //   "со счёта 4*1139" / "с карты *1139" (transfer source — listed FIRST so it wins as the
    //   leftmost match over a trailing destination mask), VISA/MIR + 4 digits (Sberbank),
    //   Карта *NNNN or Карта NNNN (Tbank/Alfa SMS), ••NNNN / *NNNN at end of line (Alfa push).
    private val SOURCE_CARD = ciRegex(
        "(?:со?\\s+(?:счёта|счета|карты|карте)\\s*(?:\\d\\s*)?[*•·]{0,2}\\s*(\\d{4})" +
            // «Счёт карты VISA •• 3387» — между сетью и цифрами стоят маскирующие точки, поэтому
            // одного \\s* мало: без них Сбер-пуш о входящем переводе оставался без номера карты и
            // операция не привязывалась ни к какому счёту.
            "|(?:VISA|MIR|МИР|MC|MASTERCARD)\\s*[*•·]{0,2}\\s*(\\d{4})" +
            "|Карта\\s+[*•·]{0,2}\\s*(\\d{4})" +
            "|[*•·]{1,2}\\s*(\\d{4})\\s*$)",
        RegexOption.MULTILINE,
    )

    // Post-operation balance: "Остаток: 16 000 ₽", "Доступно: 5000,00 RUB", "Баланс: 1 234р",
    // "В запасе: 8 001,89 ₽" (Sberbank push format).
    // Same horizontal-whitespace-only restriction as AMOUNT to prevent cross-line capture.
    private val BALANCE = ciRegex(
        "(?:Остаток|Доступно|Баланс|В\\s+запасе):?[ \\t]*([\\d][\\d \\t\\u00A0\\u202F]*(?:[.,]\\d{1,2})?)")

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
