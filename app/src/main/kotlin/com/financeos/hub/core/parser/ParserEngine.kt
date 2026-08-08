package com.financeos.hub.core.parser

import javax.inject.Inject

class ParserEngine @Inject constructor(
    private val parsers: @JvmSuppressWildcards Set<BankParser>,
) {
    private val nbsp       = 0x00A0.toChar()   // non-breaking space
    private val narrowNbsp = 0x202F.toChar()   // narrow no-break space

    fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        // Russian banks routinely use non-breaking (U+00A0) and narrow no-break (U+202F)
        // spaces as thousand separators. Regex \s does not match these, so normalise them
        // to a regular space once here so every parser's amount patterns work unchanged.
        val normalizedBody = body.replace(nbsp, ' ').replace(narrowNbsp, ' ')

        // Drop marketing/promo pushes ("Одобрили кредитку … получите карту с лимитом 163 000 ₽ …")
        // before any parser runs. They carry a money amount and a transaction-keyword substring,
        // so without this guard they get booked as phantom transfers/expenses across every bank.
        if (PromoFilter.isPromo(normalizedBody)) return null

        // A sender (e.g. the shared "900" short code) may be claimed by more than one
        // bank parser, and a parser that recognises the sender may still fail to match the
        // body. Try every parser that recognises the sender and return the first that
        // actually produces a transaction, instead of giving up after the first match.
        return parsers.asSequence()
            .filter { it.canHandle(sender) }
            .firstNotNullOfOrNull { it.parse(sender, normalizedBody, timestampMillis) }
    }

    /**
     * Recognises a credit-card payment reminder — a statement about a card, not an operation.
     *
     * A separate entry point from [parse] for two reasons: it must never produce a transaction,
     * and [PromoFilter] would otherwise reject the message outright (Сбер's reminder ends with
     * «беспроцентным периодом», which trips the marketing marker `беспроцентн`). Skipping the
     * promo filter is safe here because [CreditNoticeParser] demands the literal
     * «внесите платёж … до <дата>» shape, which no marketing copy has.
     *
     * Callers use it as a fallback: when [parse] returns null the message may still be a notice.
     */
    fun parseCreditNotice(sender: String, body: String): CreditNoticeParser.CreditNotice? {
        val normalizedBody = body.replace(nbsp, ' ').replace(narrowNbsp, ' ')
        return CreditNoticeParser.parse(sender, normalizedBody)
    }

    fun supportedBanks(): List<String> = parsers.map { it.bankId }
}
