package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransferMatchType { CARD, KEYWORD }

@Entity(tableName = "transfer_routes", indices = [Index("goal_id")])
data class TransferRouteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    @ColumnInfo(name = "match_type") val matchType: TransferMatchType,
    @ColumnInfo(name = "match_value") val matchValue: String,   // card mask "1234" (lowercased) or keyword (lowercased)
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
