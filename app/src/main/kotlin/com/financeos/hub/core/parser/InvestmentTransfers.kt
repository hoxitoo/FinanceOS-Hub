package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType

/**
 * Деньги, ушедшие брокеру или пришедшие от него, — это ПЕРЕВОД своих денег, а не трата и не доход.
 *
 * Альфа присылает пополнение брокерского счёта как обычное списание («Списание со счета 408*01139;
 * … Получатель платежа BKS Mir Investitsiy»), и разборщик честно записывает его расходом. Тогда
 * каждое пополнение попадало в траты, в «Другое», в бюджет и в оценку финансового здоровья — хотя
 * деньги никуда не делись, они просто лежат у брокера. Вывод с брокерского счёта так же записывался
 * бы заработком.
 *
 * Распознавание идёт ПО ПОЛУЧАТЕЛЮ (`merchant`), а не по всему тексту: в тексте пуша слово
 * «инвестиции» встречается и в рекламе, и в подписях банка, а получатель платежа — это ровно то, куда
 * ушли деньги. Тип меняется на TRANSFER с тем же направлением, сумма и знак не меняются — поэтому
 * баланс счёта, дедуп по знаковой сумме и откат при удалении ведут себя как прежде.
 *
 * Это первый шаг брокерского режима: строка получает категорию [CATEGORY] и тип «перевод». Когда
 * появится брокерский счёт (`AccountKind.INVESTMENT`), такой перевод станет обычным переводом между
 * своими счетами — второй стороной будет этот счёт.
 */
object InvestmentTransfers {

    /** Категория «Инвестиции» — у перевода к брокеру и от него. */
    const val CATEGORY = "cat_invest"

    /**
     * Признаки брокера в имени получателя. Все через [ciRegex] (инвариант #13).
     *
     * Широкое «invest» сюда намеренно НЕ входит: «Investment», «InvestStroy» — это и застройщики, и
     * магазины. Транслит русского «инвестиций» («Investitsiy», «Investicii») — уже признак
     * российского брокера. «БКС»/«BKS» — только отдельным словом: внутри чужого слова эти три буквы
     * встречаются слишком часто.
     */
    private val BROKER_PATTERNS: List<Regex> = listOf(
        ciRegex("""инвестиц"""),                                   // «Мир инвестиций», «Т-Инвестиции»
        ciRegex("""investi[ct]s?i"""),                             // «Investitsiy», «Investicii»
        ciRegex("""брокер|broker"""),                              // «БКС Брокер», «Открытие Брокер»
        ciRegex("""(?<![\p{L}\p{N}])(?:бкс|bks|bcs)(?![\p{L}\p{N}])"""),
        ciRegex("""финам|finam"""),
        ciRegex("""фридом\s*финанс|freedom\s*finance|freedom\s*24"""),
    )

    /** Похоже ли имя получателя на брокера. */
    fun isBroker(merchant: String?): Boolean {
        if (merchant.isNullOrBlank()) return false
        return BROKER_PATTERNS.any { it.containsMatchIn(merchant) }
    }

    /** Строка уже размечена как перевод к брокеру или от него. */
    fun isInvestmentTransfer(tx: TransactionEntity): Boolean =
        tx.type == TransactionType.TRANSFER && tx.categoryId == CATEGORY

    /**
     * Списание брокеру → исходящий перевод, зачисление от брокера → входящий. Направление и сумма
     * остаются прежними; ничего не похожего на брокера не трогается.
     */
    fun reclassify(parsed: ParsedTransaction): ParsedTransaction {
        if (!isBroker(parsed.merchant)) return parsed
        return when (parsed.type) {
            TransactionType.EXPENSE  -> parsed.copy(type = TransactionType.TRANSFER, outgoing = true,  categoryId = CATEGORY)
            TransactionType.INCOME   -> parsed.copy(type = TransactionType.TRANSFER, outgoing = false, categoryId = CATEGORY)
            TransactionType.TRANSFER -> parsed.copy(categoryId = CATEGORY)
        }
    }
}
