package com.financeos.hub.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.features.credit.DueUrgency
import com.financeos.hub.features.credit.dueLabel
import com.financeos.hub.features.credit.dueUrgency
import com.financeos.hub.features.credit.dueUrgencyColor
import com.financeos.hub.features.credit.pluralCards
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

/**
 * The whole credit position in one row of the dashboard: free limit, debt, and how close the next
 * payment is. Everything deeper — per-card terms, the grace timeline, repayment — lives behind the
 * tap, so the home screen stays the same height whether you hold one card or five.
 *
 * The free limit is labelled «Свободный лимит», not «Доступные средства». It is the bank's money,
 * and sitting a few millimetres under your own balance it would otherwise read as cash you have.
 */
@Composable
fun CreditSummaryTile(
    credit  : CreditSummary,
    onClick : () -> Unit,
) {
    val urgency = dueUrgency(credit.daysUntilDue)
    val accent  = dueUrgencyColor(urgency)
    val label   = dueLabel(credit.daysUntilDue)
    // Only an actually pressing deadline earns a coloured border; a calm card stays neutral so the
    // dashboard doesn't cry wolf every day of the month.
    val borderColor = when (urgency) {
        DueUrgency.OVERDUE, DueUrgency.CRITICAL, DueUrgency.SOON -> accent.copy(alpha = 0.35f)
        else -> FosColors.Border
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(FosDimens.RadiusCard))
            .clickable { onClick() }
            .padding(FosDimens.CardPadding),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("Кредитные карты", style = FosType.SectionCap, color = FosColors.TextMuted)
            if (label != null) {
                Text(
                    text     = label,
                    style    = FosType.Micro,
                    color    = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(FosDimens.RadiusChip))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Свободный лимит", style = FosType.Micro, color = FosColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    // TextPrimary, never Positive: an unused credit line is not a saving.
                    // A dash when no limit was entered — "0 ₽ свободно" would read as a maxed-out
                    // card rather than as a field the user hasn't filled in.
                    text  = if (credit.limitKopecks > 0) FosFormatter.amount(credit.freeKopecks) else "—",
                    style = FosType.CardAmount,
                    color = FosColors.TextPrimary,
                )
            }
            Spacer(Modifier.width(FosDimens.CardGap))
            Column {
                Text("Долг", style = FosType.Micro, color = FosColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = FosFormatter.amount(credit.debtKopecks),
                    style = FosType.CardAmount,
                    color = if (credit.debtKopecks > 0) FosColors.Negative else FosColors.TextPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("›", style = FosType.IconAction, color = FosColors.TextMuted)
        }

        if (credit.cardCount > 1) {
            Spacer(Modifier.height(6.dp))
            Text(pluralCards(credit.cardCount), style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}
