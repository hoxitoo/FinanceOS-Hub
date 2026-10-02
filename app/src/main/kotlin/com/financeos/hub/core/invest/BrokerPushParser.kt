package com.financeos.hub.core.invest

import com.financeos.hub.core.parser.AmountParser
import com.financeos.hub.core.parser.ciRegex

/**
 * Пуши приложения брокера. Пока — только БКС, и только форматы, снятые с устройства:
 *
 * - «Пополнение счета Вы пополнили счет №580922/19-м на 10 000 RUB»
 * - «LQDT: заявка активна Лимитная заявка на покупку 4760 лотов LQDT по 2.0984»
 * - «LQDT: заявка отменена …», «LQDT: заявка исполнена … по 2.0985»
 * - «Перевод между счетами 189 RUB. Со счета №580922/19-м на счет 3468071/25 (Облигации)»
 * - «Критично низкий баланс счета 3468071/25 (Облигации) Пополните … на сумму от 188.02. …»
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

    // Номер счёта БКС: цифры, «/», дефис и буквы — «580922/19-м», «1230947/21-м-иис», «3468071/25».
    private const val CONTRACT = """№?\s*(\p{N}[\p{L}\p{N}/-]*)(?:\s*\(([^)]{1,40})\))?"""

    // «Перевод между счетами 189 RUB. Со счета №580922/19-м на счет 3468071/25 (Облигации)»
    private val internalTransfer = ciRegex(
        """Перевод\s+между\s+сч[её]тами\s+([\d\s]+(?:[.,]\d{1,2})?)\s*(RUB|USD|EUR|CNY|HKD|₽)\.?\s+""" +
            """Со\s+сч[её]та\s+$CONTRACT\s+на\s+сч[её]т\s+$CONTRACT"""
    )

    // «Критично низкий баланс счета 3468071/25 (Облигации) Пополните счет 3468071/25 (Облигации) на
    // сумму от 188.02. Если стоимость портфеля станет ниже 0, брокер приступит к закрытию…»
    private val marginHeader = ciRegex("""Критично\s+низкий\s+баланс\s+сч[её]та\s+$CONTRACT""")
    private val marginAmount = ciRegex("""на\s+сумму\s+от\s+(\d[\d\s]*(?:[.,]\d{1,2})?)""")

    fun parse(body: String, timestamp: Long, broker: String = BKS): BrokerEvent? =
        parseDeposit(body, timestamp, broker)
            ?: parseInternalTransfer(body, timestamp, broker)
            ?: parseMarginAlert(body, timestamp, broker)
            ?: parseOrder(body, timestamp, broker)

    private fun parseInternalTransfer(body: String, ts: Long, broker: String): BrokerInternalTransfer? {
        val m = internalTransfer.find(body) ?: return null
        val kopecks = AmountParser.toKopecks(m.groupValues[1])
        if (kopecks <= 0L) return null
        val from = m.groupValues[3].trimEnd('.', ',')
        val to   = m.groupValues[5].trimEnd('.', ',')
        // Перевод «сам в себя» — не тот формат, что мы видели; не угадываем.
        if (contractKey(from) == contractKey(to)) return null
        return BrokerInternalTransfer(
            broker        = broker,
            timestamp     = ts,
            amountKopecks = kopecks,
            currency      = m.groupValues[2].uppercase().let { if (it == "₽") "RUB" else it },
            fromContract  = from,
            toContract    = to,
            fromLabel     = m.groupValues[4].trim().takeIf { it.isNotEmpty() },
            toLabel       = m.groupValues[6].trim().takeIf { it.isNotEmpty() },
        )
    }

    private fun parseMarginAlert(body: String, ts: Long, broker: String): BrokerMarginAlert? {
        val head = marginHeader.find(body) ?: return null
        // Без суммы — не угадываем: карточка «пополните на ?» хуже отсутствующей.
        val amount = marginAmount.find(body)?.let { AmountParser.toKopecks(it.groupValues[1].trim()) }
            ?.takeIf { it > 0L } ?: return null
        return BrokerMarginAlert(
            broker          = broker,
            timestamp       = ts,
            contract        = head.groupValues[1].trimEnd('.', ','),
            label           = head.groupValues[2].trim().takeIf { it.isNotEmpty() },
            requiredKopecks = amount,
        )
    }

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
