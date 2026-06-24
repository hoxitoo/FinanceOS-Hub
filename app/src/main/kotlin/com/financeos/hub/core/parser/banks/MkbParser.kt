package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

/**
 * МКБ (Московский Кредитный Банк).
 *
 * Push / SMS body formats:
 *   Expense : "Карта *5933 Покупка 1500р Магазин Остаток 24686.88р"
 *   Income  : "Карта *5933 Пополнение 26186.88р Остаток 26186.88р"
 *   ATM     : "Карта *5933 Снятие 5000р Остаток 21186.88р"
 *
 * The push title ("Пополнение" / "Покупка") is joined with the body by PushNotificationListener,
 * so the regex finds the pattern anywhere in the combined string.
 */
class MkbParser @Inject constructor() : BankParser {
    override val bankId = "mkb"
    override val senderPatterns = listOf(Regex("MKB|МКБ|MKBMOBILE|MKREDIT"))

    private val expense = Regex(
        """Карта\s+\*(\d{4})\s+(?:Покупка|Оплата|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*р\s+(.+?)\s+Остаток\s+([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    private val income = Regex(
        """Карта\s+\*(\d{4})\s+(?:Пополнение|Зачисление)\s+([\d\s]+(?:[.,]\d{2})?)\s*р(?:\s+Остаток\s+([\d\s]+(?:[.,]\d{2})?)\s*р)?""",
        RegexOption.IGNORE_CASE
    )

    private val atm = Regex(
        """Карта\s+\*(\d{4})\s+Снятие\s+([\d\s]+(?:[.,]\d{2})?)\s*р(?:\s+Остаток\s+([\d\s]+(?:[.,]\d{2})?)\s*р)?""",
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
                amountKopecks  = AmountParser.toKopecks(amt),
                merchant       = merchant.trim(),
                cardMask       = card,
                balanceKopecks = AmountParser.toKopecks(bal),
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        income.find(body)?.let { m ->
            val (card, amt, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.INCOME,
                amountKopecks  = AmountParser.toKopecks(amt),
                merchant       = null,
                cardMask       = card,
                balanceKopecks = bal.takeIf { it.isNotBlank() }?.let { AmountParser.toKopecks(it) },
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        atm.find(body)?.let { m ->
            val (card, amt, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = AmountParser.toKopecks(amt),
                merchant       = "Снятие наличных",
                cardMask       = card,
                balanceKopecks = bal.takeIf { it.isNotBlank() }?.let { AmountParser.toKopecks(it) },
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        return null
    }
}
