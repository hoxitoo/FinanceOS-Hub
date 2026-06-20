package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeos.hub.core.database.entities.TransferRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRouteDao {
    @Query("SELECT * FROM transfer_routes WHERE is_active = 1")
    fun observeAll(): Flow<List<TransferRouteEntity>>

    @Query("SELECT * FROM transfer_routes WHERE is_active = 1 AND goal_id = :goalId")
    fun observeByGoal(goalId: String): Flow<List<TransferRouteEntity>>

    @Query("SELECT * FROM transfer_routes WHERE is_active = 1")
    suspend fun getAllActive(): List<TransferRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: TransferRouteEntity)

    @Query("UPDATE transfer_routes SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
