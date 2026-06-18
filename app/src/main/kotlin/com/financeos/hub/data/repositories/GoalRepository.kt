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

    suspend fun delete(id: String) = dao.delete(id)
}
