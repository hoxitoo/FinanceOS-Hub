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
     * Признаки брокера в имени получателя — только НАЗВАНИЯ брокеров, все через [ciRegex] (#13).
     *
     * Общие слова сюда намеренно не входят. «Инвестиц» — это и «Инвестиционно-строительная
     * компания» (застройщик, платёж по договору), и «Инвестиционный банк» (платёж по кредиту), и
     * работодатель «УК Инвестиционные решения» — его зарплата стала бы переводом и пропала бы из
     * дохода. «Брокер» — и страховой, и ипотечный, и таможенный. Поэтому слово «инвестиции» и
     * «брокер» считаются признаком только вместе с названием брокера, а короткие названия
     * (БКС, Финам) — только отдельным словом: внутри чужого слова эти буквы встречаются часто.
     */
    private val BROKER_PATTERNS: List<Regex> = listOf(
        // БКС: «BKS Mir Investitsiy», «ООО Компания БКС», «BCS Broker».
        ciRegex("""(?<![\p{L}\p{N}])(?:бкс|bks|bcs)(?![\p{L}\p{N}])"""),
        ciRegex("""мир\s+инвестиций|mir\s+investitsi"""),
        // «Т-Инвестиции», «Альфа-Инвестиции», «ВТБ Мои Инвестиции», «Сбер Инвестиции».
        ciRegex("""(?<![\p{L}\p{N}])(?:т|тинькофф|альфа|втб|сбер|сбербанк|газпромбанк|открытие|финам)[\s-]*(?:мои\s+)?инвестиции(?![\p{L}\p{N}])"""),
        ciRegex("""(?<![\p{L}\p{N}])(?:t|tinkoff|alfa|vtb|sber)[\s-]*investi[ct]s?ii(?![\p{L}\p{N}])"""),
        // «Открытие Брокер», «Финам брокер» — но не «страховой брокер».
        ciRegex("""(?<![\p{L}\p{N}])(?:открытие|otkritie|финам|finam|атон|aton|цифра|альфа|alfa|втб|vtb|сбербанк|sberbank|газпромбанк|ренессанс|renaissance)[\s-]+(?:брокер|broker)(?![\p{L}\p{N}])"""),
        ciRegex("""(?<![\p{L}\p{N}])(?:финам|finam)(?![\p{L}\p{N}])"""),
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
