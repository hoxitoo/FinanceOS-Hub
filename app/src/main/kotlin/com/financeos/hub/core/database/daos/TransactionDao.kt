package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.financeos.hub.core.database.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
          AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp DESC
    """)
    fun observeByPeriod(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
          AND account_id = :accountId
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun observeByAccount(accountId: String, limit: Int = 50): Flow<List<TransactionEntity>>

    @Query("SELECT sms_id FROM transactions WHERE sms_id IS NOT NULL")
    suspend fun getAllSmsHashes(): List<String>

    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE sms_id = :smsId")
    suspend fun existsBySmsId(smsId: String): Boolean

    /**
     * Cross-channel dedup: returns true if an SMS or PUSH transaction with the same absolute
     * amount already exists within a ±5-minute window. Used to prevent the same bank event
     * from being inserted twice when it arrives via both channels (e.g. Sberbank sends both
     * an SMS and a push notification for every operation).
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM transactions
        WHERE is_deleted = 0
          AND ABS(amount_kopecks) = :magnitude
          AND timestamp BETWEEN :fromTs AND :toTs
          AND source IN ('SMS', 'PUSH')
    """)
    suspend fun existsSimilarSmsOrPush(magnitude: Long, fromTs: Long, toTs: Long): Boolean

    @Query("""
        SELECT category_id, SUM(ABS(amount_kopecks)) as total
        FROM transactions
        WHERE is_deleted = 0
          AND type = 'EXPENSE'
          AND timestamp BETWEEN :from AND :to
        GROUP BY category_id
        ORDER BY total DESC
    """)
    fun getExpensesByCategory(from: Long, to: Long): Flow<List<CategorySum>>

    @Query("SELECT * FROM transactions WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): TransactionEntity?

    /** All non-deleted transactions — used for backup export. */
    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY timestamp ASC")
    suspend fun getAllForBackup(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("""
        UPDATE transactions
        SET is_deleted = 1, deleted_at = :now, updated_at = :now
        WHERE id = :id
    """)
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
          AND type = 'TRANSFER'
          AND transfer_pair_id IS NULL
          AND goal_id IS NULL
          AND id != :selfId
          AND ABS(amount_kopecks) = :magnitude
          AND ( (:outgoing = 1 AND amount_kopecks > 0) OR (:outgoing = 0 AND amount_kopecks < 0) )
          AND timestamp BETWEEN :fromTs AND :toTs
        ORDER BY ABS(timestamp - :centerTs) ASC
        LIMIT 1
    """)
    suspend fun findTransferCounterpart(
        selfId: String,
        magnitude: Long,
        outgoing: Int,
        fromTs: Long,
        toTs: Long,
        centerTs: Long,
    ): TransactionEntity?

    @Query("UPDATE transactions SET type = 'TRANSFER', transfer_pair_id = :pairId, category_id = NULL, updated_at = :now WHERE id = :id")
    suspend fun markAsPairedTransfer(id: String, pairId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET goal_id = :goalId, updated_at = :now WHERE id = :id")
    suspend fun setGoal(id: String, goalId: String?, now: Long = System.currentTimeMillis())

    /**
     * Retroactively attaches orphan (unlinked) SMS/PUSH transactions for a card [mask] to
     * [accountId]. Used when the user registers a card AFTER its transactions were already
     * ingested (the bank may report a balance for a card the app could not yet resolve to an
     * account — especially when one bank has several accounts). Only touches rows still
     * unlinked, so an already-correctly-attributed transaction is never stolen. Returns the
     * number of rows linked.
     */
    @Query("""
        UPDATE transactions
        SET account_id = :accountId, updated_at = :now
        WHERE account_id IS NULL
          AND is_deleted = 0
          AND source_mask = :mask
          AND source IN ('SMS', 'PUSH')
    """)
    suspend fun linkOrphansToAccount(accountId: String, mask: String, now: Long = System.currentTimeMillis()): Int

    /**
     * The most recent bank-authoritative post-op balance across ALL of [accountId]'s cards.
     * Used to reconcile the account to the true balance after re-linking orphans. Both cards of
     * a multi-card account draw on the same balance, so the latest "Остаток" for any of them is
     * the current account balance.
     */
    @Query("""
        SELECT balance_kopecks FROM transactions
        WHERE account_id = :accountId
          AND is_deleted = 0
          AND balance_kopecks IS NOT NULL
          AND source IN ('SMS', 'PUSH')
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun latestBalanceForAccount(accountId: String): Long?

    @Query("""
        SELECT SUM(ABS(amount_kopecks)) FROM transactions
        WHERE is_deleted = 0
          AND timestamp BETWEEN :from AND :to
          AND type = 'EXPENSE'
    """)
    suspend fun sumExpenses(from: Long, to: Long): Long?

    @Query("""
        SELECT SUM(amount_kopecks) FROM transactions
        WHERE is_deleted = 0
          AND timestamp BETWEEN :from AND :to
          AND type = 'INCOME'
    """)
    suspend fun sumIncome(from: Long, to: Long): Long?

    @Query("""
        SELECT COALESCE(SUM(ABS(amount_kopecks)), 0) FROM transactions
        WHERE is_deleted = 0
          AND type = 'EXPENSE'
          AND timestamp >= :todayStart
    """)
    suspend fun getTodayExpenses(todayStart: Long): Long

    data class CategorySum(val category_id: String?, val total: Long)
}
