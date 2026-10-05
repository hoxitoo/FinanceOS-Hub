package com.financeos.hub.data.repositories

import androidx.room.withTransaction
import com.financeos.hub.core.database.FosDatabase
import com.financeos.hub.core.database.daos.BrokerEventDao
import com.financeos.hub.core.invest.BrokerEvent
import com.financeos.hub.core.invest.BrokerEventMapper
import com.financeos.hub.core.invest.BrokerAccountMark
import com.financeos.hub.core.invest.BrokerPushParser
import com.financeos.hub.core.invest.MANUAL_PREFIX
import com.financeos.hub.core.invest.OrderStatus
import com.financeos.hub.core.invest.contractKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * События брокера — только для режима «Инвестор» (инвариант #44). Кошелёк этот репозиторий не
 * использует нигде.
 */
@Singleton
class BrokerEventRepository @Inject constructor(
    private val db : FosDatabase,
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

    // ── Ручной ввод (инвариант #49) ──────────────────────────────────────────────

    /**
     * Записать события, введённые человеком, ОДНОЙ записью: у всех общий префикс id
     * («manual_<uuid>_0», «…_1»), поэтому и удаляются они вместе — актив, заведённый как
     * «пополнение + покупка», не оставит после удаления лишних денег на счёте.
     */
    suspend fun addManual(events: List<BrokerEvent>, now: Long = System.currentTimeMillis()) {
        if (events.isEmpty()) return
        val group = "$MANUAL_PREFIX${UUID.randomUUID()}"
        dao.insertAll(events.mapIndexed { i, e -> BrokerEventMapper.toEntity(e, "${group}_$i", "", now) })
    }

    /**
     * Правка записи (#51). Ручная — переписывается ЦЕЛИКОМ под тем же префиксом группы: у актива
     * деньги и покупка меняются вместе, и удаление потом снова уберёт их вдвоём. Пуш — строка
     * перезаписывается под тем же id и с тем же исходным текстом: по тексту ловится повторная
     * доставка, и без него тот же пуш, пришедший снова, записался бы второй раз.
     */
    suspend fun replace(id: String, events: List<BrokerEvent>, now: Long = System.currentTimeMillis()) {
        if (events.isEmpty()) return
        if (id.startsWith(MANUAL_PREFIX)) {
            val group = id.substringBeforeLast('_')
            // Одной транзакцией: иначе экран на миг увидел бы запись удалённой, а сбой посередине
            // потерял бы её совсем.
            db.withTransaction {
                dao.deleteByPrefix(group + "_")
                dao.insertAll(events.mapIndexed { i, e -> BrokerEventMapper.toEntity(e, "${group}_$i", "", now) })
            }
            return
        }
        val row = dao.getAll().firstOrNull { it.id == id } ?: return
        // Пуш — это одно событие; правка не может превратить его в два.
        dao.upsert(BrokerEventMapper.toEntity(events.first(), id, row.rawText, row.createdAt))
    }

    /** Завести счёт (или вернуть удалённый): одна строка на счёт, «завёл» и «удалил» её перезаписывают. */
    suspend fun saveAccount(broker: String, contract: String, label: String?, now: Long = System.currentTimeMillis()) =
        dao.upsert(BrokerEventMapper.toEntity(
            BrokerAccountMark(broker, now, contract.trim(), label?.trim()?.takeIf { it.isNotEmpty() }),
            accountRowId(broker, contract), "", now,
        ))

    /** «Удалить счёт»: скрыть из списка. Операции по нему остаются — их удаляют отдельно. */
    suspend fun hideAccount(broker: String, contract: String, now: Long = System.currentTimeMillis()) =
        dao.upsert(BrokerEventMapper.toEntity(
            BrokerAccountMark(broker, now, contract.trim(), null, hidden = true),
            accountRowId(broker, contract), "", now,
        ))

    /**
     * Удалить событие. Ручное — вместе со всей своей записью (пополнение и покупка актива уходят
     * вдвоём), пришедшее пушем — одной строкой.
     */
    suspend fun deleteEvent(id: String) {
        if (id.startsWith(MANUAL_PREFIX)) { dao.deleteByPrefix(id.substringBeforeLast('_') + "_"); return }
        val row = dao.getAll().firstOrNull { it.id == id } ?: return
        // Заявка из пушей — это цепочка «активна → исполнена/отменена». Удалить только последний
        // статус значило бы воскресить «активна»: на экране появилась бы заявка, которой нет.
        if (row.kind == BrokerEventMapper.ORDER) {
            // Цепочка — это строка и «активна» ПЕРЕД ней, но не дальше прошлой завершённой заявки того
            // же вида: две покупки по 20 лотов LQDT за минуту (рыночная и лимитная, #52) — это две
            // заявки, и удаление второй не должно уносить первую.
            val same = dao.getAll().filter {
                it.kind == BrokerEventMapper.ORDER && !it.id.startsWith(MANUAL_PREFIX) &&
                    it.broker == row.broker && it.ticker == row.ticker && it.side == row.side &&
                    it.lots == row.lots && it.timestamp <= row.timestamp && it.id != row.id
            }
            val prevDone = same.filter { it.status != OrderStatus.ACTIVE.name }.maxOfOrNull { it.timestamp }
            same.filter { it.status == OrderStatus.ACTIVE.name && (prevDone == null || it.timestamp > prevDone) }
                .forEach { dao.delete(it.id) }
            dao.delete(row.id)
        } else {
            dao.delete(id)
        }
    }

    /** Удалить актив целиком: все сделки и цены по бумаге (и деньги, заведённые вместе с ней). */
    suspend fun deleteTicker(broker: String, ticker: String) {
        dao.getAll()
            .filter { it.broker == broker && it.ticker.equals(ticker, ignoreCase = true) }
            .map { it.id }
            .forEach { deleteEvent(it) }
    }

    // НЕ с префиксом ручных записей: `deleteEvent` удаляет ручную запись по префиксу группы, и
    // «manual_acct_БКС_» снёс бы заодно все счета этого брокера.
    private fun accountRowId(broker: String, contract: String) = "acct_${broker}_${contractKey(contract)}"

    /** «Не то приложение»: всё, что оно успело записать, — не события брокера. */
    suspend fun forgetPackage(packageName: String) = dao.deleteFromPackage(packageName)

    private companion object {
        /** Две минуты: повтор приходит в пределах секунд, а два одинаковых перевода подряд — редкость. */
        const val DUP_WINDOW = 2 * 60_000L
    }
}
