package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.GoalDao
import com.financeos.hub.core.database.entities.GoalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val dao: GoalDao,
) {
    fun observeActive(): Flow<List<GoalEntity>> = dao.observeActive()

    fun observeCompleted(): Flow<List<GoalEntity>> = dao.observeCompleted()

    suspend fun upsert(goal: GoalEntity) = dao.upsert(goal)

    suspend fun updateSaved(id: String, savedKopecks: Long) = dao.updateSaved(id, savedKopecks)

    /**
     * Adds [amountKopecks] to a goal's saved balance (may be negative to undo),
     * clamping to [0, target] and updating completion state.
     */
    suspend fun contribute(goalId: String, amountKopecks: Long) {
        val g = dao.getById(goalId) ?: return
        val newSaved = (g.savedKopecks + amountKopecks).coerceAtLeast(0L)
        val completed = newSaved >= g.targetKopecks
        dao.upsert(
            g.copy(
                savedKopecks = newSaved.coerceAtMost(g.targetKopecks),
                isCompleted  = completed,
                completedAt  = if (completed) System.currentTimeMillis() else null,
                updatedAt    = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)
}
