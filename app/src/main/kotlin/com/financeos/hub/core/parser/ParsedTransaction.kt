package com.financeos.hub.core.parser

import com.financeos.hub.core.database.entities.TransactionType

data class ParsedTransaction(
    val type: TransactionType,
    val amountKopecks: Long,        // positive value; sign determined by type
    val merchant: String?,
    val cardMask: String?,          // last 4 digits
    val balanceKopecks: Long?,      // account balance after op (if SMS contains it)
    val timestamp: Long,
    val bankId: String,
    val rawSms: String,
    val smsId: String,
)
