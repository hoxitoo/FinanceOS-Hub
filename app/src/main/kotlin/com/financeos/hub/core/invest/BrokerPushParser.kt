package com.financeos.hub.core.invest

import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.ciRegex

/**
 * Пуши приложения брокера. Пока — только БКС, и только форматы, снятые с устройства:
 *
 * - «Пополнение счета Вы пополнили счет №580922/19-м на 10 000 RUB»
 * - «LQDT: заявка активна Лимитная заявка на покупку 4760 лотов LQDT по 2.0984»
 * - «LQDT: заявка отменена …», «LQDT: заявка исполнена … по 2.0985»
 *
 * Тексты склеены так же, как их собирает `PushNotificationListener`: заголовок, затем текст.
 * Форматы, которых не видели (вывод денег, частичное исполнение, продажа), НЕ угадываются: неверно
 * разобранная сделка хуже пропущенной — она молча врёт в стоимости портфеля.
 *
 * Кириллица — только через [ciRegex] (инвариант #13).
 */
object BrokerPushParser {

    const val BKS = "БКС"

    private val deposit = ciRegex(
        """пополнили\s+сч[её]т\s+№\s*([^\s,;]+)\s+на\s+([\d\s]+(?:[.,]\d{1,2})?)\s*(RUB|USD|EUR|CNY|₽)"""
    )

    private val orderHeader = ciRegex("""([A-Z0-9.]{1,12}):\s*заявка\s+(активна|отменена|исполнена)(?![А-Яа-яёЁ])""")

    private val orderBody = ciRegex(
        """(?:(\p{L}+)\s+)?заявка\s+на\s+(покупку|продажу)\s+(\d+)\s+лот\p{L}*\s+([A-Z0-9.]{1,12})\s+по\s+(\d+(?:[.,]\d+)?)"""
    )

    fun parse(body: String, timestamp: Long, broker: String = BKS): BrokerEvent? =
        parseDeposit(body, timestamp, broker) ?: parseOrder(body, timestamp, broker)

    private fun parseDeposit(body: String, ts: Long, broker: String): BrokerCashMove? {
        val m = deposit.find(body) ?: return null
        val kopecks = AmountParser.toKopecks(m.groupValues[2])
        if (kopecks <= 0L) return null
        val currency = m.groupValues[3].uppercase().let { if (it == "₽") "RUB" else it }
        return BrokerCashMove(
            broker        = broker,
            timestamp     = ts,
            contract      = m.groupValues[1].trimEnd('.', ','),
            amountKopecks = kopecks,
            currency      = currency,
        )
    }

    private fun parseOrder(body: String, ts: Long, broker: String): BrokerOrder? {
        val head = orderHeader.find(body) ?: return null
        val m    = orderBody.find(body) ?: return null
        val ticker = m.groupValues[4].uppercase()
        // Заголовок и текст обязаны говорить об одной бумаге — иначе это не тот формат.
        if (!head.groupValues[1].equals(ticker, ignoreCase = true)) return null
        val lots = m.groupValues[3].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val price = priceMicros(m.groupValues[5]) ?: return null
        val status = when (head.groupValues[2].lowercase()) {
            "активна"   -> OrderStatus.ACTIVE
            "отменена"  -> OrderStatus.CANCELLED
            else        -> OrderStatus.FILLED
        }
        return BrokerOrder(
            broker      = broker,
            timestamp   = ts,
            ticker      = ticker,
            side        = if (m.groupValues[2].lowercase() == "покупку") OrderSide.BUY else OrderSide.SELL,
            lots        = lots,
            priceMicros = price,
            status      = status,
            kind        = m.groupValues[1].takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
        )
    }

    /** «2.0985» → 2 098 500. Больше шести знаков после запятой биржа не печатает. */
    internal fun priceMicros(text: String): Long? {
        val s = text.replace(',', '.')
        val whole = s.substringBefore('.').toLongOrNull() ?: return null
        val frac  = s.substringAfter('.', "").take(6).padEnd(6, '0')
        val fracMicros = frac.toLongOrNull() ?: return null
        val micros = whole * 1_000_000 + fracMicros
        return micros.takeIf { it > 0 }
    }
}
