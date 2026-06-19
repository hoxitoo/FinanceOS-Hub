package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class PostaBankParser @Inject constructor() : BankParser {
    override val bankId = "postabank"
    override val senderPatterns = listOf(Regex("POSTABANK|POSTBANK|ПОЧТА.?БАНК|ПОЧТАБАНК"))

    // "Почта Банк: Покупка 1 500.00 руб. по карте *1234 в МАГАЗИН. Доступно: 5 000.00 руб."
    private val expense = Regex(
        """(?:Покупка|Оплата|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽)[.\s]+(?:по\s+)?(?:карт[еу]\s+)?\*(\d{4}).*?(?:в|у|магазин|торговец)\s+(.+?)[.\s]+(?:Доступно|Остаток|Баланс)[:.\s]+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽)""",
        RegexOption.IGNORE_CASE,
    )

    // "Почта Банк: Зачисление 10 000.00 руб. на карту *1234"
    private val income = Regex(
        """(?:Зачисление|Пополнение|Поступление)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:руб|RUB|₽).*?\*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        expense.find(body)?.let { m ->
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

    private fun parseAmount(s: String): Long {
        val cleaned = s.trim().replace("\\s".toRegex(), "").replace(',', '.')
        return (cleaned.toDouble() * 100).toLong()
    }
}
