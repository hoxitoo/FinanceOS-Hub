package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

class RaiffeisenParser @Inject constructor() : BankParser {
    override val bankId = "raiffeisen"
    // "RSB" removed — that is Russian Standard Bank's code, not Raiffeisen's.
    override val senderPatterns = listOf(Regex("RAIFFEISEN|РАЙФФАЙЗЕН|RAIF"))

    // "Оплата по карте *1234 на 1 500.00 ₽ в МАГАЗИН. Доступно: 5 000.00 ₽"
    private val expense1 = Regex(
        """(?:Оплата|Покупка|Списание).*?\*(\d{4}).*?(?:на\s+)?([\d\s]+(?:[.,]\d{2})?)\s*(?:₽|RUB|руб)[.\s]+(?:в|у)\s+(.+?)[.\s]+(?:Доступно|Остаток|Баланс)[:.\s]+([\d\s]+(?:[.,]\d{2})?)\s*(?:₽|RUB|руб)""",
        RegexOption.IGNORE_CASE,
    )

    // "Raiffeisen: карта *1234, покупка на сумму 1500,00 RUB, магазин МАГАЗИН, доступно 5000,00 RUB"
    private val expense2 = Regex(
        """карта\s+\*(\d{4})[,.\s]+(?:покупка|оплата).*?(?:сумму?\s+)?([\d\s]+(?:[.,]\d{2})?)\s*(?:₽|RUB|руб)[,.\s]+(?:магазин|торговец|merchant)?\s*(.+?)[,.\s]+(?:доступно|остаток|баланс)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:₽|RUB|руб)""",
        RegexOption.IGNORE_CASE,
    )

    // "Пополнение счёта *1234 на 10 000.00 ₽"
    private val income = Regex(
        """(?:Пополнение|Зачисление|Поступление).*?\*(\d{4}).*?(?:на\s+)?([\d\s]+(?:[.,]\d{2})?)\s*(?:₽|RUB|руб)""",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        TransferPatterns.detect(body)?.let { r ->
            return TransferPatterns.toParsed(r, bankId, body, smsId, timestampMillis)
        }

        expense1.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = parseAmount(amt),
                merchant       = merchant.trim().trimEnd('.', ','),
                cardMask       = card,
                balanceKopecks = parseAmount(bal),
                timestamp      = timestampMillis,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
            )
        }

        expense2.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.EXPENSE,
                amountKopecks  = parseAmount(amt),
                merchant       = merchant.trim().trimEnd('.', ','),
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
