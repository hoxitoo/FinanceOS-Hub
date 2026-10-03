package com.financeos.hub.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.financeos.hub.core.database.entities.BrokerEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrokerEventDao {

    @Query("SELECT * FROM broker_events ORDER BY timestamp")
    fun observeAll(): Flow<List<BrokerEventEntity>>

    @Query("SELECT * FROM broker_events ORDER BY timestamp")
    suspend fun getAll(): List<BrokerEventEntity>

    /** IGNORE: повтор с тем же ключом — тот же пуш; вставка ничего не меняет и не падает. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<BrokerEventEntity>): List<Long>

    /**
     * Тот же текст в окне вокруг [ts] — повторная доставка одного пуша (уведомление обновили,
     * свернули и развернули). Время у неё другое, поэтому ключ строки не совпал бы.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM broker_events WHERE raw_text = :raw AND timestamp BETWEEN :from AND :to)")
    suspend fun existsSameText(raw: String, from: Long, to: Long): Boolean

    /** События, записанные от приложения [pkg] (ключ начинается с имени пакета). */
    @Query("DELETE FROM broker_events WHERE substr(id, 1, length(:pkg) + 1) = :pkg || '_'")
    suspend fun deleteFromPackage(pkg: String)

    /** Вставка с заменой — для отметок счёта: «завёл» и «удалил» пишутся в одну и ту же строку. */
    @Upsert
    suspend fun upsert(event: BrokerEventEntity)

    @Query("DELETE FROM broker_events WHERE id = :id")
    suspend fun delete(id: String)

    /** Все строки одной ручной записи (актив = пополнение + покупка) — по общему началу id. */
    @Query("DELETE FROM broker_events WHERE substr(id, 1, length(:prefix)) = :prefix")
    suspend fun deleteByPrefix(prefix: String)

    @Query("UPDATE broker_events SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: String)
}
