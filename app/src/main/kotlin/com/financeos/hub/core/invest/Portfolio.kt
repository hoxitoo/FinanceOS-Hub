package com.financeos.hub.core.invest

/**
 * Портфель из событий брокера: позиции, свободные деньги на счёте, стоимость и результат.
 *
 * Чистая функция — без базы и без сети, поэтому проверяется тестами целиком.
 *
 * Правила, и у каждого своя причина:
 * - **Деньги двигает только исполненная заявка.** Активная и отменённая — намерение, не сделка.
 * - **Цена — последней СВОЕЙ сделки по бумаге.** Котировок у офлайн-приложения нет; экран обязан
 *   писать «по цене последней сделки», а не выдавать её за рыночную. Котировки из сети — в бэклоге.
 * - **Средняя цена — по методу средней:** покупка усредняет, продажа её не меняет и фиксирует
 *   результат по средней.
 * - **Размер лота пуш не сообщает.** По умолчанию лот = 1 бумага (так у LQDT: 4760 лотов × 2.0985 =
 *   9 988,86 ₽ при пополнении на 10 000 ₽). Для бумаг, где это не так, вызывающий передаёт
 *   [lotSizes]; без них стоимость будет занижена во столько раз, сколько бумаг в лоте.
 * - **Комиссия не учитывается** — её в пушах нет. Остаток денег на счёте поэтому приблизителен.
 * - **Валюты не складываются**, как и в кошельке: итоги — по валюте.
 * - **Перевод между счетами брокера итог не меняет** — деньги остались у того же брокера.
 * - **Остаток по каждому счёту не считается.** Пуш о сделке не говорит, с какого счёта ушли деньги,
 *   и угаданный остаток врал бы. Счета видны списком, со своими движениями и предупреждениями.
 */
object Portfolio {

    data class Position(
        val broker          : String,
        val ticker          : String,
        val currency        : String,
        /** Количество БУМАГ (лоты × размер лота). */
        val quantity        : Long,
        /** Средняя цена одной бумаги, миллионные доли валюты. */
        val avgPriceMicros  : Long,
        /** Цена последней своей сделки, миллионные доли. */
        val lastPriceMicros : Long,
    ) {
        /** Сколько вложено в оставшиеся бумаги, копейки. */
        val costKopecks : Long get() = microsToKopecks(avgPriceMicros * quantity)
        /** Стоимость по цене последней сделки, копейки. */
        val valueKopecks: Long get() = microsToKopecks(lastPriceMicros * quantity)
        val pnlKopecks  : Long get() = valueKopecks - costKopecks
    }

    /** Деньги у брокера в одной валюте — по всем его счетам вместе. */
    data class BrokerAccount(
        val broker      : String,
        /** Номер счёта, если у брокера известен ровно один; при нескольких — `null`. */
        val contract    : String?,
        val currency    : String,
        /** Пополнения − выводы − покупки + продажи. Без комиссий — см. описание объекта. */
        val cashKopecks : Long,
        /** Сколько всего завели на счёт за вычетом выводов. */
        val netDepositsKopecks: Long,
    )

    /** Счёт у брокера, узнанный из пушей: номер и название, если брокер его пишет. */
    data class Contract(
        val broker  : String,
        val contract: String,
        val label   : String?,
    ) {
        val key: String get() = contractKey(contract)!!
        /** «3468071/25 (Облигации)» — как в приложении брокера. */
        val title: String get() = if (label != null) "$contract ($label)" else contract
    }

    data class Summary(
        val currency      : String,
        val valueKopecks  : Long,
        val costKopecks   : Long,
        val cashKopecks   : Long,
    ) {
        val pnlKopecks: Long get() = valueKopecks - costKopecks
        /** Результат в процентах от вложенного в бумаги; `null`, пока бумаг нет. */
        val pnlPercent: Double? get() =
            if (costKopecks == 0L) null else pnlKopecks * 100.0 / costKopecks
        /** Всего на брокерском счёте: бумаги по цене последней сделки + свободные деньги. */
        val totalKopecks: Long get() = valueKopecks + cashKopecks
    }

    data class Result(
        val accounts : List<BrokerAccount>,
        val positions: List<Position>,
        /** Заявки — новые сверху; активные отдельно, потому что они ещё могут сработать. */
        val activeOrders: List<BrokerOrder>,
        val history     : List<BrokerOrder>,
        val summaries   : List<Summary>,
        val contracts   : List<Contract> = emptyList(),
        /** Пополнения, выводы и переводы между счетами — новые сверху. */
        val movements   : List<BrokerEvent> = emptyList(),
        val alerts      : List<MarginAlerts.State> = emptyList(),
    ) {
        val isEmpty: Boolean get() = accounts.isEmpty() && positions.isEmpty() && activeOrders.isEmpty() &&
            history.isEmpty() && movements.isEmpty() && alerts.isEmpty()
        val openAlerts: List<MarginAlerts.State> get() = alerts.filter { it.isOpen }
    }

