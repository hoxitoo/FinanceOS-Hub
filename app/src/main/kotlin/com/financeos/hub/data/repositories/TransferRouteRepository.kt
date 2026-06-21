package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.TransferRouteDao
import com.financeos.hub.core.database.entities.TransferRouteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRouteRepository @Inject constructor(
    private val dao: TransferRouteDao,
) {
    fun observeAll(): Flow<List<TransferRouteEntity>> = dao.observeAll()

    fun observeByGoal(goalId: String): Flow<List<TransferRouteEntity>> = dao.observeByGoal(goalId)

    suspend fun getAllActive(): List<TransferRouteEntity> = dao.getAllActive()

    suspend fun addRoute(route: TransferRouteEntity) = dao.insert(route)

    suspend fun removeRoute(id: String) = dao.deactivate(id)
}
