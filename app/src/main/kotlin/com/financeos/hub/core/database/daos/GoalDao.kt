package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.financeos.hub.core.database.entities.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE is_completed = 0 ORDER BY deadline_at ASC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE is_completed = 1 ORDER BY completed_at DESC")
    fun observeCompleted(): Flow<List<GoalEntity>>

    /**
     * ВСЕ цели, достигнутые в конце.
     *
     * Экрану целей нужны именно все: набранная цель не перестаёт существовать, на ней лежат
     * деньги, и их может понадобиться снять. Список, отфильтрованный по `is_completed = 0`,
     * убирал карточку с экрана в тот самый момент, когда сумма сошлась, — вместе с «±», правкой,
     * историей и удалением. Календарю и калькулятору по-прежнему нужны только незакрытые:
     * у набранной цели нечего планировать.
     */
    @Query("SELECT * FROM goals ORDER BY is_completed ASC, deadline_at ASC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: String): GoalEntity?

    /** All goals (active + completed) — used for backup export. */
    @Query("SELECT * FROM goals ORDER BY created_at ASC")
    suspend fun getAllForBackup(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("""
        UPDATE goals
        SET saved_kopecks = :savedKopecks, updated_at = :now
        WHERE id = :id
    """)
    suspend fun updateSaved(id: String, savedKopecks: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)
}
