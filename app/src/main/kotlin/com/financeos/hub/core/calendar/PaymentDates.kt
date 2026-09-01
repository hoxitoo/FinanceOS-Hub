package com.financeos.hub.core.calendar

import com.financeos.hub.core.database.entities.PaymentSchedule
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Когда обязательство наступит в следующий раз.
 *
 * Здесь одна ловушка, ради которой этот файл вообще существует отдельно.
 *
 * **Аренда 31-го числа.** Наивный `дата.plusMonths(1)` даёт 28 февраля — и на этом всё ломается
 * НАВСЕГДА: следующий шаг считается уже от 28-го, и в марте платёж остаётся 28-м, в апреле 28-м, и
 * так до конца жизни установки. Сползание молчаливое: никакой ошибки не видно, просто дата с
 * каждым коротким месяцем уезжает и больше не возвращается.
 *
 * Поэтому шаг всегда считается от ЗАДУМАННОГО числа месяца, а не от прошлой фактической даты, и
 * подрезается под длину конкретного месяца. 31 января → 28 февраля → 31 марта.
 */
object PaymentDates {

    /**
     * Все даты обязательства, попадающие в отрезок [from]..[to] включительно.
     *
     * Для [PaymentSchedule.ONCE] это либо один день, либо ни одного.
     */
    fun occurrencesIn(
        payment: PlannedPaymentEntity,
        from   : LocalDate,
        to     : LocalDate,
        zone   : ZoneId = ZoneId.systemDefault(),
    ): List<LocalDate> {
        // Обязательство не описывает время ДО своего появления. Подтвердив сегодня подписку,
        // человек не просил следить за июлем — а без этой отсечки июльские периоды всплывали как
        // «ПРОСРОЧЕНО» (долг, которого нет) и тут же закрывались июльскими покупками, из-за чего
        // «Отвязать» выглядело сломанным: снятая отметка мгновенно возвращалась на месяц раньше.
        val bornOn = Instant.ofEpochMilli(payment.createdAt).atZone(zone).toLocalDate()
        val start  = maxOf(from, bornOn)
        if (to.isBefore(start)) return emptyList()
        val anchor = Instant.ofEpochMilli(payment.anchorDate).atZone(zone).toLocalDate()

        if (payment.schedule == PaymentSchedule.ONCE) {
            return if (anchor in start..to) listOf(anchor) else emptyList()
        }

        val out = ArrayList<LocalDate>()
        var step = 0
        // Потолок по числу шагов, а не «пока не дойдём до to»: при испорченных данных (нулевой
        // период не бывает, но данные бывают всякие) цикл обязан закончиться.
        while (step <= MAX_STEPS) {
            val date = occurrence(payment, anchor, step)
            if (date.isAfter(to)) break
            if (!date.isBefore(start)) out += date
            step++
        }
        return out
    }

    /** Ближайшая дата, не раньше [notBefore]. `null` — обязательство больше не наступит. */
    fun nextOccurrence(
        payment  : PlannedPaymentEntity,
        notBefore: LocalDate,
        zone     : ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        val anchor = Instant.ofEpochMilli(payment.anchorDate).atZone(zone).toLocalDate()
        if (payment.schedule == PaymentSchedule.ONCE) {
            return anchor.takeIf { !it.isBefore(notBefore) }
        }
        for (step in 0..MAX_STEPS) {
            val date = occurrence(payment, anchor, step)
            if (!date.isBefore(notBefore)) return date
        }
        return null
    }

    /**
     * Дата на [step]-м повторении, считая от исходной.
     *
     * Считается ОТ ЯКОРЯ, а не от предыдущей даты — в этом вся суть. Прибавление по одному шагу к
     * последнему результату накапливает подрезку короткими месяцами и уводит дату навсегда.
     */
    private fun occurrence(payment: PlannedPaymentEntity, anchor: LocalDate, step: Int): LocalDate {
        val shifted = when (payment.schedule) {
            PaymentSchedule.ONCE      -> anchor
            PaymentSchedule.WEEKLY    -> return anchor.plusWeeks(step.toLong())
            PaymentSchedule.MONTHLY   -> anchor.plusMonths(step.toLong())
            PaymentSchedule.QUARTERLY -> anchor.plusMonths(step * 3L)
            PaymentSchedule.YEARLY    -> anchor.plusYears(step.toLong())
        }
        // Недельному ритму число месяца не нужно — он уже вышел выше.
        val intendedDay = payment.dayOfMonth ?: anchor.dayOfMonth
        return shifted.withDayOfMonth(intendedDay.coerceIn(1, shifted.lengthOfMonth()))
    }

    /**
     * Потолок повторений при построении. 400 недель ≈ 7,7 года, 400 месяцев ≈ 33 года — заведомо
     * больше любого разумного горизонта календаря и при этом конечно.
     */
    private const val MAX_STEPS = 400
}
