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

        // A sender (e.g. the shared "900" short code) may be claimed by more than one
        // bank parser, and a parser that recognises the sender may still fail to match the
        // body. Try every parser that recognises the sender and return the first that
        // actually produces a transaction, instead of giving up after the first match.
        return parsers.asSequence()
            .filter { it.canHandle(sender) }
            .firstNotNullOfOrNull { it.parse(sender, normalizedBody, timestampMillis) }
    }

    fun supportedBanks(): List<String> = parsers.map { it.bankId }
}
