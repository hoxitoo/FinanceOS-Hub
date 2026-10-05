package com.financeos.hub.core.invest

/**
 * Что записать в `broker_events`, когда человек вводит операцию или актив руками (инвариант #49).
 *
 * Чистые функции: форма собирает поля, здесь они превращаются в те же события, что приходят пушами,
 * — поэтому портфель, результат за период, предупреждения и группы считают ручной ввод ровно так же.
 * `null` — форма заполнена так, что записывать нечего (ноль, пустой тикер, перевод «сам в себя»).
 */
object ManualEntry {

    enum class Kind(val title: String) {
        DEPOSIT("Пополнение"),
        WITHDRAW("Вывод"),
        TRANSFER("Перевод"),
        BUY("Покупка"),
        SELL("Продажа"),
    }

    /**
     * Валюты, которые можно выбрать в ручном вводе. Брокерский счёт держит не только рубли: доллары
     * и юани (#51), и сумма в них бывает дробной — «0,41 $», «41,60 ¥».
     */
    val CURRENCIES = listOf("RUB", "USD", "CNY", "EUR", "HKD")

    /** Операция: деньги (пополнение / вывод / перевод между счетами) или сделка (покупка / продажа). */
    fun operation(
        kind        : Kind,
        broker      : String,
        timestamp   : Long,
        amountKopecks: Long? = null,
        ticker      : String? = null,
        quantity    : Long? = null,
        priceMicros : Long? = null,
        contract    : String? = null,
        toContract  : String? = null,
        currency    : String = "RUB",
    ): List<BrokerEvent>? = when (kind) {
        Kind.DEPOSIT, Kind.WITHDRAW -> amountKopecks?.takeIf { it > 0 }?.let { a ->
            listOf(BrokerCashMove(broker, timestamp, contract, if (kind == Kind.DEPOSIT) a else -a, currency))
        }
        Kind.TRANSFER -> {
            val a = amountKopecks?.takeIf { it > 0 }
            val from = contract?.takeIf { it.isNotBlank() }
            val to = toContract?.takeIf { it.isNotBlank() }
            if (a == null || from == null || to == null || contractKey(from) == contractKey(to)) null
            else listOf(BrokerInternalTransfer(broker, timestamp, a, currency, from, to))
        }
        Kind.BUY, Kind.SELL -> order(kind, broker, timestamp, ticker, quantity, priceMicros, contract, currency)?.let(::listOf)
    }

    /**
     * Актив, купленный когда-то раньше: пополнение на его стоимость + покупка. Без пополнения
     * свободные деньги ушли бы в минус на всю стоимость бумаги, а результат «за всё время» показал
     * бы её же доходом. Вместе они дают ноль результата и стоимость бумаги в итоге — как и было.
     * Удаляются тоже вместе: у записи один префикс id.
     */
    fun asset(
        broker     : String,
        timestamp  : Long,
        ticker     : String,
        quantity   : Long,
        priceMicros: Long,
        contract   : String? = null,
        currency   : String = "RUB",
    ): List<BrokerEvent>? {
        val buy = order(Kind.BUY, broker, timestamp, ticker, quantity, priceMicros, contract, currency) ?: return null
        val cost = microsToKopecks(priceMicros * quantity)
        return listOf(BrokerCashMove(broker, timestamp, contract, cost, currency), buy)
    }

    /**
     * Валюта на счёте как актив: «USD000SMALL 0,41» — это 0,41 $ денег, а не 0 бумаг. БКС держит
     * остаток валюты под таким тикером, и штуками его не ввести: центы и фэни дробные (#51).
     * Записывается пополнением в своей валюте — так же, как деньги, пришедшие пушем.
     */
    fun currencyCash(
        broker       : String,
        timestamp    : Long,
        ticker       : String,
        amountKopecks: Long?,
        contract     : String? = null,
    ): List<BrokerEvent>? {
        val cur = SecurityGroups.cashCurrency(ticker) ?: return null
        val a = amountKopecks?.takeIf { it > 0 } ?: return null
        return listOf(BrokerCashMove(broker, timestamp, contract?.takeIf { it.isNotBlank() }, a, cur))
    }

