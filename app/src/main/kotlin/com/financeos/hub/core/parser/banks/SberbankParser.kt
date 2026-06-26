package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import javax.inject.Inject

class SberbankParser @Inject constructor() : BankParser {
    override val bankId = "sberbank"
    override val senderPatterns = listOf(Regex("SBERBANK|900|СБЕРБАНК"))

    // "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
    // "Счёт карты MIR-1238 21:07 Покупка 100р SPORTLOTEREI_SP Баланс: 15 351.89р"
    // [-\s]* handles both "MIR-1238" (dash) and "MIR1234" (no separator) forms.
    // (?:[\d.]+\s+)? makes the date prefix optional (some SMS omit DD.MM.YY, show only HH:MM).
    private val expenseRu = Regex(
        """(?:VISA|MASTERCARD|МИР|MIR)[-\s]*(\d{4})\s+(?:[\d.]+\s+)?[\d:]+\s+(?:Оплата|Покупка|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*р\s+(.+?)\s+(?:Баланс|Остаток):\s*([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    // "VISA1234 18.06.25 12:34 Зачисление 10 000р"
    // NOTE: "Перевод" intentionally excluded — a transfer SMS is frequently OUTGOING and
    // would be misclassified as income (sign inversion), corrupting analytics.
    private val incomeRu = Regex(
        """(?:VISA|MASTERCARD|МИР|MIR)[-\s]*(\d{4})\s+(?:[\d.]+\s+)?[\d:]+\s+(?:Зачисление|Пополнение)\s+([\d\s]+(?:[.,]\d{2})?)\s*р""",
        RegexOption.IGNORE_CASE
    )

    // English-locale Sberbank notifications
    private val expenseEn = Regex(
        """(?:VISA|MC)\s*(\d{4})[^\d]+([\d\s]+\.\d{2})\s*RUB\s+(.+?)\s+Balance[:\s]+([\d\s]+\.\d{2})\s*RUB""",
        RegexOption.IGNORE_CASE
    )

    // Sberbank push format: "[Merchant] [amount] ₽ В запасе: [balance] ₽ [Карта/СЧЁТ] •[last4]"
    // The amount has no mandatory sign — type is inferred from keywords (Зачисление/Покупка/etc.).
    private val pushBalRe  = Regex(
        "В\\s+запасе:\\s*([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*₽",
        RegexOption.IGNORE_CASE,
    )
    private val pushAmtRe  = Regex("([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*₽")
    // "Карта •1234", "СЧЁТ • 4102", or bare "•1234"
    private val pushCardRe = Regex(
        "(?:Карта|СЧЁТ|СЧЕТ)\\s*[*•·]{1,2}\\s*(\\d{4})|[*•·]{1,2}\\s*(\\d{4})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val pushIncomeKw = Regex("(?:Зачисление|Пополнение)", RegexOption.IGNORE_CASE)

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        // Transfers (перевод/СБП) must be recognised before expense/income so they are not
        // misread as a purchase or as inverted-sign income.
        TransferPatterns.detect(body)?.let { r ->
            return TransferPatterns.toParsed(r, bankId, body, smsId, timestampMillis)
        }

        expenseRu.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.EXPENSE,
                amountKopecks   = AmountParser.toKopecks(amt),
                merchant        = merchant.trim(),
                cardMask        = card,
                balanceKopecks  = AmountParser.toKopecks(bal),
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        incomeRu.find(body)?.let { m ->
            val (card, amt) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.INCOME,
                amountKopecks   = AmountParser.toKopecks(amt),
                merchant        = null,
                cardMask        = card,
                balanceKopecks  = null,
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        expenseEn.find(body)?.let { m ->
            val (card, amt, merchant, bal) = m.destructured
            return ParsedTransaction(
                type            = TransactionType.EXPENSE,
                amountKopecks   = AmountParser.toKopecks(amt),
                merchant        = merchant.trim(),
                cardMask        = card,
                balanceKopecks  = AmountParser.toKopecks(bal),
                timestamp       = timestampMillis,
                bankId          = bankId,
                rawSms          = body,
                smsId           = smsId,
            )
        }

        return parsePush(body, smsId, timestampMillis)
    }

    /**
     * Field-based parser for Sberbank push notifications.
     * Requires "В запасе:" to be present (strong signal this is a transaction push, not marketing).
     * Transaction amount = the last ₽-value before "В запасе:", type inferred from keywords.
     */
    private fun parsePush(body: String, smsId: String, ts: Long): ParsedTransaction? {
        val balMatch = pushBalRe.find(body) ?: return null
        val balance  = AmountParser.toKopecks(balMatch.groupValues[1]).takeIf { it >= 0L } ?: return null

        val bodyBeforeBal = body.substring(0, balMatch.range.first)
        val amtMatch = pushAmtRe.findAll(bodyBeforeBal).lastOrNull() ?: return null
        val amount   = AmountParser.toKopecks(amtMatch.groupValues[1])
        if (amount <= 0L) return null

        val card     = pushCardRe.find(body)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        val isIncome = pushIncomeKw.containsMatchIn(body)
        val merchant = bodyBeforeBal.substring(0, amtMatch.range.first)
            .trim().trim('.', ',', ';', '-', ' ')
            .takeIf { it.isNotBlank() }

        return ParsedTransaction(
            type           = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            amountKopecks  = amount,
            merchant       = merchant,
            cardMask       = card,
            balanceKopecks = balance,
            timestamp      = ts,
            bankId         = bankId,
            rawSms         = body,
            smsId          = smsId,
        )
    }
}
