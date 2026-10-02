package com.financeos.hub.data.repositories

import com.financeos.hub.core.database.daos.BrokerEventDao
import com.financeos.hub.core.invest.BrokerEvent
import com.financeos.hub.core.invest.BrokerEventMapper
import com.financeos.hub.core.invest.BrokerPushParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * События брокера — только для режима «Инвестор» (инвариант #44). Кошелёк этот репозиторий не
 * использует нигде.
 */
@Singleton
class BrokerEventRepository @Inject constructor(
    private val dao: BrokerEventDao,
) {
    fun observeAll(): Flow<List<BrokerEvent>> =
        dao.observeAll().map { rows -> rows.mapNotNull(BrokerEventMapper::toDomain) }

    /**
     * Пуш приложения брокера → событие в базе. `true`, если это событие брокера и оно записано
     * впервые. Текст, который не разбирается (новости, акции), не сохраняется вовсе.
     */
    suspend fun ingestPush(packageName: String, body: String, postTime: Long): Boolean {
        val text  = body.replace(' ', ' ').replace(' ', ' ')
        val event = BrokerPushParser.parse(text, postTime) ?: return false
        // Повторная доставка того же пуша: свёрнутое/развёрнутое уведомление, обновлённая группа.
        if (dao.existsSameText(text, postTime - DUP_WINDOW, postTime + DUP_WINDOW)) return false
        val id = "${packageName}_${postTime}_${text.hashCode()}"
        return dao.insertAll(listOf(BrokerEventMapper.toEntity(event, id, text))).firstOrNull() != -1L
    }

    suspend fun dismissAlert(id: String) = dao.dismiss(id)

    /** «Не то приложение»: всё, что оно успело записать, — не события брокера. */
    suspend fun forgetPackage(packageName: String) = dao.deleteFromPackage(packageName)

    private companion object {
        /** Две минуты: повтор приходит в пределах секунд, а два одинаковых перевода подряд — редкость. */
        const val DUP_WINDOW = 2 * 60_000L
    }
}
