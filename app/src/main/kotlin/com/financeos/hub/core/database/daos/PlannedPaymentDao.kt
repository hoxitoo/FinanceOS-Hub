package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedPaymentDao {

    @Query("SELECT * FROM planned_payments WHERE is_active = 1 ORDER BY anchor_date")
    fun observeActive(): Flow<List<PlannedPaymentEntity>>

    @Query("SELECT * FROM planned_payments ORDER BY anchor_date")
    suspend fun getAll(): List<PlannedPaymentEntity>

    @Query("SELECT * FROM planned_payments WHERE id = :id")
    suspend fun getById(id: String): PlannedPaymentEntity?

    /** Уже подтверждённые подписки — чтобы не предлагать подтвердить то же самое второй раз. */
    @Query("SELECT auto_source FROM planned_payments WHERE auto_source IS NOT NULL")
    suspend fun claimedAutoSources(): List<String>

    /**
     * `@Upsert`, а не `@Insert(REPLACE)` — по той же причине, что и у счетов (инвариант #1):
     * REPLACE в SQLite это DELETE + INSERT, и любая связь по внешнему ключу на эту строку молча
     * рвалась бы при каждом редактировании.
     */
    @Upsert
    suspend fun upsert(payment: PlannedPaymentEntity)

    @Query("UPDATE planned_payments SET is_active = 0, updated_at = :now WHERE id = :id")
    suspend fun deactivate(id: String, now: Long)

    @Query("DELETE FROM planned_payments WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Отметить, что период закрыт найденной операцией. Отдельным запросом, а не через `upsert`
     * целиком: сопоставление работает в фоне и не должно затирать правки, которые человек мог
     * сделать в этот же момент в форме.
     */
    @Query(
        """
        UPDATE planned_payments
        SET last_matched_tx_id = :txId, matched_through = :through, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun markMatched(id: String, txId: String?, through: Long?, now: Long)
}
