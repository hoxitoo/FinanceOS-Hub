package com.financeos.hub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun TransactionRow(
    transaction  : TransactionEntity,
    categoryName : String,
    modifier     : Modifier = Modifier,
    onClick      : (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(FosDimens.CardPaddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Left — merchant + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = transaction.merchant ?: categoryName,
                style = FosType.TxMerchant,
                color = FosColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text  = "${FosFormatter.dayLabelYear(transaction.timestamp)} · ${transaction.description?.takeIf { it.isNotBlank() } ?: categoryName}",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right — amount.
        // CRITICAL: expenses = Negative red, income = Positive green.
        // A TRANSFER is neither income nor expense → render neutral (never red, never green).
        when (transaction.type) {
            TransactionType.TRANSFER -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text  = "↔ ${FosFormatter.amount(kotlin.math.abs(transaction.amountKopecks))}",
                        style = FosType.TxAmount,
                        color = FosColors.TextPrimary,
                    )
                    if (transaction.goalId != null) {
                        Text(
                            text  = "→ в цель",
                            style = FosType.Micro,
                            color = FosColors.TextSecondary,
                        )
                    }
                }
            }
            else -> {
                val isExpense = transaction.type == TransactionType.EXPENSE
                val amtColor  = if (isExpense) FosColors.Negative else FosColors.Positive
                Text(
                    text  = FosFormatter.signedAmount(transaction.amountKopecks),
                    style = FosType.TxAmount,
                    color = amtColor,
                )
            }
        }
    }
}
