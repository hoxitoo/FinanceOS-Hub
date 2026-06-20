package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amountKopecks: Long,        // positive magnitude
    val merchant: String?,
    val cardMask: String?,          // the SMS's own card (source for outgoing transfer)
    val balanceKopecks: Long?,      // account balance after op (if SMS contains it)
    val timestamp: Long,
    val bankId: String,
    val rawSms: String,
    val smsId: String,
    val counterpartyMask: String? = null,  // destination card if present in body
    val outgoing: Boolean = true,          // for TRANSFER: true=money leaving (to savings)
) {
    /** Signed kopecks for storage: EXPENSE negative, INCOME positive,
     *  TRANSFER negative when outgoing (balance leaves) else positive. */
    fun signedKopecks(): Long = when (type) {
        TransactionType.INCOME   ->  amountKopecks
        TransactionType.EXPENSE  -> -amountKopecks
        TransactionType.TRANSFER -> if (outgoing) -amountKopecks else amountKopecks
    }
}
