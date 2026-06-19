package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class MtsBankParser @Inject constructor() : BankParser {
    override val bankId = "mtsbank"
    // "900" removed — it is Sberbank's short code, not MTS Bank's.
    override val senderPatterns = listOf(Regex("MTS.?BANK|МТС.?БАНК|MTSB"))

    // "Покупка 1500.00 RUB по карте *1234. Магазин МАГАЗИН. Баланс: 5000.00 RUB"
    private val expense = Regex(
        """(?:Покупка|Оплата|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽|руб).*?\*(\d{4})[.\s]+(?:Магазин|Торговец)?\s*(.+?)[.\s]+(?:Баланс|Остаток|Доступно)[:.\s]+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽|руб)""",
        RegexOption.IGNORE_CASE,
    )

    // "Зачисление 10000.00 RUB на карту *1234"
    private val income = Regex(
        """(?:Зачисление|Пополнение|Поступление)\s+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽|руб).*?\*(\d{4})""",
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

    private fun parseAmount(s: String): Long = AmountParser.toKopecks(s)
}
