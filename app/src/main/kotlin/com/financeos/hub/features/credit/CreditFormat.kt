package com.financeos.hub.features.credit

import androidx.compose.ui.graphics.Color
import com.financeos.hub.ui.theme.FosColors

/**
 * Shared presentation helpers for credit cards — used by both the dashboard tile and the credit
 * screen so a card that reads "через 3 дня" in red on one never reads amber on the other.
 */

/** How close the payment deadline is. Drives the accent colour of the tile and the card block. */
enum class DueUrgency { UNKNOWN, CALM, SOON, CRITICAL, OVERDUE }

fun dueUrgency(daysUntilDue: Int?): DueUrgency = when {
    daysUntilDue == null -> DueUrgency.UNKNOWN   // terms not entered — say nothing rather than guess
    daysUntilDue < 0     -> DueUrgency.OVERDUE
    daysUntilDue <= 3    -> DueUrgency.CRITICAL
    daysUntilDue <= 10   -> DueUrgency.SOON
    else                 -> DueUrgency.CALM
}

/**
 * Accent colour for an urgency level.
 *
 * Negative is reserved for expenses, errors and overrun — a missed or imminent credit payment is
 * exactly an overrun, so it earns the red. A comfortable deadline is deliberately muted rather
 * than green: a credit card being "on time" is not a saving, and Positive means income/savings.
 */
fun dueUrgencyColor(urgency: DueUrgency): Color = when (urgency) {
    DueUrgency.OVERDUE, DueUrgency.CRITICAL -> FosColors.Negative
    DueUrgency.SOON                         -> FosColors.Warning
    DueUrgency.CALM                         -> FosColors.Info
    DueUrgency.UNKNOWN                      -> FosColors.TextMuted
}

/** Chip text for a deadline, or null when the card's terms were never entered. */
fun dueLabel(daysUntilDue: Int?): String? = when {
    daysUntilDue == null -> null
    daysUntilDue < 0     -> "Просрочен на ${pluralDays(-daysUntilDue)}"
    daysUntilDue == 0    -> "Платёж сегодня"
    else                 -> "Платёж через ${pluralDays(daysUntilDue)}"
}

/** «1 день» / «3 дня» / «12 дней» — Russian count agreement. */
fun pluralDays(n: Int): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11          -> "день"
        n % 10 in 2..4 && n % 100 !in 12..14  -> "дня"
        else                                  -> "дней"
    }
    return "$n $word"
}

/** «1 карта» / «3 карты» / «5 карт». */
fun pluralCards(n: Int): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11          -> "карта"
        n % 10 in 2..4 && n % 100 !in 12..14  -> "карты"
        else                                  -> "карт"
    }
    return "$n $word"
}
