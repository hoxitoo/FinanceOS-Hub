package com.financeos.hub.core.invest

import com.financeos.hub.core.database.entities.BrokerEventEntity

/**
 * Модель события брокера ↔ строка `broker_events`. Чистые функции — проверяются тестом туда и
 * обратно, чтобы новое поле не потерялось при записи молча (инвариант #29 со стороны данных).
 */
object BrokerEventMapper {

    const val CASH     = "CASH"
    const val TRANSFER = "TRANSFER"
    const val ORDER    = "ORDER"
    const val ALERT    = "ALERT"
    const val ACCOUNT  = "ACCOUNT"
    const val PRICE    = "PRICE"

    fun toEntity(e: BrokerEvent, id: String, rawText: String, now: Long = System.currentTimeMillis()): BrokerEventEntity {
        val base = BrokerEventEntity(
            id = id, broker = e.broker, kind = "", timestamp = e.timestamp, amountKopecks = null,
            currency = "RUB", contract = null, contractLabel = null, toContract = null, toContractLabel = null,
            ticker = null, side = null, lots = null, priceMicros = null, status = null, orderKind = null,
            dismissed = false, rawText = rawText, createdAt = now,
        )
        return when (e) {
            is BrokerCashMove -> base.copy(
                kind = CASH, amountKopecks = e.amountKopecks, currency = e.currency, contract = e.contract,
            )
            is BrokerInternalTransfer -> base.copy(
                kind = TRANSFER, amountKopecks = e.amountKopecks, currency = e.currency,
                contract = e.fromContract, contractLabel = e.fromLabel,
                toContract = e.toContract, toContractLabel = e.toLabel,
            )
            is BrokerMarginAlert -> base.copy(
                kind = ALERT, amountKopecks = e.requiredKopecks, currency = e.currency,
                contract = e.contract, contractLabel = e.label, dismissed = e.dismissed,
            )
            is BrokerOrder -> base.copy(
                kind = ORDER, currency = e.currency, ticker = e.ticker, side = e.side.name, lots = e.lots,
                priceMicros = e.priceMicros, status = e.status.name, orderKind = e.kind, contract = e.contract,
            )
            is BrokerAccountMark -> base.copy(
                kind = ACCOUNT, contract = e.contract, contractLabel = e.label, dismissed = e.hidden,
            )
            is BrokerPriceMark -> base.copy(
                kind = PRICE, ticker = e.ticker, priceMicros = e.priceMicros, currency = e.currency,
            )
        }
    }

    /** Строка → событие; строка неизвестного вида или с недостающим полем — `null`, а не падение. */
    fun toDomain(r: BrokerEventEntity): BrokerEvent? = when (r.kind) {
        CASH -> r.amountKopecks?.let {
            BrokerCashMove(r.broker, r.timestamp, r.contract, it, r.currency, id = r.id)
        }
        TRANSFER -> if (r.amountKopecks != null && r.contract != null && r.toContract != null) {
            BrokerInternalTransfer(
                r.broker, r.timestamp, r.amountKopecks, r.currency,
                r.contract, r.toContract, r.contractLabel, r.toContractLabel, id = r.id,
            )
        } else null
        ALERT -> if (r.amountKopecks != null && r.contract != null) {
            BrokerMarginAlert(
                r.broker, r.timestamp, r.contract, r.contractLabel, r.amountKopecks, r.currency,
                id = r.id, dismissed = r.dismissed,
            )
        } else null
        ORDER -> {
            val side   = runCatching { OrderSide.valueOf(r.side ?: "") }.getOrNull()
            val status = runCatching { OrderStatus.valueOf(r.status ?: "") }.getOrNull()
            if (r.ticker != null && side != null && status != null && r.lots != null && r.priceMicros != null) {
                BrokerOrder(
                    r.broker, r.timestamp, r.ticker, side, r.lots, r.priceMicros, status, r.orderKind, r.currency,
                    contract = r.contract, id = r.id,
                )
            } else null
        }
        ACCOUNT -> r.contract?.let {
            BrokerAccountMark(r.broker, r.timestamp, it, r.contractLabel, hidden = r.dismissed, id = r.id)
        }
        PRICE -> if (r.ticker != null && r.priceMicros != null) {
            BrokerPriceMark(r.broker, r.timestamp, r.ticker, r.priceMicros, r.currency, id = r.id)
        } else null
        else -> null
    }
}
