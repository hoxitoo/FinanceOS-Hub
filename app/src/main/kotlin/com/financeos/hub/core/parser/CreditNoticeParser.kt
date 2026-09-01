package com.financeos.hub.core.parser

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Recognises a bank's credit-card PAYMENT REMINDER — a statement about the card, not an operation.
 *
 * Verified against the real Сбер push:
 *
 *   Платёж по кредитной карте
 *   Внесите платёж 373,98р до 31.08.26 на кредитную карту, чтобы не допустить просрочки
 *   и продолжать пользоваться беспроцентным периодом.
 *
 * Two things make this worth parsing separately rather than through a [BankParser]:
 *
 *  1. **It must never become a transaction.** No money moved. Booking 373,98 ₽ as an expense would
 *     both invent a purchase and double-count the real payment when it later arrives.
 *  2. **[PromoFilter] rejects it.** The phrase «беспроцентным периодом» trips the marketing marker
 *     `беспроцентн`, so by the time any parser runs the message is already gone. This parser is
 *     therefore consulted BEFORE the promo filter — safe because the pattern demands the literal
 *     «внесите платёж … до <date>» shape, which marketing copy does not have.
 *
 * What it yields is better than anything the app can infer: the bank's OWN obligatory payment and
 * its OWN deadline, instead of a due date derived from a statement day the user typed in.
 */
object CreditNoticeParser {

    /** A credit card's obligatory payment as the bank stated it. */
    data class CreditNotice(
        val amountKopecks: Long,
        /** Deadline, epoch millis at the start of that day in the device's zone. */
        val dueAtMillis  : Long,
        val bankId       : String,
        val rawText      : String,
    )

    /** Only messages that are clearly about a credit card — never a loan or a regular account. */
    private val CREDIT_CONTEXT = ciRegex(
        "кредитн[а-яё]*\\s+карт[а-яё]*")

    /**
     * «Внесите платёж 373,98р до 31.08.26», also «внести 1 200 ₽ до 05.09.2026».
     * The amount allows the bare «р» suffix Сбер uses here, not just «₽».
     */
    private val PAYMENT = ciRegex(
        "внес(?:ите|ти)\\s+(?:платёж|платеж|)\\s*([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*(?:₽|руб[а-яё]*|р)(?![а-яё])" +
            "[^\\d]{0,40}?до\\s+(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")

    private val SENDERS = ciRegex("SBERBANK|900|СБЕРБАНК")

    fun parse(sender: String, body: String, zone: ZoneId = ZoneId.systemDefault()): CreditNotice? {
        if (!SENDERS.containsMatchIn(sender)) return null
        if (!CREDIT_CONTEXT.containsMatchIn(body)) return null

        val m = PAYMENT.find(body) ?: return null
        val amount = AmountParser.toKopecks(m.groupValues[1])
        if (amount <= 0L) return null

        val due = parseDate(m.groupValues[2], zone) ?: return null
        return CreditNotice(
            amountKopecks = amount,
            dueAtMillis   = due,
            bankId        = "sberbank",
            rawText       = body,
        )
    }

    /** «31.08.26» / «31.08.2026» → start of that day. Returns null on an impossible date. */
    private fun parseDate(raw: String, zone: ZoneId): Long? {
        val pattern = if (raw.length == 10) "dd.MM.yyyy" else "dd.MM.yy"
        return runCatching {
            LocalDate
                .parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
