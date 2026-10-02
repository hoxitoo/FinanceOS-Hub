package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Событие брокера из пуша — пополнение, перевод между счетами, заявка, предупреждение.
 *
 * Своя таблица, а НЕ `transactions` (инвариант #44): кошелёк — аналитика, бюджет, оценка, «За всё
 * время», подписки, календарь — физически не читает эту таблицу, и ни в одном его запросе не нужен
 * фильтр «кроме брокера», который однажды забыли бы.
 *
 * Одна плоская строка на все виды событий: какие колонки заполнены, решает [kind]. Перевод в модель
 * и обратно — `core/invest/BrokerEventMapper`. Тип хранится строкой, а не enum с конвертером: новый
 * вид события (вывод, купон) — новая строка значения, а не правка схемы.
 */
@Entity(
    tableName = "broker_events",
    indices = [Index("timestamp")],
)
data class BrokerEventEntity(
    /** Ключ дедупа: пакет приложения + время пуша + хеш текста. */
    @PrimaryKey val id: String,
    val broker: String,
    /** CASH | TRANSFER | ORDER | ALERT. */
    val kind: String,
    val timestamp: Long,
    /** Деньги события, копейки: пополнение со знаком, сумма перевода, требование предупреждения. */
    @ColumnInfo(name = "amount_kopecks") val amountKopecks: Long?,
    val currency: String,
    /** Счёт события; у перевода — счёт-источник. */
    val contract: String?,
    @ColumnInfo(name = "contract_label") val contractLabel: String?,
    /** У перевода между счетами — счёт-получатель. */
    @ColumnInfo(name = "to_contract") val toContract: String?,
    @ColumnInfo(name = "to_contract_label") val toContractLabel: String?,
    // ── Заявка ──
    val ticker: String?,
    /** BUY | SELL. */
    val side: String?,
    val lots: Long?,
    @ColumnInfo(name = "price_micros") val priceMicros: Long?,
    /** ACTIVE | CANCELLED | FILLED. */
    val status: String?,
    @ColumnInfo(name = "order_kind") val orderKind: String?,
    /** Предупреждение закрыто человеком («Закрыть» на карточке). */
    val dismissed: Boolean,
    /** Исходный текст пуша — для дедупа повторной доставки и для диагностики. */
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
