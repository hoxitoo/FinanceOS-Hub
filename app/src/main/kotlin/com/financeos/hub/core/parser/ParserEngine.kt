package com.financeos.hub.core.parser

import javax.inject.Inject

class ParserEngine @Inject constructor(
    private val parsers: @JvmSuppressWildcards Set<BankParser>,
) {
    fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? =
        parsers.firstOrNull { it.canHandle(sender) }
            ?.parse(sender, body, timestampMillis)

    fun supportedBanks(): List<String> = parsers.map { it.bankId }
}
