package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import kotlin.math.abs

/**
 * All operations routed to a goal, newest first. Answers "where did my goal progress come from?" —
 * and, just as importantly, makes a withdrawal from a goal-linked account visible instead of the
 * progress silently dropping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalHistorySheet(
    goal       : GoalEntity,
    vm         : GoalsViewModel,
    sheetState : SheetState,
    onDismiss  : () -> Unit,
) {
    // remember(goal.id): historyFor() builds a new stateIn flow on every call, so calling it
    // straight from the composable body would spawn a fresh collector on each recomposition.
    val historyFlow = remember(goal.id) { vm.historyFor(goal.id) }
    val history by historyFlow.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = FosColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 40.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "${goal.emoji} ${goal.name}",
                    style = FosType.ScreenTitle,
                    color = FosColors.TextPrimary,
                )
                Text(
                    FosFormatter.compact(goal.savedKopecks),
                    style = FosType.BodySemi,
                    color = FosColors.Positive,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "из ${FosFormatter.compact(goal.targetKopecks)}",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = FosColors.Border)
            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) {
                Text(
                    "Пока нет операций по этой цели.\nПереводы на привязанный счёт появятся здесь автоматически.",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            } else {
                Text("ОПЕРАЦИИ", style = FosType.SectionCap, color = FosColors.TextMuted)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier            = Modifier.height(360.dp),
                ) {
                    items(history, key = { it.id }) { tx -> GoalHistoryRow(tx) }
                }
            }
        }
    }
}

@Composable
private fun GoalHistoryRow(tx: TransactionEntity) {
    // For a goal, an OUTGOING transfer is money put IN (progress up) and an incoming one is money
    // taken back OUT — so the sign shown here is the goal's perspective, not the account's.
    val intoGoal = tx.amountKopecks < 0
    val amount   = abs(tx.amountKopecks)
    val symbol   = FosFormatter.currencySymbol(tx.currency)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface2)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.merchant?.takeIf { it.isNotBlank() }
                    ?: if (tx.type == TransactionType.TRANSFER) "Перевод" else "Операция",
                style    = FosType.TxMerchant,
                color    = FosColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                FosFormatter.dayLabelYear(tx.timestamp),
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
        }
        Text(
            text  = (if (intoGoal) "+" else "−") + FosFormatter.amount(amount, symbol),
            style = FosType.TxAmount,
            color = if (intoGoal) FosColors.Positive else FosColors.Negative,
        )
    }
}
