package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.GoalDao
import com.financeos.hub.core.database.entities.GoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val dao: GoalDao,
) {
    // Guards concurrent read-modify-write in contribute() — two bank pushes routed to the
    // same goal at the same moment must not both read the same savedKopecks and overwrite each other.
    private val mutex = Mutex()

    fun observeActive(): Flow<List<GoalEntity>> = dao.observeActive()

    fun observeCompleted(): Flow<List<GoalEntity>> = dao.observeCompleted()

    suspend fun upsert(goal: GoalEntity) = dao.upsert(goal)

    suspend fun updateSaved(id: String, savedKopecks: Long) = dao.updateSaved(id, savedKopecks)

    /**
     * Adds [amountKopecks] to a goal's saved balance (may be negative to undo),
     * clamping to [0, target] and updating completion state.
     * The mutex prevents a TOCTOU race when two transfers route to the same goal concurrently.
     */
    suspend fun contribute(goalId: String, amountKopecks: Long) {
        mutex.withLock {
            val g = dao.getById(goalId) ?: return
            val newSaved  = (g.savedKopecks + amountKopecks).coerceAtLeast(0L)
            val completed = newSaved >= g.targetKopecks
            dao.upsert(
                g.copy(
                    savedKopecks = newSaved.coerceAtMost(g.targetKopecks),
                    isCompleted  = completed,
                    completedAt  = if (completed && g.completedAt == null)
                                       System.currentTimeMillis() else g.completedAt,
                    updatedAt    = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun delete(id: String) = dao.delete(id)
}
