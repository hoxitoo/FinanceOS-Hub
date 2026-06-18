package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.BudgetDao
import com.financeos.hub.core.database.entities.BudgetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
) {
    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()

    suspend fun upsert(budget: BudgetEntity) = dao.upsert(budget)

    suspend fun deactivate(id: String) = dao.deactivate(id)
}