    // ── Правка (#51) ────────────────────────────────────────────────────────────

    /**
     * Что показать в листе правки: поля записи, к которой относится нажатая строка. [asset] — запись
     * «Актив» (пополнение на стоимость + покупка): её правят целиком, иначе деньги и бумага разошлись бы.
     */
    data class Draft(
        val kind              : Kind,
        val asset             : Boolean,
        val amountKopecks     : Long?,
        val ticker            : String,
        val quantity          : Long?,
        val priceMicros       : Long?,
        val currentPriceMicros: Long?,
        /** Когда указана текущая цена: неизменённая цена сохраняется со СВОИМ временем, не «сейчас». */
        val currentPriceAt    : Long?,
        val contract          : String?,
        val toContract        : String?,
        val currency          : String,
        val timestamp         : Long,
    )

    /**
     * Нажатая строка [tapped] и вся её запись [group] (у ручной — все строки с общим префиксом id, у
     * пуша — она сама) → поля формы. `null` — править нечего (предупреждение брокера, отметка счёта).
     */
    fun draftOf(tapped: BrokerEvent, group: List<BrokerEvent>): Draft? {
        val buy  = group.filterIsInstance<BrokerOrder>().firstOrNull { it.side == OrderSide.BUY }
        val cash = group.filterIsInstance<BrokerCashMove>().firstOrNull()
        if (buy != null && cash != null) {
            val mark = group.filterIsInstance<BrokerPriceMark>().lastOrNull()
            return Draft(
                kind = Kind.BUY, asset = true, amountKopecks = null, ticker = buy.ticker, quantity = buy.lots,
                priceMicros = buy.priceMicros, currentPriceMicros = mark?.priceMicros,
                currentPriceAt = mark?.timestamp, contract = buy.contract,
                toContract = null, currency = buy.currency, timestamp = buy.timestamp,
            )
        }
        return when (tapped) {
            is BrokerCashMove -> Draft(
                kind = if (tapped.amountKopecks >= 0) Kind.DEPOSIT else Kind.WITHDRAW, asset = false,
                amountKopecks = kotlin.math.abs(tapped.amountKopecks), ticker = "", quantity = null,
                priceMicros = null, currentPriceMicros = null, currentPriceAt = null, contract = tapped.contract,
                toContract = null, currency = tapped.currency, timestamp = tapped.timestamp,
            )
            is BrokerInternalTransfer -> Draft(
                kind = Kind.TRANSFER, asset = false, amountKopecks = tapped.amountKopecks, ticker = "",
                quantity = null, priceMicros = null, currentPriceMicros = null, currentPriceAt = null,
                contract = tapped.fromContract,
                toContract = tapped.toContract, currency = tapped.currency, timestamp = tapped.timestamp,
            )
            is BrokerOrder -> Draft(
                kind = if (tapped.side == OrderSide.SELL) Kind.SELL else Kind.BUY, asset = false,
                amountKopecks = null, ticker = tapped.ticker, quantity = tapped.lots,
                // Цена, которую брокер не прислал (#52), — пустое поле, а не «0».
                priceMicros = tapped.priceMicros.takeIf { it > 0L }, currentPriceMicros = null,
                currentPriceAt = null, contract = tapped.contract,
                toContract = null, currency = tapped.currency, timestamp = tapped.timestamp,
            )
            else -> null
        }
    }

