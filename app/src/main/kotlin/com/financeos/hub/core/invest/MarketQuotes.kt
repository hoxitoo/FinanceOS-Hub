package com.financeos.hub.core.invest

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Котировки Мосбиржи (ISS, iss.moex.com) — инвариант #55.
 *
 * Открытый ISS отдаёт данные БЕЗ ключа и С ЗАДЕРЖКОЙ 15 минут; без задержки — только по платной
 * подписке. Обновление раз в сутки (решение пользователя), поэтому задержка значения не имеет, но
 * экран всё равно пишет «на ДД.ММ ЧЧ:ММ».
 *
 * Здесь — чистые функции: разбор ответов ISS, история цен и формат кэша. Сеть — в
 * `MarketQuotesRepository`. Ответы ISS разбираются ПО ИМЕНАМ КОЛОНОК, а не по позициям: биржа
 * добавляет колонки, и позиционный разбор молча читал бы чужое поле.
 */
object MarketQuotes {

    /** Котировка бумаги: цена ОДНОЙ бумаги в миллионных долях валюты [currency]. */
    data class Quote(
        val ticker     : String,
        val priceMicros: Long,
        val currency   : String,
        /** Бумаг в лоте — пуш брокера пишет количество лотами («20 лотов LQDT»). */
        val lotSize    : Long,
        /** Группа по режиму торгов биржи — точнее справочника по форме тикера (#48). */
        val group      : SecurityGroup?,
        /** Цена закрытия прошлой торговой сессии — для результата «за 24 часа» с первого дня. */
        val prevPriceMicros: Long?,
    )

    /** Снимок: котировки, курсы валют к рублю и момент получения. */
    data class Snapshot(
        val quotes   : Map<String, Quote>,
        /** Валюта → рублей за единицу, миллионные доли. Рубль не хранится — он 1. */
        val fxToRub  : Map<String, Long>,
        val fetchedAt: Long,
    )

    /** Точка истории цены бумаги: момент и цена одной бумаги. */
    data class PricePoint(val at: Long, val priceMicros: Long)

    /** Всё, что лежит в кэше: последний снимок и дневная история цен по тикерам. */
    data class Cache(
        val snapshot: Snapshot?,
        val history : Map<String, List<PricePoint>>,
    ) {
        /** Цена бумаги на момент [at]: последняя точка истории не позже него. */
        fun priceAt(ticker: String, at: Long): PricePoint? =
            history[ticker]?.lastOrNull { it.at <= at }

        companion object { val EMPTY = Cache(null, emptyMap()) }
    }

    /** Режимы торгов, которые считаются ОСНОВНЫМИ, — по порядку предпочтения. */
    private val SHARE_BOARDS = listOf("TQBR", "TQTF", "TQTD", "TQTE", "TQIF", "TQPI", "TQBS")
    private val BOND_BOARDS  = listOf("TQOB", "TQCB", "TQIR", "TQOD", "TQOE", "TQOY")
    private val FUND_BOARDS  = setOf("TQTF", "TQTD", "TQTE", "TQIF", "TQPI")

    /**
     * Валютные инструменты биржи → валюта. Курс — рублей за единицу. Доллара и евро здесь НЕТ:
     * биржевые торги ими остановлены в июне 2024, и `USD000UTSTOM` отдавал бы курс двухлетней
     * давности. Их курс — официальный ЦБ, который ISS публикует отдельно ([cbrUrl]).
     */
    val FX_TICKERS = mapOf(
        "CNYRUB_TOM"   to "CNY",
        "HKDRUB_TOM"   to "HKD",
    )

    private const val ISS = "https://iss.moex.com/iss"

    fun sharesUrl(tickers: Collection<String>): String =
        "$ISS/engines/stock/markets/shares/securities.json?iss.meta=off&iss.only=securities,marketdata" +
            "&securities=${tickers.joinToString(",")}" +
            "&securities.columns=SECID,BOARDID,LOTSIZE,CURRENCYID,PREVPRICE,PREVLEGALCLOSEPRICE" +
            "&marketdata.columns=SECID,BOARDID,LAST,LCURRENTPRICE,MARKETPRICE"

