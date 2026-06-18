package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class VtbParser @Inject constructor() : BankParser {
    override val bankId = "vtb"
    override val senderPatterns = listOf(Regex("VTB|ВТБ"))

    // "ВТБ. Карта *1234. Покупка 500,00 руб. МАГАЗИН 18.06.25 в 12:34. Баланс 9500,00 руб."
    private val expense = Regex(
        """Карта\s+\*(\d{4})[.\s]+(?:Покупка|Оплата|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*руб[.\s]+(.+?)\s+\d{2}\.\d{2}\.\d{2}.*?Баланс\s+([\d\s]+(?:[.,]\d{2})?)\s*руб""",
        RegexOption.IGNORE_CASE
    )

    // "ВТБ. Карта *1234. Зачисление 15000,00 руб."
    private val income = Regex(
        """Карта\s+\*(\d{4})[.\s]+(?:Зачисление|Пополнение)\s+([\d\s]+(?:[.,]\d{2})?)\s*руб""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        expense.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = parseAmount(amt),
                merchant       = merchant.trim(),
                cardMask       = card,
                balanceKopecks = parseAmount(bal),
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        income.find(body)?.let { m ->
            val (card, amt) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.INCOME,
                amountKopecks  = parseAmount(amt),
                merchant       = null,
                cardMask       = card,
                balanceKopecks = null,
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        return null
    }

    private fun parseAmount(s: String): Long {
        val cleaned = s.trim().replace("\\s".toRegex(), "").replace(',', '.')
        return (cleaned.toDouble() * 100).toLong()
    }
}
