package com.financeos.hub.features.analytics.tabs

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
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.features.analytics.AnalyticsViewModel
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import kotlin.math.abs

/**
 * Drill-down for one category: every operation of the CURRENT and the PREVIOUS month, with a
 * month-over-month headline so the two are actually comparable rather than two loose lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOpsSheet(
    categoryTitle: String,
    categoryId   : String,
    vm           : AnalyticsViewModel,
    sheetState   : SheetState,
    onDismiss    : () -> Unit,
) {
    // remember(categoryId): categoryOperations() builds a new stateIn flow per call, so calling it
    // straight from the composable body would spawn a collector on every recomposition.
    val opsFlow = remember(categoryId) { vm.categoryOperations(categoryId) }
    val ops by opsFlow.collectAsState()

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
            Text(categoryTitle, style = FosType.ScreenTitle, color = FosColors.TextPrimary)
            Spacer(Modifier.height(6.dp))

            // Headline comparison
            val diff      = ops.currentTotal - ops.previousTotal
            val spentMore = diff > 0
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Этот месяц", style = FosType.Micro, color = FosColors.TextMuted)
                    Text(
                        FosFormatter.compact(ops.currentTotal),
                        style = FosType.BodySemi,
                        color = FosColors.TextPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Прошлый месяц", style = FosType.Micro, color = FosColors.TextMuted)
                    Text(
                        FosFormatter.compact(ops.previousTotal),
                        style = FosType.BodySemi,
                        color = FosColors.TextSecondary,
                    )
                }
            }
            if (ops.previousTotal > 0 || ops.currentTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        diff == 0L -> "Столько же, сколько в прошлом месяце"
                        spentMore  -> "На ${FosFormatter.compact(diff)} больше, чем в прошлом месяце"
                        else       -> "На ${FosFormatter.compact(abs(diff))} меньше, чем в прошлом месяце"
                    },
                    style = FosType.Micro,
                    color = when {
                        diff == 0L -> FosColors.TextMuted
                        spentMore  -> FosColors.Negative
                        else       -> FosColors.Positive
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = FosColors.Border)
            Spacer(Modifier.height(10.dp))

            if (ops.current.isEmpty() && ops.previous.isEmpty()) {
                Text(
                    "Нет операций в этой категории за два месяца.",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier            = Modifier.height(380.dp),
                ) {
                    if (ops.current.isNotEmpty()) {
                        item {
                            Text("ЭТОТ МЕСЯЦ", style = FosType.SectionCap, color = FosColors.TextMuted)
                        }
                        items(ops.current, key = { "c_${it.id}" }) { OpRow(it) }
                    }
                    if (ops.previous.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("ПРОШЛЫЙ МЕСЯЦ", style = FosType.SectionCap, color = FosColors.TextMuted)
                        }
                        items(ops.previous, key = { "p_${it.id}" }) { OpRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpRow(tx: TransactionEntity) {
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
                tx.merchant?.takeIf { it.isNotBlank() } ?: "Операция",
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
            FosFormatter.amount(abs(tx.amountKopecks), FosFormatter.currencySymbol(tx.currency)),
            style = FosType.TxAmount,
            color = FosColors.Negative,
        )
    }
}
