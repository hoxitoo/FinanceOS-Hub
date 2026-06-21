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

    @Query("""
        SELECT SUM(amount_kopecks) FROM transactions
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
