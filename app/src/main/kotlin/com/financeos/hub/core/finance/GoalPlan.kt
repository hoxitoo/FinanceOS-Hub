package com.financeos.hub.core.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Расчёт по одной цели: успеваю ли, сколько осталось, сколько откладывать.
 *
 * Отдельно от [SavingsMath] намеренно. Тот считает ВКЛАД: ставка, капитализация, налог,
 * индексация взноса — всё это нужно, когда деньги работают. Цель в этом приложении — копилка:
 * сколько положили, столько и лежит. Приводить сюда проценты значило бы обещать доход, которого
 * приложение не видит и проверить не может; человек сравнит расчёт с реальным остатком и решит,
 * что приложение врёт.
 *
 * Поэтому здесь только деление и календарь. Ровно один класс задач, зато каждая цифра честная.
 */
object GoalPlan {

    /**
     * @param remainingKopecks  сколько ещё не отложено (0, если цель набрана)
     * @param monthsLeft        полных месяцев до срока; `null` — срока нет
     * @param requiredMonthly   сколько откладывать в месяц, чтобы успеть; `null` — считать не из чего
     * @param monthsAtCurrentPace за сколько месяцев цель наберётся текущим темпом; `null` — темпа нет
     * @param onTrack           успевает ли текущий темп к сроку; `null` — сравнивать нечего
     * @param elapsedShare      какая доля срока прошла (0..1); `null` — неизвестно начало или срок
     */
    data class Outlook(
        val remainingKopecks   : Long,
        val monthsLeft         : Int?,
        val requiredMonthly    : Long?,
        val monthsAtCurrentPace: Int?,
        val onTrack            : Boolean?,
        val elapsedShare       : Float?,
    )

    /** Потолок расчёта: дальше цифра перестаёт быть планом и становится отпиской. */
    private const val MAX_MONTHS = 600

    /**
     * @param savedKopecks   сколько уже отложено
     * @param targetKopecks  цель
     * @param startedAt      начало накопления, epoch ms; `null` — не указано
     * @param deadlineAt     срок, epoch ms; `null` — без срока
     * @param paceKopecks    сколько человек откладывает в месяц (его собственный темп); `null`/0 — нет данных
     * @param today          «сегодня» параметром, чтобы расчёт был проверяемым
     */
    fun outlook(
        savedKopecks : Long,
        targetKopecks: Long,
        startedAt    : Long?,
        deadlineAt   : Long?,
        paceKopecks  : Long?,
        today        : LocalDate = LocalDate.now(),
    ): Outlook {
        val remaining = (targetKopecks - savedKopecks).coerceAtLeast(0L)

        val deadline = deadlineAt?.let { epochToDate(it) }
        // Месяцы считаются по КАЛЕНДАРЮ, а не делением дней на 30: «до 1 марта» — это про число в
        // календаре, и на длинном сроке деление уезжает на целый месяц.
        val monthsLeft = deadline?.let {
            ChronoUnit.MONTHS.between(today.withDayOfMonth(1), it.withDayOfMonth(1))
                .toInt()
                .coerceAtLeast(0)
        }

        // Цель набрана — планировать нечего, и «откладывайте 0 ₽» не совет.
        if (remaining == 0L) {
            return Outlook(
                remainingKopecks    = 0L,
                monthsLeft          = monthsLeft,
                requiredMonthly     = null,
                monthsAtCurrentPace = 0,
                onTrack             = true,
                elapsedShare        = elapsedShare(startedAt, deadline, today),
            )
        }

        val required = when {
            monthsLeft == null -> null
            // Срок уже сегодня или прошёл: вся недостающая сумма — «сейчас», а не «в месяц».
            // Делить на ноль месяцев нельзя, а показать 0 было бы ложью.
            monthsLeft <= 0    -> remaining
            else               -> ceil(remaining.toDouble() / monthsLeft).toLong()
        }

        val pace = paceKopecks?.takeIf { it > 0L }
        val monthsAtPace = pace?.let {
            ceil(remaining.toDouble() / it).toLong().takeIf { m -> m <= MAX_MONTHS }?.toInt()
        }

        val onTrack = when {
            pace == null || required == null -> null
            monthsLeft != null && monthsLeft <= 0 -> false
            else -> pace >= required
        }

        return Outlook(
            remainingKopecks    = remaining,
            monthsLeft          = monthsLeft,
            requiredMonthly     = required,
            monthsAtCurrentPace = monthsAtPace,
            onTrack             = onTrack,
            elapsedShare        = elapsedShare(startedAt, deadline, today),
        )
    }

    /**
     * Доля прошедшего срока — то, ради чего и нужна дата начала.
     *
     * Рядом с долей набранных денег она отвечает на вопрос, которого не видно по проценту цели:
     * «собрано 20 %» звучит одинаково и на второй месяц из двенадцати, и на одиннадцатый.
     */
    private fun elapsedShare(startedAt: Long?, deadline: LocalDate?, today: LocalDate): Float? {
        val start = startedAt?.let { epochToDate(it) } ?: return null
        if (deadline == null) return null
        val total = ChronoUnit.DAYS.between(start, deadline)
        if (total <= 0L) return null
        val gone = ChronoUnit.DAYS.between(start, today)
        return (gone.toFloat() / total).coerceIn(0f, 1f)
    }

    private fun epochToDate(epochMillis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
}