    val EMPTY = Result(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    fun compute(events: List<BrokerEvent>, lotSizes: Map<String, Long> = emptyMap()): Result {
        val sorted = events.sortedBy { it.timestamp }

        data class Acc(var qty: Long = 0, var avg: Long = 0, var last: Long = 0)
        val holdings = linkedMapOf<Triple<String, String, String>, Acc>()        // broker, ticker, currency
        val cash     = linkedMapOf<Pair<String, String>, Long>()                  // broker, currency
        val deposits = linkedMapOf<Pair<String, String>, Long>()
        // Счета брокера: номер → название. Название — из последнего пуша, где оно было.
        val known = linkedMapOf<Pair<String, String>, Contract>()
        fun see(broker: String, contract: String?, label: String?) {
            val key = contractKey(contract) ?: return
            val prev = known[broker to key]
            known[broker to key] = Contract(broker, prev?.contract ?: contract!!.trim().removePrefix("№").trim(), label ?: prev?.label)
        }

        for (e in sorted) when (e) {
            is BrokerCashMove -> {
                val key = e.broker to e.currency
                cash[key]     = (cash[key] ?: 0L) + e.amountKopecks
                deposits[key] = (deposits[key] ?: 0L) + e.amountKopecks
                see(e.broker, e.contract, null)
            }
            // Деньги остались у того же брокера: итог не меняется, меняется только счёт.
            is BrokerInternalTransfer -> {
                see(e.broker, e.fromContract, e.fromLabel)
                see(e.broker, e.toContract, e.toLabel)
            }
            is BrokerMarginAlert -> see(e.broker, e.contract, e.label)
            is BrokerOrder -> {
                if (e.status != OrderStatus.FILLED) continue
                val qty    = e.lots * (lotSizes[e.ticker] ?: 1L)
                val amount = microsToKopecks(e.priceMicros * qty)
                val acc    = holdings.getOrPut(Triple(e.broker, e.ticker, e.currency)) { Acc() }
                val cashKey = e.broker to e.currency
                when (e.side) {
                    OrderSide.BUY -> {
                        val newQty = acc.qty + qty
                        acc.avg = if (newQty == 0L) 0L else (acc.avg * acc.qty + e.priceMicros * qty) / newQty
                        acc.qty = newQty
                        cash[cashKey] = (cash[cashKey] ?: 0L) - amount
                    }
                    OrderSide.SELL -> {
                        acc.qty -= qty
                        if (acc.qty <= 0L) { acc.qty = 0L; acc.avg = 0L }
                        cash[cashKey] = (cash[cashKey] ?: 0L) + amount
                    }
                }
                acc.last = e.priceMicros
            }
        }

        val positions = holdings.filter { it.value.qty > 0L }.map { (k, a) ->
            Position(k.first, k.second, k.third, a.qty, a.avg, a.last)
        }.sortedByDescending { it.valueKopecks }

        val contracts = known.values.toList()
        val accounts = cash.keys.map { key ->
            BrokerAccount(
                broker             = key.first,
                contract           = contracts.filter { it.broker == key.first }.singleOrNull()?.contract,
                currency           = key.second,
                cashKopecks        = cash[key] ?: 0L,
                netDepositsKopecks = deposits[key] ?: 0L,
            )
        }

        val orders = sorted.filterIsInstance<BrokerOrder>()
        // Одна заявка приходит несколькими пушами (активна → исполнена). В ленте — последний статус:
        // «активна» после «исполнена» той же заявки — это уже прошлое, а не вторая заявка.
        // Цена в ключ НЕ входит: переставленная заявка (2.0984 → 2.0985, как у БКС 1 октября) — та же
        // заявка. Цена ключа — две настоящие разные активные заявки одного объёма склеятся в одну
        // строку; номера заявки в пуше нет, развести их нечем.
        val latest = orders.groupBy { listOf(it.broker, it.ticker, it.side, it.lots) }
            .values.map { it.last() }
        val active = latest.filter { it.status == OrderStatus.ACTIVE }.sortedByDescending { it.timestamp }
        val history = orders.filter { it.status != OrderStatus.ACTIVE }.sortedByDescending { it.timestamp }

        val currencies = (positions.map { it.currency } + accounts.map { it.currency }).distinct()
        val summaries = currencies.map { cur ->
            val ps = positions.filter { it.currency == cur }
            Summary(
                currency     = cur,
                valueKopecks = ps.sumOf { it.valueKopecks },
                costKopecks  = ps.sumOf { it.costKopecks },
                cashKopecks  = accounts.filter { it.currency == cur }.sumOf { it.cashKopecks },
            )
        }

        val movements = sorted.filter { it is BrokerCashMove || it is BrokerInternalTransfer }.reversed()
        return Result(
            accounts, positions, active, history, summaries,
            contracts = contracts,
            movements = movements,
            alerts    = MarginAlerts.evaluate(sorted),
        )
    }
}
