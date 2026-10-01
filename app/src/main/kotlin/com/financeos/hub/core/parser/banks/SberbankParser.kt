package com.financeos.hub.core.parser.banks

import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.ParsedTransaction
import com.financeos.hub.core.parser.SberPushTitle
import com.financeos.hub.core.parser.TransferPatterns
import com.financeos.hub.core.parser.ciRegex
import javax.inject.Inject

class SberbankParser @Inject constructor() : BankParser {
    override val bankId = "sberbank"
    override val senderPatterns = listOf(Regex("SBERBANK|900|СБЕРБАНК"))

    // "VISA1234 18.06.25 12:34 Оплата 1 500р МАГАЗИН Баланс: 12 345,67р"
    // "Счёт карты MIR-1238 21:07 Покупка 100р SPORTLOTEREI_SP Баланс: 15 351.89р"
    // [-\s]* handles both "MIR-1238" (dash) and "MIR1234" (no separator) forms.
    // (?:[\d.]+\s+)? makes the date prefix optional (some SMS omit DD.MM.YY, show only HH:MM).
    private val expenseRu = ciRegex(
        """(?:VISA|MASTERCARD|МИР|MIR)[-\s]*(\d{4})\s+(?:[\d.]+\s+)?[\d:]+\s+(?:Оплата|Покупка|Списание)\s+([\d\s]+(?:[.,]\d{2})?)\s*р\s+(.+?)\s+(?:Баланс|Остаток):\s*([\d\s]+(?:[.,]\d{2})?)\s*р""")

    // "VISA1234 18.06.25 12:34 Зачисление 10 000р"
    // NOTE: "Перевод" intentionally excluded — a transfer SMS is frequently OUTGOING and
    // would be misclassified as income (sign inversion), corrupting analytics.
    private val incomeRu = ciRegex(
        """(?:VISA|MASTERCARD|МИР|MIR)[-\s]*(\d{4})\s+(?:[\d.]+\s+)?[\d:]+\s+(?:Зачисление|Пополнение)\s+([\d\s]+(?:[.,]\d{2})?)\s*р""")

    // English-locale Sberbank notifications
    private val expenseEn = Regex(
        """(?:VISA|MC)\s*(\d{4})[^\d]+([\d\s]+\.\d{2})\s*RUB\s+(.+?)\s+Balance[:\s]+([\d\s]+\.\d{2})\s*RUB""",
        RegexOption.IGNORE_CASE
    )

    // Sberbank push format: "[Merchant] [amount] ₽ [balance label]: [balance] ₽ [Карта/СЧЁТ] •[last4]"
    // The amount has no mandatory sign — type is inferred from keywords (Зачисление/Покупка/etc.).
    //
    // The label varies by card and by notification style. A real credit-card purchase push reads
    // "Покупка DNS 18 699 ₽ — Баланс: 411 301 ₽ Счёт карты МИР •• 6703", which the old
    // «В запасе»-only anchor missed entirely — an 18 699 ₽ purchase was silently dropped. All four
    // labels are accepted; PromoFilter has already rejected marketing copy by the time we get here.
    //
    // What the number MEANS still depends on the account, not on the label: on a credit card
    // «Баланс» is the free limit, not money owned. That translation lives in AccountLinker, which
    // knows the account kind — the parser only reports what the bank printed.
    private val pushBalRe  = ciRegex(
        "(?:В\\s+запасе|Баланс|Остаток|Доступно):\\s*([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*₽")

    // Leading operation word in a push title ("Покупка DNS" → "DNS"). Left in place the merchant
    // would be "Покупка DNS", which no merchant rule matches and which reads as the operation type
    // rather than the shop.
    private val pushOpPrefix = ciRegex(
        "^(?:Покупка|Оплата|Списание|Зачисление|Пополнение|Перевод|Платёж|Платеж)(?![А-Яа-яёЁ])[\\s:—–-]*")
    private val pushAmtRe  = Regex("([\\d][\\d \\u00A0\\u202F]*(?:[.,]\\d{1,2})?)\\s*₽")
    // "Карта •1234", "СЧЁТ • 4102", or bare "•1234"
    private val pushCardRe = ciRegex(
        "(?:Карта|СЧЁТ|СЧЕТ)\\s*[*•·]{1,2}\\s*(\\d{4})|[*•·]{1,2}\\s*(\\d{4})(?!\\d)")
    private val pushIncomeKw = ciRegex("(?:Зачисление|Пополнение)")