    fun bondsUrl(tickers: Collection<String>): String =
        "$ISS/engines/stock/markets/bonds/securities.json?iss.meta=off&iss.only=securities,marketdata" +
            "&securities=${tickers.joinToString(",")}" +
            "&securities.columns=SECID,BOARDID,LOTSIZE,FACEVALUE,FACEUNIT,ACCRUEDINT,PREVPRICE,CURRENCYID" +
            "&marketdata.columns=SECID,BOARDID,LAST,MARKETPRICE"

    fun fxUrl(): String =
        "$ISS/engines/currency/markets/selt/securities.json?iss.meta=off&iss.only=securities,marketdata" +
            "&securities=${FX_TICKERS.keys.joinToString(",")}" +
            "&securities.columns=SECID,BOARDID,PREVPRICE,PREVWAPRICE" +
            "&marketdata.columns=SECID,BOARDID,LAST,WAPRICE,MARKETPRICE"

    /** Официальные курсы ЦБ (доллар, евро), которые ISS публикует в статистике валютного рынка. */
    fun cbrUrl(): String = "$ISS/statistics/engines/currency/markets/selt/rates.json?iss.meta=off"

    // ── Разбор ISS ──────────────────────────────────────────────────────────────

    /** Блок ISS «{columns:[…], data:[[…]]}» → строки «имя колонки → значение». */
    private fun rows(root: JSONObject, block: String): List<Map<String, Any?>> {
        val obj = root.optJSONObject(block) ?: return emptyList()
        val cols = obj.optJSONArray("columns") ?: return emptyList()
        val data = obj.optJSONArray("data") ?: return emptyList()
        val names = (0 until cols.length()).map { cols.optString(it) }
        return (0 until data.length()).mapNotNull { i ->
            val row = data.optJSONArray(i) ?: return@mapNotNull null
            names.indices.associate { j -> names[j] to row.opt(j).takeUnless { it == JSONObject.NULL } }
        }
    }

