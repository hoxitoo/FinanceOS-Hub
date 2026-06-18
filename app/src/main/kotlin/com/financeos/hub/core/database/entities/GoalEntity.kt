package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    @ColumnInfo(name = "target_kopecks") val targetKopecks: Long,
    @ColumnInfo(name = "saved_kopecks") val savedKopecks: Long = 0L,
    @ColumnInfo(name = "deadline_at") val deadlineAt: Long?,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
