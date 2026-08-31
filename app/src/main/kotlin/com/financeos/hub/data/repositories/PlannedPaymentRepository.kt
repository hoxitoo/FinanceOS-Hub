package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.PlannedPaymentDao
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannedPaymentRepository @Inject constructor(
    private val dao: PlannedPaymentDao,
) {
    fun observeActive(): Flow<List<PlannedPaymentEntity>> = dao.observeActive()

    suspend fun getAll(): List<PlannedPaymentEntity> = dao.getAll()

    suspend fun getById(id: String): PlannedPaymentEntity? = dao.getById(id)

    /** Какие найденные подписки уже подтверждены — чтобы не предлагать их повторно. */
    suspend fun claimedAutoSources(): Set<String> = dao.claimedAutoSources().toSet()

    suspend fun save(payment: PlannedPaymentEntity) =
        dao.upsert(payment.copy(updatedAt = System.currentTimeMillis()))

    /**
     * Мягкое удаление: строка остаётся, чтобы уже сопоставленные операции не потеряли свою историю
     * и чтобы подтверждённая подписка не всплыла обратно в предложениях.
     */
    suspend fun deactivate(id: String) = dao.deactivate(id, System.currentTimeMillis())

    /** Настоящее удаление — только по явному желанию человека. */
    suspend fun delete(id: String) = dao.delete(id)

    suspend fun markMatched(id: String, txId: String?, throughEpochMillis: Long?) =
        dao.markMatched(id, txId, throughEpochMillis, System.currentTimeMillis())

    /**
     * Отвязать найденную операцию: обязательство снова считается незакрытым, а отвергнутая операция
     * запоминается — иначе сборщик тут же вернул бы её на место.
     */
    suspend fun unmatch(id: String) = dao.unmatch(id, System.currentTimeMillis())
}
