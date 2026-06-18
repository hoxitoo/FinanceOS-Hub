package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.CategoryDao
import com.financeos.hub.core.database.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun getAll(): List<CategoryEntity> = dao.getAll()

    suspend fun getById(id: String): CategoryEntity? = dao.getById(id)
}
