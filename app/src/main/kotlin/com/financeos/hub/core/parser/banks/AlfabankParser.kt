package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class AlfabankParser @Inject constructor() : BankParser {
    override val bankId = "alfabank"
    override val senderPatterns = listOf(Regex("ALFABANK|ALFA|АЛЬФА"))

    // "Покупка. Альфа-Банк. Карта *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. Доступно: 10000.00 RUB"
    private val expense = Regex(
        """(?:Покупка|Оплата)[.\s]+Карта\s+\*(\d{4})[.\s]+[\d.]+\s+[\d:]+[.\s]+([\d]+(?:[.,]\d{2})?)\s*(?:RUB|₽)[.\s]+(.+?)[.\s]+(?:Доступно|Баланс):\s*([\d]+(?:[.,]\d{2})?)\s*(?:RUB|₽)""",
        RegexOption.IGNORE_CASE
    )

    // "Зачисление. Альфа-Банк. Карта *1234. 10000.00 RUB."
    private val income = Regex(
        """(?:Зачисление|Пополнение)[.\s]+Карта\s+\*(\d{4})[.\s]+([\d]+(?:[.,]\d{2})?)\s*(?:RUB|₽)""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        expense.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = parseAmount(amt),
                merchant       = merchant.trim().trimEnd('.'),
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
        val cleaned = s.trim().replace(',', '.')
        return (cleaned.toDouble() * 100).toLong()
    }
}
