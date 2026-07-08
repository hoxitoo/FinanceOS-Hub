package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.financeos.hub.core.database.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE is_active = 1 ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE is_active = 1 AND card_mask = :mask LIMIT 1")
    suspend fun findByCardMask(mask: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE is_active = 1 ORDER BY name")
    suspend fun getAllActive(): List<AccountEntity>

    // @Upsert (not @Insert REPLACE): REPLACE deletes the existing row and re-inserts it, which
    // CASCADE-deletes the account's cards (CardEntity FK onDelete = CASCADE). That made every
    // balance edit / manual op / delete silently wipe the account's cards. @Upsert updates the row
    // in place — no delete, no cascade.
    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET balance_kopecks = :kopecks, updated_at = :now WHERE id = :id")
    suspend fun updateBalance(id: String, kopecks: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)

    @Query("SELECT COALESCE(SUM(balance_kopecks), 0) FROM accounts WHERE is_active = 1")
    suspend fun sumAllBalances(): Long
}
