package com.financeos.hub.features.transactions

import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.ui.components.TxSource

/**
 * С какой доли источник считается доминирующим.
 *
 * Сорок процентов, а не большинство: в списке из трёх карт с долями 45/30/25 выделять две из трёх
 * незачем — «редким» должно быть меньшинство, а не почти всё. Ниже этого порога список разнороден
 * по-настоящему, и тогда цветными идут все: различать нужно каждую строку, и пестрота здесь —
 * информация, а не шум.
 */
private const val DOMINANT_SHARE = 0.40

/** Длина имени счёта, после которой оно ломает верстку строки. */
private const val NAME_LIMIT = 14

/**
 * Строит метки источников по ОТФИЛЬТРОВАННОМУ списку операций.
 *
 * Пометить каждую операцию её банком звучит как очевидная правка, но у человека с двенадцатью
 * счетами в двух банках девяносто процентов строк получат одну и ту же цветную метку. Цвет,
 * повторённый двадцать раз подряд, перестаёт быть признаком и становится фоном — ровно то, из-за
 * чего в этом списке расход не красится красным кантом (правило огранки #7: «когда красное всё,
 * не выделено ничего»).
 *
 * Поэтому заметность обратна частоте: **доминирующий источник говорит тихо, редкий — цветом**.
 * Глаз при прокрутке ловит исключения, а не повторяющийся фон, и чем однороднее история, тем
 * заметнее в ней единственная чужая карта.
 *
 * @param transactions то, что человек видит сейчас, — не вся история
 * @return ключ источника ([sourceKeyOf]) → метка
 */
internal fun buildSourceLabels(
    transactions: List<TransactionEntity>,
    accounts    : List<AccountEntity>,
    cards       : List<CardEntity>,
): Map<String, TxSource> {
    val counts = transactions.mapNotNull { sourceKeyOf(it) }
        .groupingBy { it }
        .eachCount()
    if (counts.isEmpty()) return emptyMap()

    val total    = counts.values.sum()
    val topEntry = counts.maxByOrNull { it.value }
    // Доминирующий есть, только если он действительно перевешивает. Иначе тихих нет вовсе, и все
    // метки цветные — список и правда разнородный.
    val quietKey = topEntry
        ?.takeIf { it.value.toDouble() / total >= DOMINANT_SHARE }
        ?.key

    val accountById = accounts.associateBy { it.id }
    // Маска → банк: у непривязанной операции счёта нет, но карта с такой маской может быть заведена
    // на другом счёте, и цвет банка тогда известен.
    val bankByMask = buildMap {
        cards.forEach { c -> accountById[c.accountId]?.let { put(c.cardMask, it.bank) } }
        accounts.forEach { a -> a.cardMask?.let { put(it, a.bank) } }
    }

    return counts.keys.associateWith { key ->
        val prominent = key != quietKey
        val accId     = key.removePrefix("acc:").takeIf { key.startsWith("acc:") }
        if (accId != null) {
            val acc = accountById[accId]
            TxSource(
                // Маска, если банк её печатает: четыре цифры дают постоянную ширину и уже знакомы
                // человеку по пушам. Имя — для счетов без карты.
                label     = acc?.cardMask?.let { "•• $it" } ?: acc?.name?.take(NAME_LIMIT) ?: "счёт",
                bank      = acc?.bank.orEmpty(),
                prominent = prominent,
            )
        } else {
            val mask = key.removePrefix("mask:")
            TxSource(
                label     = "•• $mask",
                bank      = bankByMask[mask].orEmpty(),
                prominent = prominent,
            )
        }
    }
}
