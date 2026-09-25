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
    /**
     * Когда человек начал копить на эту цель.
     *
     * Отдельно от [createdAt] намеренно: запись в приложении почти всегда появляется позже самого
     * накопления («коплю с весны, завёл цель в сентябре»), и подставлять дату создания значило бы
     * молча укоротить срок. Необязательное: у новой цели заполняется днём создания, у старых
     * остаётся пустым, пока человек не укажет.
     */
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
