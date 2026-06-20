package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeos.hub.core.database.entities.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE is_active = 1 AND account_id = :accountId")
    fun observeByAccount(accountId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE is_active = 1")
    fun observeAll(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity)

    @Query("UPDATE cards SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
