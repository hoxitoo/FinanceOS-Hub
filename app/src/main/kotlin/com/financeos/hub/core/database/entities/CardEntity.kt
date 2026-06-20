package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["account_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("account_id")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "card_mask") val cardMask: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
