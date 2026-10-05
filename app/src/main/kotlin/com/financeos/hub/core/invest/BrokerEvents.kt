package com.financeos.hub.core.invest

/**
 * События брокерского счёта — то, что видно ТОЛЬКО в режиме инвестора.
 *
 * Намеренно отдельная модель, а не [com.financeos.hub.core.database.entities.TransactionEntity].
 * Кошелёк — общая история, аналитика, бюджет, оценка здоровья — не должен видеть инвестиций, и
 * надёжнее всего, когда он их физически не читает, а не когда в каждом из десятка запросов стоит
 * фильтр, который однажды забудут.
 *
 * Деньги — в копейках (как везде). Цена бумаги — в МИЛЛИОННЫХ долях валюты: биржа печатает четыре
 * знака после запятой («по 2.0985»), и копеек на неё не хватает.
 */
sealed interface BrokerEvent {
    /** Брокер, приславший событие («БКС»). */
    val broker   : String
    val timestamp: Long
    /**
     * Строка в базе — чтобы событие можно было удалить. `null` у примера и у только что
     * разобранного пуша. У введённого вручную начинается с [MANUAL_PREFIX] (инвариант #49).
     */
    val id       : String?
}

/** Префикс id событий, введённых человеком, а не пришедших пушем. */
const val MANUAL_PREFIX = "manual_"

/** Событие введено вручную — его можно править и удалять без оглядки на пуши. */
val BrokerEvent.isManual: Boolean get() = id?.startsWith(MANUAL_PREFIX) == true

/** Деньги пришли на брокерский счёт (+) или ушли с него (−). */
data class BrokerCashMove(
    override val broker   : String,
    override val timestamp: Long,
    /** Номер договора/счёта у брокера, как его печатает брокер: «580922/19-м». */
    val contract     : String?,
    /** Знаковая сумма в копейках: + пополнение, − вывод. */
    val amountKopecks: Long,
    val currency     : String,
    override val id  : String? = null,
) : BrokerEvent

/**
 * Деньги переложили между ДВУМЯ счетами одного брокера: «Перевод между счетами 189 RUB. Со счета
 * №580922/19-м на счет 3468071/25 (Облигации)».
 *
 * Итог у брокера НЕ меняется — это не пополнение и не вывод. Записать его пополнением значило бы
 * посчитать деньги дважды.
 */
data class BrokerInternalTransfer(
    override val broker   : String,
    override val timestamp: Long,
    val amountKopecks: Long,
    val currency     : String,
    val fromContract : String,
    val toContract   : String,
    /** Название счёта, как его пишет брокер в скобках: «Облигации». */
    val fromLabel    : String? = null,
    val toLabel      : String? = null,
    override val id  : String? = null,
) : BrokerEvent

/**
 * Предупреждение брокера (маржин-колл): «Критично низкий баланс счета 3468071/25 (Облигации)
 * Пополните счет … на сумму от 188.02. Если стоимость портфеля станет ниже 0, брокер приступит к
 * закрытию ваших позиций».
 *
 * Факт о счёте, а не движение денег — как напоминание банка о платеже по кредитке. Закрывается
 * деньгами, пришедшими на этот счёт ПОСЛЕ него (см. [MarginAlerts]).
 */
data class BrokerMarginAlert(
    override val broker   : String,
    override val timestamp: Long,
    val contract        : String,
    val label           : String?,
    /** «Пополните … на сумму от 188.02» — сколько требует брокер, копейки. */
    val requiredKopecks : Long,
    /** Валюту пуш не пишет; счёт рублёвый — так у всех известных предупреждений БКС. */
    val currency        : String = "RUB",
    /** Строка в базе — чтобы предупреждение можно было закрыть вручную. Пусто у примера. */
    override val id     : String? = null,
    val dismissed       : Boolean = false,
) : BrokerEvent

enum class OrderSide { BUY, SELL }

/** Жизнь заявки: выставлена → исполнена или отменена. */
enum class OrderStatus { ACTIVE, CANCELLED, FILLED }

/** Заявка на покупку или продажу. Деньги двигает только [OrderStatus.FILLED]. */
data class BrokerOrder(
    override val broker   : String,
    override val timestamp: Long,
    val ticker     : String,
    val side       : OrderSide,
    /** Количество ЛОТОВ — так их пишет брокер. Сколько бумаг в лоте, пуш не сообщает. */
    val lots       : Long,
    /**
     * Цена ОДНОЙ бумаги в миллионных долях валюты (2.0985 → 2 098 500). [UNKNOWN_PRICE] — цену брокер
     * не прислал (рыночная заявка, #52).
     */
    val priceMicros: Long,
    val status     : OrderStatus,
    /** «Лимитная» / «Рыночная» — как написал брокер; для подписи. */
    val kind       : String? = null,
    val currency   : String = "RUB",
    /** Счёт сделки. Пуш его не пишет (только ручной ввод знает), поэтому чаще всего `null`. */
    val contract   : String? = null,
    override val id: String? = null,
) : BrokerEvent {
    val priceKnown: Boolean get() = priceMicros > 0L

    companion object {
        /** Цена не пришла: «Рыночная заявка на покупку 20 лотов LQDT» — без «по …». */
        const val UNKNOWN_PRICE = 0L
    }
}

/**
 * Счёт у брокера, заведённый или скрытый ЧЕЛОВЕКОМ (инвариант #49): пуши могут не прийти вовсе, а
 * счёт, о котором брокер не писал, иначе нельзя было бы выбрать. [hidden] — «удалить счёт»: счёт
 * пропадает из списка, даже если пуши его упоминали; операции по нему остаются в истории.
 */
data class BrokerAccountMark(
    override val broker   : String,
    override val timestamp: Long,
    val contract: String,
    val label   : String?,
    val hidden  : Boolean = false,
    override val id: String? = null,
) : BrokerEvent

/**
 * Текущая цена бумаги, указанная вручную. Котировок у приложения нет, и без этого стоимость
 * стояла бы на цене последней своей сделки вечно. Меняет только цену, не количество и не деньги.
 */
data class BrokerPriceMark(
    override val broker   : String,
    override val timestamp: Long,
    val ticker     : String,
    val priceMicros: Long,
    val currency   : String = "RUB",
    override val id: String? = null,
) : BrokerEvent

/** Миллионные доли валюты → копейки, с округлением до ближайшей. */
internal fun microsToKopecks(micros: Long): Long =
    if (micros >= 0) (micros + 5_000) / 10_000 else -((-micros + 5_000) / 10_000)

/**
 * Номер счёта брокера для сравнения: «№3468071/25», «3468071/25 » и «3468071/25» — один счёт.
 * Регистр сворачивается целиком: в номере бывает кириллица («580922/19-м»).
 */
fun contractKey(contract: String?): String? =
    contract?.trim()?.removePrefix("№")?.trim()?.trimEnd('.', ',', ';')?.lowercase()?.takeIf { it.isNotBlank() }