    /**
     * Число из ISS → миллионные доли; ноль, отрицательное и абсурдное — «нет цены». Ответ сети —
     * чужие данные: «1e999999999» без потолка разворачивался бы в миллиард цифр (зависание), а
     * `toLong` молча обрезал бы переполнение в мусорную цену. Потолок — [MAX_UNITS] за штуку.
     */
    internal fun micros(v: Any?): Long? {
        val d = when (v) {
            is Number -> v.toString().toBigDecimalOrNull()
            is String -> v.toBigDecimalOrNull()
            else      -> null
        } ?: return null
        if (d.signum() <= 0 || d > MAX_UNITS) return null
        return runCatching { d.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    /** Сто миллионов за штуку или за единицу валюты — больше на бирже не бывает. */
    private val MAX_UNITS = BigDecimal(100_000_000)

    /** Размер лота из ответа: 1…[MAX_LOT], иначе 1. Лот умножает количество, и мусор в нём — переполнение. */
    private const val MAX_LOT = 1_000_000L
    private fun lotOf(v: Any?): Long = (v as? Number)?.toLong()?.takeIf { it in 1..MAX_LOT } ?: 1L

    /** Тикер из ответа: только то, что могло быть в запросе ([TICKER]), иначе строка отбрасывается. */
    val TICKER = Regex("""[A-Z0-9_.]{1,20}""")

    private fun currencyOf(raw: Any?): String = when (val c = raw?.toString()?.uppercase()) {
        null, "", "SUR", "RUR", "RUB" -> "RUB"
        else                          -> c
    }

    /** Ответ рынка акций (акции и фонды) → котировки; одна на бумагу, по основному режиму торгов. */
    fun parseShares(json: String): List<Quote> = runCatching {
        val root = JSONObject(json)
        val sec = rows(root, "securities")
        val md  = rows(root, "marketdata")
        sec.groupBy { it["SECID"]?.toString() ?: "" }.mapNotNull { (secid, boards) ->
            if (!secid.uppercase().matches(TICKER)) return@mapNotNull null
            boards.sortedBy { b -> SHARE_BOARDS.indexOf(b["BOARDID"]).let { if (it < 0) 99 else it } }
                .firstNotNullOfOrNull { b ->
                    val board = b["BOARDID"]?.toString()
                    val m = md.firstOrNull { it["SECID"] == secid && it["BOARDID"] == board }
                    val prev = micros(b["PREVPRICE"]) ?: micros(b["PREVLEGALCLOSEPRICE"])
                    val price = micros(m?.get("LAST")) ?: micros(m?.get("LCURRENTPRICE")) ?:
                        micros(m?.get("MARKETPRICE")) ?: prev ?: return@firstNotNullOfOrNull null
                    Quote(
                        ticker = secid.uppercase(), priceMicros = price,
                        currency = currencyOf(b["CURRENCYID"]),
                        lotSize = lotOf(b["LOTSIZE"]),
                        group = when {
                            board in FUND_BOARDS -> SecurityGroup.FUNDS
                            board == "TQBR" || board == "TQBS" -> SecurityGroup.SHARES
                            else -> null
                        },
                        prevPriceMicros = prev,
                    )
                }
        }
    }.getOrDefault(emptyList())

    /**
     * Ответ рынка облигаций → котировки. Биржа даёт цену в ПРОЦЕНТАХ от номинала; цена одной бумаги
     * = номинал × процент / 100 + НКД (накопленный купонный доход) — так считает стоимость брокер.
     */
    fun parseBonds(json: String): List<Quote> = runCatching {
        val root = JSONObject(json)
        val sec = rows(root, "securities")
        val md  = rows(root, "marketdata")
        sec.groupBy { it["SECID"]?.toString() ?: "" }.mapNotNull { (secid, boards) ->
            if (!secid.uppercase().matches(TICKER)) return@mapNotNull null
            boards.sortedBy { b -> BOND_BOARDS.indexOf(b["BOARDID"]).let { if (it < 0) 99 else it } }
                .firstNotNullOfOrNull { b ->
                    val board = b["BOARDID"]?.toString()
                    val m = md.firstOrNull { it["SECID"] == secid && it["BOARDID"] == board }
                    val face = micros(b["FACEVALUE"]) ?: return@firstNotNullOfOrNull null
                    val accrued = micros(b["ACCRUEDINT"]) ?: 0L
                    // Цена одной облигации; абсурдная (переполнение, больше потолка) — «нет цены».
                    fun perBond(pct: Long?): Long? = pct?.let {
                        runCatching {
                            BigDecimal(face).multiply(BigDecimal(it)).divide(BigDecimal(100_000_000L), 0, RoundingMode.HALF_UP)
                                .add(BigDecimal(accrued)).longValueExact()
                        }.getOrNull()?.takeIf { p -> p <= MAX_UNITS.toLong() * 1_000_000 }
                    }
                    val pct = micros(m?.get("LAST")) ?: micros(m?.get("MARKETPRICE")) ?: micros(b["PREVPRICE"])
                        ?: return@firstNotNullOfOrNull null
                    Quote(
                        ticker = secid.uppercase(), priceMicros = perBond(pct) ?: return@firstNotNullOfOrNull null,
                        currency = currencyOf(b["FACEUNIT"] ?: b["CURRENCYID"]),
                        lotSize = lotOf(b["LOTSIZE"]),
                        group = SecurityGroup.BONDS,
                        prevPriceMicros = perBond(micros(b["PREVPRICE"])),
                    )
                }
        }
    }.getOrDefault(emptyList())

    /** Ответ валютного рынка → курсы к рублю. */
    fun parseFx(json: String): Map<String, Long> = runCatching {
        val root = JSONObject(json)
        val sec = rows(root, "securities")
        val md  = rows(root, "marketdata")
        FX_TICKERS.mapNotNull { (secid, cur) ->
            val m = md.filter { it["SECID"] == secid }
            val s = sec.filter { it["SECID"] == secid }
            val rate = m.firstNotNullOfOrNull { micros(it["LAST"]) ?: micros(it["WAPRICE"]) ?: micros(it["MARKETPRICE"]) }
                ?: s.firstNotNullOfOrNull { micros(it["PREVWAPRICE"]) ?: micros(it["PREVPRICE"]) }
            rate?.let { cur to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Ответ со статистикой курсов → доллар и евро по курсу ЦБ (блок «cbrf»). */
    fun parseCbr(json: String): Map<String, Long> = runCatching {
        val row = rows(JSONObject(json), "cbrf").firstOrNull() ?: return emptyMap()
        listOfNotNull(
            micros(row["CBRF_USD_LAST"])?.let { "USD" to it },
            micros(row["CBRF_EUR_LAST"])?.let { "EUR" to it },
        ).toMap()
    }.getOrDefault(emptyMap())

    // ── История ─────────────────────────────────────────────────────────────────

    private const val DAY_MS = 24L * 3_600_000

    /** Сколько истории держать: чуть больше года — хватает на «месяц» и на сравнения. */
    private const val KEEP_MS = 400L * DAY_MS

    /**
     * Добавить снимок в историю. Кроме цены «сейчас», кладётся цена прошлого закрытия сутками
     * раньше — так результат «за 24 часа» есть уже после первого обновления. Одна точка на сутки:
     * повторное обновление в тот же день заменяет её.
     */
    fun merge(cache: Cache, snapshot: Snapshot): Cache {
        val history = cache.history.toMutableMap()
        for (q in snapshot.quotes.values) {
            val points = history[q.ticker].orEmpty().toMutableList()
            fun put(at: Long, price: Long) {
                points.removeAll { it.at / DAY_MS == at / DAY_MS }
                points += PricePoint(at, price)
            }
            q.prevPriceMicros?.let { prev ->
                val dayBefore = snapshot.fetchedAt - DAY_MS
                if (points.none { it.at / DAY_MS == dayBefore / DAY_MS }) put(dayBefore, prev)
            }
            put(snapshot.fetchedAt, q.priceMicros)
            history[q.ticker] = points.filter { snapshot.fetchedAt - it.at <= KEEP_MS }.sortedBy { it.at }
        }
        return Cache(snapshot, history)
    }

    // ── Кэш на диске ────────────────────────────────────────────────────────────

    fun toJson(cache: Cache): String {
        val root = JSONObject()
        cache.snapshot?.let { s ->
            root.put("fetchedAt", s.fetchedAt)
            root.put("fx", JSONObject().apply { s.fxToRub.forEach { (k, v) -> put(k, v) } })
            root.put("quotes", JSONArray().apply {
                s.quotes.values.forEach { q ->
                    put(JSONObject().apply {
                        put("t", q.ticker); put("p", q.priceMicros); put("c", q.currency); put("l", q.lotSize)
                        q.group?.let { put("g", it.name) }
                        q.prevPriceMicros?.let { put("pp", it) }
                    })
                }
            })
        }
        root.put("history", JSONObject().apply {
            cache.history.forEach { (t, pts) ->
                put(t, JSONArray().apply { pts.forEach { put(JSONArray().put(it.at).put(it.priceMicros)) } })
            }
        })
        return root.toString()
    }

    /** Повреждённый или чужой файл — пустой кэш, а не падение: котировки — улучшение, не основа. */
    fun fromJson(json: String?): Cache = runCatching {
        if (json.isNullOrBlank()) return Cache.EMPTY
        val root = JSONObject(json)
        val snapshot = if (root.has("fetchedAt")) {
            val fx = root.optJSONObject("fx")
            val qs = root.optJSONArray("quotes") ?: JSONArray()
            Snapshot(
                quotes = (0 until qs.length()).map { qs.getJSONObject(it) }.associate { o ->
                    val q = Quote(
                        ticker = o.getString("t"), priceMicros = o.getLong("p"), currency = o.getString("c"),
                        lotSize = o.optLong("l", 1L),
                        group = o.optString("g").takeIf { it.isNotEmpty() }
                            ?.let { g -> runCatching { SecurityGroup.valueOf(g) }.getOrNull() },
                        prevPriceMicros = if (o.has("pp")) o.getLong("pp") else null,
                    )
                    q.ticker to q
                },
                fxToRub = fx?.keys()?.asSequence()?.associateWith { fx.getLong(it) }.orEmpty(),
                fetchedAt = root.getLong("fetchedAt"),
            )
        } else null
        val h = root.optJSONObject("history")
        val history = h?.keys()?.asSequence()?.associateWith { t ->
            val arr = h.getJSONArray(t)
            (0 until arr.length()).map { i -> arr.getJSONArray(i).let { PricePoint(it.getLong(0), it.getLong(1)) } }
        }.orEmpty()
        Cache(snapshot, history)
    }.getOrDefault(Cache.EMPTY)
}
