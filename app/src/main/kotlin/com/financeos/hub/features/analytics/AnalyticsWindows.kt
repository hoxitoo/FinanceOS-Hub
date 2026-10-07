package com.financeos.hub.features.analytics

import java.time.LocalDate
import java.time.YearMonth

/**
 * Окна периода аналитики — одни для круговой диаграммы и для карточки категории (#54).
 *
 * Карточка категории раньше считала СВОЁ окно («этот месяц / прошлый месяц») при любом чипе: в строке
 * «Покупки · 64 672 ₽» за год, а внутри — пять сентябрьских операций. Цифра в строке и список под ней
 * обязаны быть об одном отрезке, поэтому окно — одна функция на оба места.
 */
object AnalyticsWindows {

    /** Первый день окна (`null` — с начала истории) и последний день включительно. */
    data class Window(val from: LocalDate?, val to: LocalDate)

    /** Окно выбранного периода: заканчивается последним днём текущего месяца. */
    fun current(period: AnalyticsPeriod, month: YearMonth): Window {
        val to = month.atEndOfMonth()
        val from = when (period) {
            AnalyticsPeriod.MONTH     -> month.atDay(1)
            AnalyticsPeriod.HALF_YEAR -> month.minusMonths(5).atDay(1)
            AnalyticsPeriod.YEAR      -> month.minusMonths(11).atDay(1)
            AnalyticsPeriod.ALL       -> null
        }
        return Window(from, to)
    }

    /**
     * Такой же по длине отрезок ПЕРЕД окном — с чем сравнивать: месяц с прошлым месяцем, полгода с
     * прошлым полугодием, год с прошлым годом. У «Всего времени» сравнивать не с чем — `null`.
     */
    fun previous(period: AnalyticsPeriod, month: YearMonth): Window? {
        val months = when (period) {
            AnalyticsPeriod.MONTH     -> 1L
            AnalyticsPeriod.HALF_YEAR -> 6L
            AnalyticsPeriod.YEAR      -> 12L
            AnalyticsPeriod.ALL       -> return null
        }
        val end = month.minusMonths(months)
        return Window(end.minusMonths(months - 1).atDay(1), end.atEndOfMonth())
    }

    /** Подписи карточки: как назвать окно и отрезок перед ним. */
    fun labels(period: AnalyticsPeriod): Pair<String, String?> = when (period) {
        AnalyticsPeriod.MONTH     -> "Этот месяц" to "Прошлый месяц"
        AnalyticsPeriod.HALF_YEAR -> "За 6 месяцев" to "6 месяцев до этого"
        AnalyticsPeriod.YEAR      -> "За год" to "Год до этого"
        AnalyticsPeriod.ALL       -> "За всё время" to null
    }

    /** «…, чем в прошлом месяце» — с чем сравнили, в нужном падеже. */
    fun versus(period: AnalyticsPeriod): String = when (period) {
        AnalyticsPeriod.MONTH     -> "в прошлом месяце"
        AnalyticsPeriod.HALF_YEAR -> "за 6 месяцев до этого"
        AnalyticsPeriod.YEAR      -> "за год до этого"
        AnalyticsPeriod.ALL       -> ""
    }
}
