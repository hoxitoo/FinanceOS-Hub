package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class TbankParser @Inject constructor() : BankParser {
    override val bankId = "tbank"
    override val senderPatterns = listOf(Regex("TBANK|TINKOFF|Т-БАНК|900"))

    // "Оплата 1500,00 RUB. Кафе Урюк. Карта *1234. Баланс: 5000,00 RUB"
    private val expense = Regex(
        """(?:Оплата|Покупка|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽)[.\s]+(.+?)[.\s]+(?:Карта\s+\*(\d{4}))[.\s]+(?:Баланс|Доступно):\s*([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽)""",
        RegexOption.IGNORE_CASE
    )

    // "Пополнение 10000,00 RUB. Карта *1234. Баланс: 15000,00 RUB"
    private val income = Regex(
        """(?:Пополнение|Зачисление|Перевод)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽)[.\s]+(?:Карта\s+\*(\d{4}))?""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        expense.find(body)?.let { m ->
            val (amt, merchant, card, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = parseAmount(amt),
                merchant       = merchant.trim().trimEnd('.'),
                cardMask       = card.ifEmpty { null },
                balanceKopecks = parseAmount(bal),
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        income.find(body)?.let { m ->
            val (amt, card) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.INCOME,
                amountKopecks  = parseAmount(amt),
                merchant       = null,
                cardMask       = card.ifEmpty { null },
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
