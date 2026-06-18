package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BudgetPeriod { WEEKLY, MONTHLY }

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["category_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "limit_kopecks") val limitKopecks: Long,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
