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
