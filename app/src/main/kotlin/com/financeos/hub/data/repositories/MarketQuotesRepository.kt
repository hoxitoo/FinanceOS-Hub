package com.financeos.hub.data.repositories

import android.content.Context
import com.financeos.hub.core.invest.BrokerOrder
import com.financeos.hub.core.invest.MarketQuotes
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.core.invest.SecurityGroups
import com.financeos.hub.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Котировки Мосбиржи (#55): сеть, кэш на диске, решение «пора ли обновлять».
 *
 * В сеть уходят ТОЛЬКО тикеры своих бумаг — ни сумм, ни количества, ни счетов. Пока переключатель в
 * настройках выключен, не уходит ничего. Без сети — последний полученный снимок, с его временем.
 */
@Singleton
class MarketQuotesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
    private val brokerEvents: BrokerEventRepository,
) {
    private val file get() = File(context.filesDir, "moex_quotes.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _cache = MutableStateFlow(MarketQuotes.Cache.EMPTY)
    /** Последний снимок и история цен; пустой, пока ничего не получено. */
    val cache: StateFlow<MarketQuotes.Cache> = _cache.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        scope.launch { _cache.value = MarketQuotes.fromJson(runCatching { file.readText() }.getOrNull()) }
    }

    /**
     * Обновить, если включено и снимок старше [maxAgeMs] (по умолчанию — почти сутки: обновление
     * раз в день, решение пользователя). `force` — по кнопке «Обновить». `true` — снимок получен.
     */
    suspend fun refresh(force: Boolean = false, maxAgeMs: Long = 20L * 3_600_000): Boolean = mutex.withLock {
        if (!prefs.marketQuotesEnabled.first()) return false
        val now = System.currentTimeMillis()
        val last = _cache.value.snapshot?.fetchedAt ?: runCatching {
            MarketQuotes.fromJson(file.readText()).snapshot?.fetchedAt
        }.getOrNull()
        if (!force && last != null && now - last < maxAgeMs) return false

        val events = brokerEvents.observeAll().first()
        // Бумаги, которые есть или были: у проданной цена не нужна, у рыночной заявки без цены (#52)
        // нужна история на момент сделки.
        val held = Portfolio.compute(events).positions.map { it.ticker }
        val traded = events.filterIsInstance<BrokerOrder>().map { it.ticker }
        val tickers = (held + traded).map { it.uppercase() }.distinct()
            // Валюта на счёте — деньги, а не бумага (#51): котировка ей не нужна, нужен курс.
            .filter { SecurityGroups.cashCurrency(it) == null && it.matches(Regex("""[A-Z0-9_.]{1,20}""")) }

        _refreshing.value = true
        try {
            val quotes = if (tickers.isEmpty()) emptyList() else {
                val chunks = tickers.chunked(50)
                chunks.flatMap { c -> get(MarketQuotes.sharesUrl(c))?.let(MarketQuotes::parseShares).orEmpty() } +
                    chunks.flatMap { c -> get(MarketQuotes.bondsUrl(c))?.let(MarketQuotes::parseBonds).orEmpty() }
            }
            val fx = get(MarketQuotes.fxUrl())?.let(MarketQuotes::parseFx).orEmpty()
            // Ничего не пришло — сеть недоступна или биржа отвечает не так: старый снимок остаётся.
            if (quotes.isEmpty() && fx.isEmpty()) return false
            val snapshot = MarketQuotes.Snapshot(
                quotes    = quotes.associateBy { it.ticker },
                // Курс, которого в этот раз нет, берётся из прошлого снимка — лучше вчерашний, чем никакого.
                fxToRub   = _cache.value.snapshot?.fxToRub.orEmpty() + fx,
                fetchedAt = now,
            )
            val merged = MarketQuotes.merge(_cache.value, snapshot)
            withContext(Dispatchers.IO) {
                val tmp = File(context.filesDir, "moex_quotes.json.tmp")
                tmp.writeText(MarketQuotes.toJson(merged))
                tmp.renameTo(file)
            }
            _cache.value = merged
            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return false
        } finally {
            _refreshing.value = false
        }
    }

    /** Обновить в фоне — экран открыт, а снимок устарел. */
    fun refreshIfStale() { scope.launch { refresh() } }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FinanceOS-Hub")
            }
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
