package com.financeos.hub.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Деньги уходят или приходят. Зарплата — такое же обязательство, только со знаком плюс. */
enum class PaymentDirection { OUT, IN }

/** Как часто повторяется. `ONCE` — разовый платёж, у него нет следующего раза. */
enum class PaymentSchedule { ONCE, WEEKLY, MONTHLY, QUARTERLY, YEARLY }

/**
 * Обязательство: деньги, которые уйдут (или придут) в известную дату.
 *
 * Это НЕ операция, а план. Операция — свершившийся факт с суммой до копейки; обязательство —
 * намерение, у которого сумма может оказаться другой, а дата съехать. Смешивать их в одной таблице
 * нельзя: тогда «Свободно» считалось бы по деньгам, которых ещё не было, а история засорилась бы
 * записями о том, чего не происходило.
 *
 * Связь между ними односторонняя: обязательство помнит, какая операция его закрыла
 * ([lastMatchedTxId]), а операция об обязательстве не знает ничего. Так удаление обязательства не
 * может испортить историю, а ошибка сопоставления откатывается одним полем.
 */
@Entity(
    tableName = "planned_payments",
    indices = [Index("is_active"), Index("anchor_date")],
)
data class PlannedPaymentEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "amount_kopecks") val amountKopecks: Long,
    val currency: String = "RUB",
    val direction: PaymentDirection = PaymentDirection.OUT,
    val schedule: PaymentSchedule = PaymentSchedule.MONTHLY,

    /** Дата первого (для `ONCE` — единственного) платежа, epoch millis. */
    @ColumnInfo(name = "anchor_date") val anchorDate: Long,

    /**
     * Задуманное число месяца для `MONTHLY`/`QUARTERLY`/`YEARLY`.
     *
     * Хранится ОТДЕЛЬНО от [anchorDate], и это не избыточность, а защита от сползания. Аренда 31-го:
     * `дата.plusMonths(1)` даёт 28 февраля и дальше НАВСЕГДА остаётся на 28-м, потому что следующий
     * шаг считается уже от неё. Держа задуманное число отдельно и подрезая его под длину каждого
     * месяца, мы в марте снова возвращаемся на 31-е.
     *
     * `null` — брать число из [anchorDate].
     */
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,

    /** С какого счёта ждём списание. `null` — с любого. Сужает сопоставление. */
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,

    /**
     * Ключ продавца из детектора подписок, если обязательство выросло из найденной подписки.
     *
     * Нужен для двух вещей: не показывать одно и то же дважды (и как найденную подписку, и как
     * объявленный платёж) и не предлагать подтвердить то, что уже подтверждено.
     */
    @ColumnInfo(name = "auto_source") val autoSource: String? = null,

    /** Операция, закрывшая последний период. Только ссылка — саму операцию не трогаем. */
    @ColumnInfo(name = "last_matched_tx_id") val lastMatchedTxId: String? = null,

    /** Докуда уже закрыто: дата периода, для которого нашлась операция. */
    @ColumnInfo(name = "matched_through") val matchedThrough: Long? = null,

    /**
     * Операция, которую человек ОТВЕРГ кнопкой «Отвязать».
     *
     * Без этой памяти отвязывание было бы бесполезной кнопкой: сборщик на следующем же проходе
     * находит ту же самую операцию (она снова свободна и по-прежнему подходит) и закрывает
     * обязательство опять. Человек нажимает «Отвязать», строка мигает и возвращается.
     *
     * Хранится одна, а не список: смысл действия — «нет, это не она», после чего искать надо
     * ДРУГУЮ. Следующая подходящая операция закроет период как обычно.
     */
    @ColumnInfo(name = "rejected_tx_id") val rejectedTxId: String? = null,

    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
