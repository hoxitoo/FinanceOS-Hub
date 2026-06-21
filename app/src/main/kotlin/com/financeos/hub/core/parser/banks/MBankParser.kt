package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

/**
 * МБанк (MBANK, КБ Кыргызстан) — multi-currency push/SMS (USD/KGS/EUR/RUB).
 *
 * Push layout (title joined with text by [PushNotificationListener]):
 *   MBANK
 *   Покупка: 22 USD
 *   GOOGLE *Claude by Anth, 855-836-3987
 *   Карта: *6461
 *   Доступно: 11.96 USD
 *
 * Fields are line-oriented, so we extract each field independently rather than with one
 * monolithic regex: type keyword → amount, the next non-label line → merchant,
 * "Карта: *NNNN" → card mask, "Доступно:" → post-op balance. Amounts/balances are stored
 * as minor units (×100) in the card's own currency; the linked account carries the currency.
 */
class MBankParser @Inject constructor() : BankParser {
    override val bankId = "mbank"
    override val senderPatterns = listOf(Regex("MBANK|МБАНК"))

    // Currency suffix shared by amount + balance (ISO codes and Russian/symbol forms).
    private val cur = "USD|KGS|EUR|RUB|сом|руб|₽|€"

    private val expenseKw = Regex("Покупка|Оплата|Списание|Снятие", RegexOption.IGNORE_CASE)
    private val incomeKw  = Regex("Пополнение|Зачисление|Поступление|Возврат", RegexOption.IGNORE_CASE)

    // Digit-grouping (regular/NBSP/narrow-NBSP spaces) plus at most ONE decimal group, so a
    // stray second separator (e.g. a "." thousands separator) can't poison toDoubleOrNull → 0.
    private val number      = "[\\d][\\d\\s\\u00A0\\u202F]*(?:[.,]\\d{1,2})?"
    private val amountToken = Regex("($number)\\s*(?:$cur)", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("Доступно:?\\s*($number)\\s*(?:$cur)", RegexOption.IGNORE_CASE)
    private val cardRe      = Regex("Карта:?\\s*\\*?\\s*(\\d{4})", RegexOption.IGNORE_CASE)

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        TransferPatterns.detect(body)?.let { r ->
            return TransferPatterns.toParsed(r, bankId, body, smsId, timestampMillis)
        }

        val isExpense = expenseKw.containsMatchIn(body)
        val isIncome  = !isExpense && incomeKw.containsMatchIn(body)
        if (!isExpense && !isIncome) return null

        // Amount = first currency-suffixed number AFTER the type keyword (so we never pick
        // up the "Доступно:" balance, which always comes later in the message).
        val kwMatch = (if (isExpense) expenseKw else incomeKw).find(body) ?: return null
        val afterKw = body.substring(kwMatch.range.last + 1)
        val amount  = amountToken.find(afterKw)?.groupValues?.getOrNull(1)
            ?.let { AmountParser.toKopecks(it) } ?: return null
        if (amount <= 0L) return null

        val cardMask = cardRe.find(body)?.groupValues?.getOrNull(1)
        val balance  = balanceRe.find(body)?.groupValues?.getOrNull(1)?.let { AmountParser.toKopecks(it) }
        val merchant = extractMerchant(body)

        return ParsedTransaction(
            type           = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
            amountKopecks  = amount,
            merchant       = merchant,
            cardMask       = cardMask,
            balanceKopecks = balance,
            timestamp      = timestampMillis,
            bankId         = bankId,
            rawSms         = body,
            smsId          = smsId,
        )
    }

    /** The merchant is the first line after the type keyword that is not itself a labelled field. */
    private fun extractMerchant(body: String): String? {
        val lines = body.split('\n', '\r').map { it.trim() }.filter { it.isNotBlank() }
        val kwIdx = lines.indexOfFirst { expenseKw.containsMatchIn(it) || incomeKw.containsMatchIn(it) }
        if (kwIdx < 0) return null
        return lines.drop(kwIdx + 1).firstOrNull { line ->
            !line.contains("Карта", ignoreCase = true) &&
            !line.contains("Доступно", ignoreCase = true) &&
            !line.contains("Баланс", ignoreCase = true)
        }?.trim()?.trimEnd('.', ',')
    }
}
