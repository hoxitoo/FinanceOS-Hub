package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class SberbankParser @Inject constructor() : BankParser {
    override val bankId = "sberbank"
    override val senderPatterns = listOf(Regex("SBERBANK|900|СБЕРБАНК"))

    // "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
    private val expenseRu = Regex(
        """(?:VISA|MASTERCARD|МИР|MIR)\s*(\d{4})\s+[\d.]+\s+[\d:]+\s+(?:Оплата|Покупка|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*р\s+(.+?)\s+(?:Баланс|Остаток):\s*([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    // "VISA1234 18.06.25 12:34 Зачисление 10 000р"
    private val incomeRu = Regex(
        """(?:VISA|MASTERCARD|МИР|MIR)\s*(\d{4})\s+[\d.]+\s+[\d:]+\s+(?:Зачисление|Пополнение|Перевод)\s+([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    // English-locale Sberbank notifications
    private val expenseEn = Regex(
        """(?:VISA|MC)\s*(\d{4})[^\d]+([\d\s]+\.\d{2})\s*RUB\s+(.+?)\s+Balance[:\s]+([\d\s]+\.\d{2})\s*RUB""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        expenseRu.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.EXPENSE,
                amountKopecks   = parseRuAmount(amt),
                merchant        = merchant.trim(),
                cardMask        = card,
                balanceKopecks  = parseRuAmount(bal),
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
                amountKopecks   = parseRuAmount(amt),
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
                amountKopecks   = parseEnAmount(amt),
                merchant        = merchant.trim(),
                cardMask        = card,
                balanceKopecks  = parseEnAmount(bal),
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        return null
    }

    private fun parseRuAmount(s: String): Long {
        val cleaned = s.trim().replace("\\s".toRegex(), "").replace(',', '.')
        return (cleaned.toDouble() * 100).toLong()
    }

    private fun parseEnAmount(s: String): Long {
        val cleaned = s.trim().replace("\\s".toRegex(), "")
        return (cleaned.toDouble() * 100).toLong()
    }
}
