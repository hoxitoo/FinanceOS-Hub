package com.financeos.hub.core.analytics

/**
 * One row of the month-over-month comparison.
 *
 * [delta] keeps the original convention — **positive = spent LESS than last month** (an improvement).
 * [prevKopecks] / [currentKopecks] carry the raw "было → стало" figures so the UI can show the actual
 * numbers instead of only a difference, which nobody could read on its own.
 */
data class WaterfallBar(
    val label         : String,
    val delta         : Long,
    val isTotal       : Boolean = false,
    val prevKopecks   : Long = 0L,
    val currentKopecks: Long = 0L,
    /**
     * True for the income row. The sign has the OPPOSITE meaning there: growing income is good,
     * growing spending is bad — without this the UI would paint an income rise as a loss.
     */
    val isIncome      : Boolean = false,
)
