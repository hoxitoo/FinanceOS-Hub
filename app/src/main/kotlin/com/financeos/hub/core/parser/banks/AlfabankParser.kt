package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.TransferPatterns
import com.financeos.hub.core.parser.ciRegex
import javax.inject.Inject

class AlfabankParser @Inject constructor() : BankParser {
    override val bankId = "alfabank"
    override val senderPatterns = listOf(Regex("ALFABANK|ALFA|АЛЬФА"))

    // SMS: "Покупка. Альфа-Банк. Карта *1234. 18.06.2025 12:34:56. 1500.00 RUB. МАГАЗИН. Доступно: 10000.00 RUB"
    private val smsSExpense = ciRegex(
        """(?:Покупка|Оплата)[.\s]+Карта\s+\*(\d{4})[.\s]+[\d.]+\s+[\d:]+[.\s]+([\d\s]+(?:[.,]\d{1,2})?)\s*(?:RUB|₽)[.\s]+(.+?)[.\s]+(?:Доступно|Баланс):\s*([\d\s]+(?:[.,]\d{1,2})?)\s*(?:RUB|₽)""")

    // SMS: "Зачисление. Альфа-Банк. Карта *1234. 10000.00 RUB."
    private val smsSIncome = ciRegex(
        """(?:Зачисление|Пополнение)[.\s]+Карта\s+\*(\d{4})[.\s]+([\d\s]+(?:[.,]\d{1,2})?)\s*(?:RUB|₽)""")

    // Push is field-oriented and varies a lot (amount may carry 0/1/2 decimals, "Остаток"
    // and the card mask are sometimes absent), so we extract each field independently rather
    // than with one rigid regex. The old fixed-2-decimal regex silently dropped pushes like
    // "-468,7 ₽. Другое Остаток: 3 621,04 ₽; ··2548" (one decimal digit → no match at all).
    private val pushAmount = Regex("""([-−+])\s*([\d\s]+(?:[.,]\d{1,2})?)\s*₽""")
    private val ostatokRe  = ciRegex("""Остаток:\s*([\d\s]+(?:[.,]\d{1,2})?)\s*₽""")
    // Card mask: "··2548" / "••2548" / "*2548" anywhere in the body.
    // Require the masking glyph immediately before the digits and a negative lookahead (?!\d) so we
    // don't capture the middle of a longer digit run — but no $ anchor, because the push body is
    // built by joining the notification title and text, so the mask may not be at the very end.
    private val pushMask   = Regex("""[*•·]{1,2}\s*(\d{4})(?!\d)""")
    // Strip "··NNNN" card glyph from the extracted merchant string.
    private val maskTail   = Regex("""[*•·]{1,2}\s*\d{4}(?!\d)""")
    // Account-number form "Списание со счета 408*01139" — the glyph is followed by MORE than 4
    // digits, so pushMask can't capture it. The meaningful identifier is the LAST 4 ("1139").
    private val acctNumber = ciRegex("""сч[её]т[а-яё]*\s+\d*[*•·]\s*(\d{4,})""")
    // "Получатель платежа FONBET" → merchant "FONBET" (this format puts the payee after a label,
    // not as a bare merchant line, so the generic extractor would otherwise grab the whole body).
    private val payeeRe    = ciRegex("""Получател[ья]\s+платежа\s+([^;.\n]+)""")

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        TransferPatterns.detect(body)?.let { r ->
            return TransferPatterns.toParsed(r, bankId, body, smsId, timestampMillis)
        }

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

        return parsePush(body, smsId, timestampMillis)
    }

    /** Field-based parse for Alfa push notifications (sign-led amount, optional balance/card). */
    private fun parsePush(body: String, smsId: String, ts: Long): ParsedTransaction? {
        val m = pushAmount.find(body) ?: return null
        val amount = parseAmount(m.groupValues[2])
        if (amount <= 0L) return null

        val isIncome = m.groupValues[1] == "+"
        val balance  = ostatokRe.find(body)?.groupValues?.getOrNull(1)?.let { parseAmount(it) }
        // Use findAll().lastOrNull() so that when the joined title+body contains a glyph+4-digits
        // sequence earlier in the string (e.g. "··1139" in the notification title), the regex
        // still returns the LAST match — which is Alfa's own card mask ("··2548" in the body
        // balance line), not the counterparty or amount mask from the title.
        val card     = pushMask.findAll(body).lastOrNull()?.groupValues?.getOrNull(1)
            ?: acctNumber.find(body)?.groupValues?.getOrNull(1)?.takeLast(4)   // "408*01139" → "1139"
        val merchant = payeeRe.find(body)?.groupValues?.getOrNull(1)?.trim()   // "Получатель платежа FONBET"
            ?: body.substring(m.range.last + 1)
                .substringBefore("Остаток")
                .substringBefore("Списание со сч")   // don't fold the "Списание со счета …" prefix into merchant
                .replace(maskTail, "")
                .trim()
                .trim('.', ',', ';', ' ')
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

    private fun parseAmount(s: String): Long = AmountParser.toKopecks(s)
}