    // ── Сумма без склейки с названием ──────────────────────────────────────────
    // Прежний [pushAmtRe] начинает сумму с ЛЮБОЙ цифры и тянет через пробелы, поэтому цифры в конце
    // названия прилипали к сумме: «DODO PIZZA PERM-5 1 034 ₽» → 51 034 ₽, «R15173685 90 ₽» →
    // 1 517 368 590 ₽ (реальные пуши, у тестера «трат на сто миллионов»). Сумма записывается по
    // правилам денег: 1–3 цифры, дальше группы РОВНО по три, и не прямо после буквы или цифры.
    // «5 1 034» так не разбить, «15173685 90» — тоже: остаётся только настоящая сумма.
    // Не нашлось (сумма без разбивки, «10000 ₽») — прежний шаблон: пуш не должен потеряться.
    private val pushAmtStrict = Regex(
        "(?<![\\p{L}\\p{N}])(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})*(?:[.,]\\d{1,2})?)\\s*₽")

    // «+ 77,23 ₽» — знак прихода. Только отдельно стоящий плюс: «СберПрайм+ 399 ₽» — это название.
    private val pushPlusBefore = Regex("(?:^|\\s)\\+\\s*$")

    // Оплата по СБП / QR в магазине — это покупка, а не перевод. Проверяется ДО TransferPatterns,
    // который считает переводом любое «СБП». Отдельное слово «перевод» в тексте — уже не покупка.
    private val sbpPayment = ciRegex("""оплат\p{L}*\s+(?:\p{L}+\s+)?по\s+(?:СБП|QR)(?![А-Яа-яёЁ])""")
    private val transferWord = ciRegex("""(?<![А-Яа-яёЁ])перевод""")

    // Названия доходов вместо обрубков «пенсии +», «средств +».
    private val incomeNames: List<Pair<Regex, String>> = listOf(
        ciRegex("""^Зачисление\s+пенсии""")   to "Пенсия",
        ciRegex("""^Выплата\s+процентов""")   to "Проценты",
        ciRegex("""^Зачисление\s+зарплаты""") to "Зарплата",
        ciRegex("""^Зачисление\s+средств""")  to "Зачисление",
    )

    override fun parse(sender: String, body: String, timestampMillis: Long): ParsedTransaction? {
        val smsId = "${sender}_${timestampMillis}_${body.hashCode()}"

        // Оплата по СБП в магазине — покупка: сначала разбор пуша, и только если он не справился,
        // прежний путь через TransferPatterns (как было до правки, чтобы пуш не потерялся).
        if (sbpPayment.containsMatchIn(body) && !transferWord.containsMatchIn(body)) {
            parsePush(body, smsId, timestampMillis)?.let { return it }
        }

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
     * Requires a balance label to be present (strong signal this is a transaction push, not
     * marketing). Transaction amount = the last ₽-value before that label, type inferred from
     * keywords.
     */
    private fun parsePush(body: String, smsId: String, ts: Long): ParsedTransaction? {
        val balMatch = pushBalRe.find(body) ?: return null
        val balance  = AmountParser.toKopecks(balMatch.groupValues[1]).takeIf { it >= 0L } ?: return null

        val bodyBeforeBal = body.substring(0, balMatch.range.first)
        val amtMatch = pushAmtStrict.findAll(bodyBeforeBal).lastOrNull()
            ?: pushAmtRe.findAll(bodyBeforeBal).lastOrNull()
            ?: return null
        val amount   = AmountParser.toKopecks(amtMatch.groupValues[1])
        if (amount <= 0L) return null

        val card     = pushCardRe.find(body)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        val beforeAmt = bodyBeforeBal.substring(0, amtMatch.range.first)
        val isIncome = pushIncomeKw.containsMatchIn(body) || pushPlusBefore.containsMatchIn(beforeAmt)
        val merchant = SberPushTitle.merchant(beforeAmt, pushOpPrefix, incomeNames)

        // «Деньги отправились в Альфа-Банк», «Денежки уже в Яндекс Банк» — деньги ушли в другой
        // банк, это перевод, а не покупка. Только расход: приход из банка — обычное зачисление.
        if (!isIncome && SberPushTitle.isBank(merchant)) {
            return ParsedTransaction(
                type           = TransactionType.TRANSFER,
                amountKopecks  = amount,
                merchant       = merchant,
                cardMask       = card,
                balanceKopecks = balance,
                timestamp      = ts,
                bankId         = bankId,
                rawSms         = body,
                smsId          = smsId,
                outgoing       = true,
            )
        }

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
