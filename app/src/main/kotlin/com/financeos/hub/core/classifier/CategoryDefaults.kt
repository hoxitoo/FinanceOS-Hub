package com.financeos.hub.core.classifier

import com.financeos.hub.core.database.entities.TransactionType

/**
 * Fallback category for transactions the [CategoryClassifier] can't match by merchant text.
 *
 * Income often arrives with no merchant (e.g. an Alfa "Зачисление" push sets merchant = null),
 * so the dictionary classifier returns null and the row showed up with no category at all.
 * For INCOME we default to "Прочие доходы" so every credit lands in a sensible income bucket;
 * EXPENSE/TRANSFER keep null (rendered as "Другое") to avoid mislabelling.
 */
object CategoryDefaults {
    const val INCOME = "cat_income"

    fun forType(type: TransactionType): String? =
        if (type == TransactionType.INCOME) INCOME else null
}
