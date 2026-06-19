package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class RosselkhozParser @Inject constructor() : BankParser {
    override val bankId = "rosselkhoz"
    override val senderPatterns = listOf(Regex("RSHB|РСХБ|ROSSELKHOZ|РОССЕЛЬХОЗ"))

    // "RSHB: Покупка 1 500.00 руб по карте *1234. МАГАЗИН. Баланс: 5 000.00 руб."
    private val expense1 = Regex(
        """(?:Покупка|Оплата|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽|р)[.\s]+(?:по\s+)?(?:карт[еу]\s+)?\*(\d{4})[.\s]+(.+?)[.\s]+(?:Баланс|Остаток|Доступно)[:.\s]+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽|р)""",
        RegexOption.IGNORE_CASE,
    )

    // "РСХБ: Покупка на 1 500,00 р. Карта *1234. Магазин МАГАЗИН. Баланс 5 000,00 р."
    private val expense2 = Regex(
        """(?:Покупка|Оплата).*?(?:на\s+)?([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽|р)[.\s]+Карта\s+\*(\d{4})[.\s]+(?:Магазин|Торговец)?\s*(.+?)[.\s]+(?:Баланс|Остаток|Доступно)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽|р)""",
        RegexOption.IGNORE_CASE,
    )

    // "RSHB: Зачисление 10 000.00 руб на счет *1234"
    private val income = Regex(
        """(?:Зачисление|Пополнение|Поступление)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽|р).*?\*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        expense1.find(body)?.let { m ->
            val (amt, card, merchant, bal) = m.destructured
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
            val (amt, card, merchant, bal) = m.destructured
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
            val (amt, card) = m.destructured
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
