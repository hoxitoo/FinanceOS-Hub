package com.financeos.hub.ui.components

import androidx.compose.foundation.background
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface)
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
                text  = "${FosFormatter.dayLabel(transaction.timestamp)} · $categoryName",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right — amount; CRITICAL: expenses = Negative red
        val isExpense = transaction.type == TransactionType.EXPENSE
        val amtColor  = if (isExpense) FosColors.Negative else FosColors.Positive
        Text(
            text  = FosFormatter.signedAmount(transaction.amountKopecks),
            style = FosType.TxAmount,
            color = amtColor,
        )
    }
}
