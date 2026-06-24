package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

class SberbankParser @Inject constructor() : BankParser {
    override val bankId = "sberbank"
    override val senderPatterns = listOf(Regex("SBERBANK|900|СБЕРБАНК"))

    // "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
    // "Счёт карты MIR-1238 21:07 Покупка 100р SPORTLOTEREI_SP Баланс: 15 351.89р"
    // [-\s]* handles both "MIR-1238" (dash) and "MIR1234" (no separator) forms.
    // (?:[\d.]+\s+)? makes the date prefix optional (some SMS omit DD.MM.YY, show only HH:MM).
    private val expenseRu = Regex(
        """(?:VISA|MASTERCARD|МИР|MIR)[-\s]*(\d{4})\s+(?:[\d.]+\s+)?[\d:]+\s+(?:Оплата|Покупка|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*р\s+(.+?)\s+(?:Баланс|Остаток):\s*([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    // "VISA1234 18.06.25 12:34 Зачисление 10 000р"
    // NOTE: "Перевод" intentionally excluded — a transfer SMS is frequently OUTGOING and
    // would be misclassified as income (sign inversion), corrupting analytics.
    private val incomeRu = Regex(
        """(?:VISA|MASTERCARD|МИР|MIR)[-\s]*(\d{4})\s+(?:[\d.]+\s+)?[\d:]+\s+(?:Зачисление|Пополнение)\s+([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    // English-locale Sberbank notifications
    private val expenseEn = Regex(
        """(?:VISA|MC)\s*(\d{4})[^\d]+([\d\s]+\.\d{2})\s*RUB\s+(.+?)\s+Balance[:\s]+([\d\s]+\.\d{2})\s*RUB""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        // Transfers (перевод/СБП) must be recognised before expense/income so they are not
        // misread as a purchase or as inverted-sign income.
        TransferPatterns.detect(body)?.let { r ->
            return TransferPatterns.toParsed(r, bankId, body, smsId, timestampMillis)
        }

        expenseRu.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.EXPENSE,
                amountKopecks   = AmountParser.toKopecks(amt),
                merchant        = merchant.trim(),
                cardMask        = card,
                balanceKopecks  = AmountParser.toKopecks(bal),
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        incomeRu.find(body)?.let { m ->
            val (card, amt) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.INCOME,
                amountKopecks   = AmountParser.toKopecks(amt),
                merchant        = null,
                cardMask        = card,
                balanceKopecks  = null,
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        expenseEn.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.EXPENSE,
                amountKopecks   = AmountParser.toKopecks(amt),
                merchant        = merchant.trim(),
                cardMask        = card,
                balanceKopecks  = AmountParser.toKopecks(bal),
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        return null
    }
}
