package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { EXPENSE, INCOME, TRANSFER }
enum class TransactionSource { SMS, MANUAL, PUSH, PDF }

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns  = ["account_id"],
            onDelete      = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["category_id"],
            onDelete      = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("account_id"),
        Index("category_id"),
        Index("timestamp"),
        Index("sms_id", unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val type: TransactionType,
    val source: TransactionSource,
    @ColumnInfo(name = "amount_kopecks") val amountKopecks: Long,   // negative = expense
    val merchant: String?,
    val description: String?,
    val timestamp: Long,
    @ColumnInfo(name = "sms_id") val smsId: String?,                // dedup key
    @ColumnInfo(name = "goal_id") val goalId: String? = null,
    @ColumnInfo(name = "transfer_pair_id") val transferPairId: String? = null,
    // Account/card last-4 the money LEFT (source) and ARRIVED at (counterparty), when the
    // bank SMS/push exposes them. Null when the message omits the number → shown as "неизвестно".
    @ColumnInfo(name = "source_mask") val sourceMask: String? = null,
    @ColumnInfo(name = "counterparty_mask") val counterpartyMask: String? = null,
    // Bank-reported post-operation balance ("Остаток"/"Доступно") for the source card, when the
    // SMS/push exposes it. Null when omitted. Used to reconcile an account to the authoritative
    // balance when the card is linked after the transaction was already ingested.
    @ColumnInfo(name = "balance_kopecks") val balanceKopecks: Long? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
