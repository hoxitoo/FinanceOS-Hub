package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["category_id"],
            onDelete      = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("category_id")],
)
data class MerchantRuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,            // lowercase keyword to match in merchant/description
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val priority: Int = 0,          // higher = matched first
    @ColumnInfo(name = "is_regex") val isRegex: Boolean = false,
)
