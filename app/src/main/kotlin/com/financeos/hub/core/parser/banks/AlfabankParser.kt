package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import javax.inject.Inject

class AlfabankParser @Inject constructor() : BankParser {
    override val bankId = "alfabank"
    override val senderPatterns = listOf(Regex("ALFABANK|ALFA|АЛЬФА"))

    // SMS: "Покупка. Альфа-Банк. Карта *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. Доступно: 10000.00 RUB"
    private val smsSExpense = Regex(
        """(?:Покупка|Оплата)[.\s]+Карта\s+\*(\d{4})[.\s]+[\d.]+\s+[\d:]+[.\s]+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽)[.\s]+(.+?)[.\s]+(?:Доступно|Баланс):\s*([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽)""",
        RegexOption.IGNORE_CASE
    )

    // SMS: "Зачисление. Альфа-Банк. Карта *1234. 10000.00 RUB."
    private val smsSIncome = Regex(
        """(?:Зачисление|Пополнение)[.\s]+Карта\s+\*(\d{4})[.\s]+([\d\s]+(?:[.,]\d{2})?)\s*(?:RUB|₽)""",
        RegexOption.IGNORE_CASE
    )

    // Push expense: "-43 ₽. Транспорт Перми Остаток: 479,64 ₽; ••2548"
    private val pushExpense = Regex(
        """[-−]([\d\s]+(?:[.,]\d{2})?)\s*₽[.\s]+(.+?)\s+Остаток:\s*([\d\s]+(?:[.,]\d{2})?)\s*₽[;,\s]*[•·]{2}(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    // Push income: "+5 000 ₽. Пополнение Остаток: 10 000 ₽; ••2548"
    private val pushIncome = Regex(
        """[+]([\d\s]+(?:[.,]\d{2})?)\s*₽[.\s]+(.+?)\s+Остаток:\s*([\d\s]+(?:[.,]\d{2})?)\s*₽[;,\s]*[•·]{2}(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        smsSExpense.find(body)?.let { m ->
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

        smsSIncome.find(body)?.let { m ->
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

        pushExpense.find(body)?.let { m ->
            val (amt, merchant, bal, card) = m.destructured
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

        pushIncome.find(body)?.let { m ->
            val (amt, merchant, bal, card) = m.destructured
            return ParsedTransaction(
                type           = TransactionType.INCOME,
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

        return null
    }

    private fun parseAmount(s: String): Long = AmountParser.toKopecks(s)
}
