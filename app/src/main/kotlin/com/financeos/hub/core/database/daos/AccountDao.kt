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

    /**
     * Own money only. CREDIT accounts hold the BANK's money (a negative debt), so summing every
     * account would let a credit line move net worth — the spend on a credit card would read as
     * your balance falling, and a big repayment as it rising. INVESTMENT is excluded too: it is
     * not cash you can reach today, and the cushion pillar of the health score treats this as
     * spendable reserve.
     */
    @Query("SELECT COALESCE(SUM(balance_kopecks), 0) FROM accounts WHERE is_active = 1 AND kind = 'CASH'")
    suspend fun sumCashBalances(): Long

    /** Total outstanding credit-card debt as a POSITIVE number (0 when nothing is owed). */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN balance_kopecks < 0 THEN -balance_kopecks ELSE 0 END), 0)
        FROM accounts WHERE is_active = 1 AND kind = 'CREDIT'
    """)
    suspend fun sumCreditDebt(): Long
}
