package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

class GazprombankParser @Inject constructor() : BankParser {
    override val bankId = "gazprombank"
    override val senderPatterns = listOf(Regex("GAZPROMBANK|GPB|ГАЗПРОМБАНК"))

    // "GPB. Операция по карте *1234. Списание 2500,00 руб. МАГАЗИН. Баланс 7500,00 руб."
    private val expense = Regex(
        """карте\s+\*(\d{4})[.\s]+(?:Списание|Покупка|Оплата)\s+([\d\s]+(?:[.,]\d{2})?)\s*руб[.\s]+(.+?)[.\s]+Баланс\s+([\d\s]+(?:[.,]\d{2})?)\s*руб""",
        RegexOption.IGNORE_CASE
    )

    // "GPB. Операция по карте *1234. Зачисление 20000,00 руб."
    private val income = Regex(
        """карте\s+\*(\d{4})[.\s]+(?:Зачисление|Пополнение)\s+([\d\s]+(?:[.,]\d{2})?)\s*руб""",
        RegexOption.IGNORE_CASE
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

    private fun parseAmount(s: String): Long = AmountParser.toKopecks(s)
}
