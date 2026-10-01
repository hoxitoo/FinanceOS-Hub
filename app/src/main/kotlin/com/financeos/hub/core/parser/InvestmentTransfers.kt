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

    // Границы слова для кириллицы и латиницы: `\b` в Java только ASCII (инвариант #13).
    private const val B = """(?<![\p{L}\p{N}])"""
    private const val A = """(?![\p{L}\p{N}])"""

    /**
     * Брокер → как его называть по-русски. Порядок значим: побеждает первое совпадение.
     *
     * Имя нужно экрану: Альфа пишет получателя транслитом («BKS Mir Investitsiy»), а человек знает
     * брокера как «БКС». Исходный текст в операции не меняется — по нему ищет поиск и он виден в
     * карточке; подмена делается только при показе ([MerchantNames.display]).
     *
     * Признак — только НАЗВАНИЯ брокеров, все через [ciRegex] (#13). Общие слова сюда намеренно не
     * входят. «Инвестиц» — это и «Инвестиционно-строительная компания» (застройщик), и
     * «Инвестиционный банк» (кредит), и работодатель «УК Инвестиционные решения» — его зарплата стала
     * бы переводом и пропала бы из дохода. «Брокер» — и страховой, и ипотечный, и таможенный. Поэтому
     * «инвестиции» и «брокер» — признак только рядом с названием брокера, а короткие названия (БКС,
     * Финам) — только отдельным словом.
     */
    private val BROKERS: List<Pair<Regex, String>> = listOf(
        // БКС: «BKS Mir Investitsiy», «ООО Компания БКС», «BCS Broker», «БКС Мир инвестиций».
        ciRegex("""$B(?:бкс|bks|bcs)$A|мир\s+инвестиций|mir\s+investitsi""") to "БКС",
        ciRegex("""$B(?:т|тинькофф)[\s-]*инвестиции$A|$B(?:t|tinkoff)[\s-]*investi[ct]s?ii$A""") to "Т-Инвестиции",
        ciRegex("""${B}альфа[\s-]*инвестиции$A|${B}alfa[\s-]*investi[ct]s?ii$A|$B(?:альфа|alfa)[\s-]+(?:брокер|broker)$A""") to "Альфа-Инвестиции",
        ciRegex("""${B}втб[\s-]*(?:мои\s+)?инвестиции$A|${B}vtb[\s-]*investi[ct]s?ii$A|$B(?:втб|vtb)[\s-]+(?:брокер|broker)$A""") to "ВТБ Мои Инвестиции",
        ciRegex("""$B(?:сбер|сбербанк)[\s-]*инвестиции$A|${B}sber[\s-]*investi[ct]s?ii$A|$B(?:сбербанк|sberbank)[\s-]+(?:брокер|broker)$A""") to "Сбер Инвестиции",
        ciRegex("""${B}газпромбанк[\s-]*инвестиции$A|${B}газпромбанк[\s-]+брокер$A""") to "Газпромбанк Инвестиции",
        ciRegex("""${B}открытие[\s-]*инвестиции$A|$B(?:открытие|otkritie)[\s-]+(?:брокер|broker)$A""") to "Открытие Брокер",
        ciRegex("""$B(?:финам|finam)$A""") to "Финам",
        ciRegex("""$B(?:атон|aton)[\s-]+(?:брокер|broker)$A""") to "Атон",
        ciRegex("""${B}цифра[\s-]+брокер$A""") to "Цифра брокер",
        ciRegex("""$B(?:ренессанс|renaissance)[\s-]+(?:брокер|broker)$A""") to "Ренессанс Брокер",
        ciRegex("""фридом\s*финанс|freedom\s*finance|freedom\s*24""") to "Фридом Финанс",
    )

    /** Похоже ли имя получателя на брокера. */
    fun isBroker(merchant: String?): Boolean = brokerName(merchant) != null

    /** Русское название брокера по имени получателя («BKS Mir Investitsiy» → «БКС»), иначе null. */
    fun brokerName(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        return BROKERS.firstOrNull { (re, _) -> re.containsMatchIn(merchant) }?.second
    }

    /** Строка уже размечена как перевод к брокеру или от него. */
    fun isInvestmentTransfer(tx: TransactionEntity): Boolean =
        tx.type == TransactionType.TRANSFER && tx.categoryId == CATEGORY

    /**
     * Строка, записанная ДО распознавания (старая история, копия до v21), которую надо разметить:
     * расход или доход брокеру/от брокера, всё ещё лежащий в машинной категории. Строку, которую
     * человек разложил руками, не трогаем — признака «кто поставил категорию» в схеме нет.
     */
    fun needsRelabel(type: TransactionType, categoryId: String?, merchant: String?): Boolean =
        (type == TransactionType.EXPENSE || type == TransactionType.INCOME) &&
            (categoryId == null || categoryId in MACHINE_CATEGORIES) &&
            isBroker(merchant)

    /** Машинные категории: «Другое», «Прочие доходы» — то, что ставит разбор, а не человек. */
    val MACHINE_CATEGORIES = setOf("cat_other", "cat_income")

    /** [needsRelabel] → перевод «Инвестиции»; сумма и знак прежние. */
    fun relabel(tx: TransactionEntity): TransactionEntity =
        if (needsRelabel(tx.type, tx.categoryId, tx.merchant)) {
            tx.copy(type = TransactionType.TRANSFER, categoryId = CATEGORY)
        } else {
            tx
        }

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
