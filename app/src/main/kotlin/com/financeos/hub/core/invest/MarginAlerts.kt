package com.financeos.hub.core.invest

/**
 * Предупреждения брокера о низком балансе («маржин-колл») — какие из них ещё требуют действия.
 *
 * Брокер присылает требование и замолкает: сообщения «вы пополнили, всё хорошо» не бывает. Поэтому
 * требование закрывается тем же способом, что и напоминание банка о платеже по кредитке (инвариант
 * #38): деньгами, которые пришли на ЭТОТ счёт ПОСЛЕ предупреждения, — пополнением или переводом с
 * другого счёта того же брокера. Брокер считал свою цифру, уже зная обо всём, что было раньше, и
 * засчитать более ранний перевод значило бы закрыть требование тем, что его не закрывает.
 *
 * Ошибки несимметричны: не заметить покрытие — карточка повисит лишнее; объявить покрытым, когда
 * это не так, — человек не пополнит счёт, и брокер закроет позиции. При сомнении требование
 * остаётся открытым, а закрыть его вручную человек может всегда.
 */
object MarginAlerts {

    data class State(
        val alert      : BrokerMarginAlert,
        /** Сколько пришло на счёт после предупреждения, копейки. */
        val coveredKopecks: Long,
        /** Более позднее предупреждение по тому же счёту заменило это. */
        val superseded : Boolean,
    ) {
        val remainingKopecks: Long get() = (alert.requiredKopecks - coveredKopecks).coerceAtLeast(0L)
        val isCovered: Boolean get() = coveredKopecks >= alert.requiredKopecks
        /** Требует действия: не покрыто, не закрыто вручную и не заменено новым. */
        val isOpen: Boolean get() = !isCovered && !alert.dismissed && !superseded
    }

    fun evaluate(events: List<BrokerEvent>): List<State> {
        val alerts = events.filterIsInstance<BrokerMarginAlert>().sortedBy { it.timestamp }
        if (alerts.isEmpty()) return emptyList()
        // Повтор того же требования (тот же счёт и та же сумма) — одна карточка, а не две.
        // Момент — ПЕРВОЙ доставки: перевод между двумя повторами тоже покрывает требование.
        val distinct = alerts.fold(mutableListOf<BrokerMarginAlert>()) { acc, a ->
            val prev = acc.lastOrNull { contractKey(it.contract) == contractKey(a.contract) }
            val repeat = prev != null && prev.requiredKopecks == a.requiredKopecks && !hasIncomingBetween(
                events, a.contract, a.currency, prev.timestamp, a.timestamp,
            )
            // Повтор ЗАКРЫТОГО вручную требования — снова карточка: брокер повторяет, значит, не
            // оплачено, а при сомнении требование остаётся открытым.
            if (repeat && prev != null && !prev.dismissed) {
                acc[acc.lastIndexOf(prev)] = prev.copy(dismissed = a.dismissed)
            } else {
                acc += a
            }
            acc
        }
        return distinct.map { a ->
            val newer = distinct.any { it !== a && contractKey(it.contract) == contractKey(a.contract) && it.timestamp > a.timestamp }
            State(
                alert          = a,
                coveredKopecks = incomingAfter(events, a.contract, a.currency, a.timestamp),
                superseded     = newer,
            )
        }.sortedByDescending { it.alert.timestamp }
    }

    /** Деньги, пришедшие на [contract] не раньше [since]: пополнения и переводы с других счетов. */
    internal fun incomingAfter(events: List<BrokerEvent>, contract: String, currency: String, since: Long): Long {
        val key = contractKey(contract)
        return events.sumOf { e ->
            when {
                e.timestamp < since -> 0L
                e is BrokerCashMove && e.amountKopecks > 0 && e.currency == currency &&
                    contractKey(e.contract) == key -> e.amountKopecks
                e is BrokerInternalTransfer && e.currency == currency &&
                    contractKey(e.toContract) == key -> e.amountKopecks
                else -> 0L
            }
        }
    }

    private fun hasIncomingBetween(events: List<BrokerEvent>, contract: String, currency: String, from: Long, to: Long): Boolean =
        incomingAfter(events.filter { it.timestamp < to }, contract, currency, from) > 0L
}
