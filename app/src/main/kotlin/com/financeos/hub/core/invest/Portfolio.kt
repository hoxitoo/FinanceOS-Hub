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
 * - **Цена, которую брокер не прислал** (рыночная заявка, #52), берётся последней известной по бумаге
 *   — своей сделки или указанной вручную — и помечается оценкой ([Result.estimates]). Если по бумаге
 *   не известно ничего, сделка в портфель НЕ идёт и лежит в [Result.unpriced]: выдуманная цена
 *   молча врала бы и в бумагах, и в деньгах, а пропуск экран называет.
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
        /** Текущая цена одной бумаги: биржевая (#55), иначе последней своей сделки, миллионные доли. */
        val lastPriceMicros : Long,
        /** Когда получена биржевая цена; `null` — цена своя (сделка или указанная вручную). */
        val marketAt        : Long? = null,
        /** Группа по режиму торгов биржи; `null` — по форме тикера (#48). */
        val groupOverride   : SecurityGroup? = null,
    ) {
        /** Сколько вложено в оставшиеся бумаги, копейки. */
        val costKopecks : Long get() = microsToKopecks(avgPriceMicros * quantity)
        /** Стоимость по цене последней сделки, копейки. */
        val valueKopecks: Long get() = microsToKopecks(lastPriceMicros * quantity)
        val pnlKopecks  : Long get() = valueKopecks - costKopecks
        /** Результат в процентах от вложенного; `null`, если вложено ноль. */
        val pnlPercent  : Double? get() = if (costKopecks == 0L) null else pnlKopecks * 100.0 / costKopecks
        val group       : SecurityGroup get() = groupOverride ?: SecurityGroups.of(ticker)
    }

    /**
     * Группа на экране, как у БКС: бумаги одной группы и — в «Валюте» — свободные деньги на счетах.
     * Итоги по валютам раздельно: курса у офлайн-приложения нет, рубли с юанями не складываются.
     */
    data class Group(
        val group    : SecurityGroup,
        val positions: List<Position>,
        /** Свободные деньги по валютам — только у группы «Валюта». */
        val cash     : List<BrokerAccount>,
    ) {
        /** Стоимость группы по валютам: валюта → копейки. */
        val valueByCurrency: Map<String, Long> get() =
            (positions.map { it.currency to it.valueKopecks } + cash.map { it.currency to it.cashKopecks })
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        /** Результат бумаг группы по валютам. Деньги результата не дают. */
        val pnlByCurrency: Map<String, Long> get() =
            positions.groupBy { it.currency }.mapValues { (_, ps) -> ps.sumOf { it.pnlKopecks } }
        val costByCurrency: Map<String, Long> get() =
            positions.groupBy { it.currency }.mapValues { (_, ps) -> ps.sumOf { it.costKopecks } }
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
        /** Результат за 24 часа / месяц / всё время — по валютам. */
        val periods     : Map<ResultPeriod, List<PeriodResult>> = emptyMap(),
        /** Исполненные сделки без цены, посчитанные по последней известной цене: сделка → цена. */
        val estimates   : Map<BrokerOrder, Long> = emptyMap(),
        /** Исполненные сделки без цены, оценить которые не по чему: в портфель они не вошли. */
        val unpriced    : List<BrokerOrder> = emptyList(),
        /** Когда получены биржевые цены, если хоть одна бумага оценена по ним (#55). */
        val marketAt    : Long? = null,
        /** Курсы к рублю (миллионные доли за единицу) — для итога в рублях, как у брокера. */
        val fxToRub     : Map<String, Long> = emptyMap(),
    ) {
        /**
         * Всё у брокера в РУБЛЯХ, как в приложении БКС: валюта переведена по биржевому курсу.
         * `null`, если курса хоть одной валюты нет — складывать без курса нельзя (#40).
         */
        val totalRubKopecks: Long? get() = if (summaries.isEmpty()) null else summaries.fold(0L as Long?) { acc, s ->
            acc?.let { a -> toRub(s.totalKopecks, s.currency, fxToRub)?.let { a + it } }
        }

        /** Результат периода в рублях — по тем же курсам; `null`, если курса нет. */
        fun periodRub(period: ResultPeriod): PeriodResult? {
            val rs = periods[period] ?: return null
            var pnl = 0L; var base = 0L
            for (r in rs) {
                pnl  += toRub(r.pnlKopecks, r.currency, fxToRub) ?: return null
                base += toRub(r.baseKopecks, r.currency, fxToRub) ?: return null
            }
            return PeriodResult("RUB", pnl, if (base <= 0L) null else pnl * 100.0 / base, base)
        }
        val isEmpty: Boolean get() = accounts.isEmpty() && positions.isEmpty() && activeOrders.isEmpty() &&
            history.isEmpty() && movements.isEmpty() && alerts.isEmpty() && contracts.isEmpty()
        val openAlerts: List<MarginAlerts.State> get() = alerts.filter { it.isOpen }

        /** Группы в порядке экрана БКС; пустые не показываются. */
        val groups: List<Group> get() {
            val byGroup = positions.groupBy { it.group }
            return SecurityGroup.values().mapNotNull { g ->
                val ps   = byGroup[g].orEmpty()
                val cash = if (g == SecurityGroup.CURRENCY) accounts.filter { it.cashKopecks != 0L } else emptyList()
                if (ps.isEmpty() && cash.isEmpty()) null else Group(g, ps, cash)
            }
        }
    }

    val EMPTY = Result(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    /** Период пилюли результата, как у БКС. */
    enum class ResultPeriod(val label: String) {
        DAY("за 24 часа"),
        MONTH("за месяц"),
        ALL("за всё время"),
    }

    /** Результат за период в одной валюте. */
    data class PeriodResult(
        val currency  : String,
        val pnlKopecks: Long,
        /** В процентах от того, что было на счёте в начале периода плюс заведённое за период. */
        val percent   : Double?,
        /** От чего считан процент: было в начале + заведённое. */
        val baseKopecks: Long = 0L,
    )

    /** Сумма в валюте → рубли по курсу; рубль — как есть, без курса — `null`. */
    fun toRub(kopecks: Long, currency: String, fx: Map<String, Long>): Long? =
        if (currency == "RUB") kopecks
        else fx[currency]?.let {
            java.math.BigDecimal(kopecks).multiply(java.math.BigDecimal(it))
                .divide(java.math.BigDecimal(1_000_000L), 0, java.math.RoundingMode.HALF_UP).toLong()
        }

    /**
     * Результат за период — как считает брокер: на сколько изменилось всё, что лежит у брокера,
     * МИНУС деньги, которые за это время завели (и плюс выведенные). Пополнение на 10 000 —
     * не доход: без вычета «за день» после пополнения показывало бы +10 000.
     *
     * Без котировок стоимость меняется только на своих сделках (цена последней сделки), поэтому
     * между сделками результат за 24 часа — ноль; экран это подписывает.
     */
    internal fun periodResults(before: Result, now: Result): List<PeriodResult> {
        val currencies = (now.summaries.map { it.currency } + before.summaries.map { it.currency }).distinct()
        return currencies.map { cur ->
            val totalNow  = now.summaries.firstOrNull { it.currency == cur }?.totalKopecks ?: 0L
            val totalThen = before.summaries.firstOrNull { it.currency == cur }?.totalKopecks ?: 0L
            val depNow    = now.accounts.filter { it.currency == cur }.sumOf { it.netDepositsKopecks }
            val depThen   = before.accounts.filter { it.currency == cur }.sumOf { it.netDepositsKopecks }
            val inflow    = depNow - depThen
            val pnl       = (totalNow - totalThen) - inflow
            val base      = totalThen + inflow
            PeriodResult(cur, pnl, if (base <= 0L) null else pnl * 100.0 / base, base)
        }
    }

    /**
     * [market] — котировки Мосбиржи (#55): цена бумаги на каждый момент берётся из их истории, если она
     * новее своей сделки; размер лота — из котировки (только для пушей: ручной ввод уже в штуках).
     * Без котировок всё считается, как раньше, — по ценам своих сделок.
     */
    fun compute(
        events  : List<BrokerEvent>,
        lotSizes: Map<String, Long> = emptyMap(),
        now     : Long = System.currentTimeMillis(),
        zone    : java.time.ZoneId = java.time.ZoneId.systemDefault(),
        market  : MarketQuotes.Cache? = null,
    ): Result {
        val lots = market?.snapshot?.quotes?.mapValues { it.value.lotSize }.orEmpty() + lotSizes
        val result = computeAt(events, lots, market, now).copy(fxToRub = market?.snapshot?.fxToRub.orEmpty())
        if (result.isEmpty) return result
        val monthAgo = java.time.Instant.ofEpochMilli(now).atZone(zone).minusMonths(1).toInstant().toEpochMilli()
        fun upTo(t: Long) = computeAt(events.filter { it.timestamp < t }, lots, market, t)
        return result.copy(periods = mapOf(
            ResultPeriod.DAY   to periodResults(upTo(now - 24 * 3_600_000L), result),
            ResultPeriod.MONTH to periodResults(upTo(monthAgo), result),
            ResultPeriod.ALL   to periodResults(EMPTY, result),
        ))
    }

    private fun computeAt(
        events  : List<BrokerEvent>,
        lotSizes: Map<String, Long>,
        market  : MarketQuotes.Cache? = null,
        at      : Long = Long.MAX_VALUE,
    ): Result {
        val sorted = events.sortedBy { it.timestamp }

        // lastAt — когда цена стала известна: своя сделка или указанная цена новее биржевой — побеждает она.
        data class Acc(var qty: Long = 0, var avg: Long = 0, var last: Long = 0, var lastAt: Long = 0)
        val holdings = linkedMapOf<Triple<String, String, String>, Acc>()        // broker, ticker, currency
        val cash     = linkedMapOf<Pair<String, String>, Long>()                  // broker, currency
        val deposits = linkedMapOf<Pair<String, String>, Long>()
        // Счета брокера: номер → название. Название — из последнего пуша, где оно было.
        val known = linkedMapOf<Pair<String, String>, Contract>()
        val hidden = mutableSetOf<Pair<String, String>>()
        // Последняя известная цена бумаги — переживает продажу всех бумаг (у проданной acc обнуляется
        // только количество, но держим отдельно, чтобы не зависеть от этого).
        val lastPrice = mutableMapOf<Triple<String, String, String>, Long>()
        val estimates = linkedMapOf<BrokerOrder, Long>()
        val unpriced  = mutableListOf<BrokerOrder>()
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
            // Счёт, заведённый или скрытый человеком. Действует ПОСЛЕДНЯЯ отметка: удалил и завёл
            // снова — счёт вернулся.
            is BrokerAccountMark -> {
                val key = contractKey(e.contract)
                if (key != null) {
                    if (e.hidden) hidden += e.broker to key
                    else { hidden -= e.broker to key; see(e.broker, e.contract, e.label) }
                }
            }
            // Цена, указанная вручную: двигает только «текущую цену» уже купленной бумаги.
            is BrokerPriceMark -> holdings[Triple(e.broker, e.ticker, e.currency)]?.let {
                it.last = e.priceMicros
                it.lastAt = e.timestamp
                lastPrice[Triple(e.broker, e.ticker, e.currency)] = e.priceMicros
            }
            is BrokerOrder -> {
                see(e.broker, e.contract, null)
                if (e.status != OrderStatus.FILLED) continue
                val holdKey = Triple(e.broker, e.ticker, e.currency)
                // Цены нет — последняя известная по бумаге; нет и её — сделка не считается (#52).
                val price = if (e.priceKnown) e.priceMicros else {
                    // Биржевая цена на момент сделки (#55) точнее последней своей.
                    val known = market?.priceAt(e.ticker.uppercase(), e.timestamp)?.priceMicros
                        ?.takeIf { market?.snapshot?.quotes?.get(e.ticker.uppercase())?.currency.let { c -> c == null || c == e.currency } }
                        ?: holdings[holdKey]?.last?.takeIf { it > 0L } ?: lastPrice[holdKey]
                    if (known == null) { unpriced += e; continue }
                    estimates[e] = known
                    known
                }
                // Лоты — у пуша; ручной ввод уже в штуках (#49), умножать его на лот нельзя.
                val qty    = if (e.isManual) e.lots else e.lots * (lotSizes[e.ticker.uppercase()] ?: lotSizes[e.ticker] ?: 1L)
                val amount = microsToKopecks(price * qty)
                val acc    = holdings.getOrPut(holdKey) { Acc() }
                val cashKey = e.broker to e.currency
                when (e.side) {
                    OrderSide.BUY -> {
                        val newQty = acc.qty + qty
                        acc.avg = if (newQty == 0L) 0L else (acc.avg * acc.qty + price * qty) / newQty
                        acc.qty = newQty
                        cash[cashKey] = (cash[cashKey] ?: 0L) - amount
                    }
                    OrderSide.SELL -> {
                        acc.qty -= qty
                        if (acc.qty <= 0L) { acc.qty = 0L; acc.avg = 0L }
                        cash[cashKey] = (cash[cashKey] ?: 0L) + amount
                    }
                }
                acc.last = price
                acc.lastAt = e.timestamp
                lastPrice[holdKey] = price
            }
        }

        var marketAt: Long? = null
        val positions = holdings.filter { it.value.qty > 0L }.map { (k, a) ->
            val ticker = k.second.uppercase()
            val quote = market?.snapshot?.quotes?.get(ticker)
            // Биржевая цена — только в валюте бумаги и только если она новее своей. Для начала периода,
            // которое старше всей истории котировок, — самая ранняя точка: иначе начало стояло бы на
            // цене своей сделки, конец — на рыночной, и весь их разрыв попал бы в результат «за месяц».
            val point = (market?.priceAt(ticker, at)
                ?: market?.history?.get(ticker)?.firstOrNull()?.takeIf { at != Long.MAX_VALUE })
                ?.takeIf { it.at > a.lastAt && (quote == null || quote.currency == k.third) }
            if (point != null) marketAt = maxOf(marketAt ?: 0L, point.at)
            Position(
                k.first, k.second, k.third, a.qty, a.avg, point?.priceMicros ?: a.last,
                marketAt = point?.at, groupOverride = quote?.group,
            )
        }.sortedByDescending { it.valueKopecks }

        val contracts = known.filterKeys { it !in hidden }.values.toList()
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
            estimates = estimates,
            unpriced  = unpriced.sortedByDescending { it.timestamp },
            marketAt  = marketAt,
        )
    }
}