    /**
     * Время правленой записи: день сменили — новый день в ТО ЖЕ время суток; день прежний — прежнее
     * время до миллисекунды. «Сейчас» вместо него переставило бы операцию в истории и сдвинуло бы
     * результат «за 24 часа», а у пуша — разорвало бы его с цепочкой заявки.
     */
    fun editedTimestamp(
        original: Long,
        day     : java.time.LocalDate,
        zone    : java.time.ZoneId = java.time.ZoneId.systemDefault(),
        now     : Long = System.currentTimeMillis(),
    ): Long {
        val at = java.time.Instant.ofEpochMilli(original).atZone(zone)
        if (at.toLocalDate() == day) return original
        // Вчерашние 23:00, перенесённые на сегодня, не должны оказаться в будущем.
        return minOf(day.atTime(at.toLocalTime()).atZone(zone).toInstant().toEpochMilli(), now)
    }

    /**
     * Что правка ПУША обязана сохранить.
     * - Заявка: статус, подпись брокера, бумагу, сторону, лоты и время. По ним заявка связана со своими
     *   прежними пушами («активна → исполнена»): смени их — и «активна» стала бы последним словом
     *   заявки и ожила бы на экране («Заявки · 1»), а удаление перестало бы её находить. Править у
     *   пуша можно цену (рыночная приходит без неё, #52), счёт и валюту.
     * - Перевод между счетами: названия счетов («Облигации») — их пишет только брокер, а форма их не
     *   знает; при тех же счетах они остаются.
     */
    fun keepPushed(original: BrokerEvent, edited: List<BrokerEvent>): List<BrokerEvent> {
        if (original.isManual || original.id == null) return edited
        return edited.map { e ->
            when {
                original is BrokerOrder && e is BrokerOrder -> e.copy(
                    status = original.status, kind = original.kind, ticker = original.ticker,
                    side = original.side, lots = original.lots, timestamp = original.timestamp,
                )
                original is BrokerInternalTransfer && e is BrokerInternalTransfer -> e.copy(
                    fromLabel = if (contractKey(e.fromContract) == contractKey(original.fromContract)) original.fromLabel else null,
                    toLabel   = if (contractKey(e.toContract) == contractKey(original.toContract)) original.toLabel else null,
                )
                else -> e
            }
        }
    }

    /** Цена в поле ввода: 2 098 500 → «2,0985» — сырая строка, без разбивки (инвариант #7). */
    fun priceInput(micros: Long): String {
        val whole = micros / 1_000_000
        val frac  = (micros % 1_000_000).toString().padStart(6, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else "$whole,$frac"
    }

    private fun order(
        kind: Kind, broker: String, ts: Long, ticker: String?, quantity: Long?, priceMicros: Long?,
        contract: String?, currency: String,
    ): BrokerOrder? {
        val t = ticker?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        val q = quantity?.takeIf { it > 0 } ?: return null
        val p = priceMicros?.takeIf { it > 0 } ?: return null
        // Стоимость считается как цена × количество в миллионных долях — абсурдный ввод не должен
        // переполнить Long и превратиться в отрицательную сумму.
        runCatching { Math.multiplyExact(p, q) }.getOrNull() ?: return null
        return BrokerOrder(
            broker = broker, timestamp = ts, ticker = t,
            side = if (kind == Kind.SELL) OrderSide.SELL else OrderSide.BUY,
            // Вручную вводят ШТУКИ, а не лоты: лот = 1 бумага (так считает и портфель, #44).
            lots = q, priceMicros = p, status = OrderStatus.FILLED, kind = "Вручную",
            currency = currency, contract = contract?.takeIf { it.isNotBlank() },
        )
    }

    /** Цена из поля ввода: «2,0985» → 2 098 500; цифры и одна запятая/точка, до шести знаков. */
    fun parsePrice(text: String): Long? = BrokerPushParser.priceMicros(text.trim())

    /** Что можно напечатать в поле цены: цифры и один разделитель, не больше шести знаков после него. */
    fun sanitizePrice(raw: String): String {
        val cleaned = raw.replace('.', ',').filter { it.isDigit() || it == ',' }
        val head = cleaned.substringBefore(',')
        if (!cleaned.contains(',')) return head.take(12)
        return head.take(12) + "," + cleaned.substringAfter(',').replace(",", "").take(6)
    }
}
