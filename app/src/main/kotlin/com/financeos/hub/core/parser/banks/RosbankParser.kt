package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

class RosbankParser @Inject constructor() : BankParser {
    override val bankId = "rosbank"
    override val senderPatterns = listOf(Regex("ROSBANK|РОСБАНК"))

    // "Карта *1234: Покупка 1500.00 RUB МАГАЗИН 01.06.2025 15:30. Остаток: 5000.00 RUB"
    private val expense = Regex(
        """(?:Карта\s+\*(\d{4})[:\s]+)?(?:Покупка|Оплата|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽|руб)[.\s]+(.+?)\s+\d{2}[./]\d{2}[./]\d{2,4}.*?(?:Остаток|Баланс|Доступно):\s*([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽|руб)""",
        RegexOption.IGNORE_CASE,
    )

    // "Карта *1234: Зачисление 10000.00 RUB 01.06.2025 15:30"
    private val income = Regex(
        """(?:Карта\s+\*(\d{4})[:\s]+)?(?:Зачисление|Пополнение|Поступление)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽|руб)""",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        TransferPatterns.detect(body)?.let { r ->
            return TransferPatterns.toParsed(r, bankId, body, smsId, timestampMillis)
        }

        expense.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = parseAmount(amt),
                merchant       = merchant.trim().trimEnd('.', ','),
                cardMask       = card.ifEmpty { null },
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

    private fun parseAmount(s: String): Long = AmountParser.toKopecks(s)
}
