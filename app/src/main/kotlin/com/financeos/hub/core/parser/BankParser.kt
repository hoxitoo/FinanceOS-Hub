package com.financeos.hub.core.parser

interface BankParser {
    val bankId: String
    val senderPatterns: List<Regex>

    fun canHandle(sender: String): Boolean =
        senderPatterns.any { it.containsMatchIn(sender.uppercase()) }

    fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction?
}
