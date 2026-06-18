package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.financeos.hub.core.database.entities.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE is_active = 1")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND is_active = 1 LIMIT 1")
    suspend fun getByCategory(categoryId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("UPDATE budgets SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
