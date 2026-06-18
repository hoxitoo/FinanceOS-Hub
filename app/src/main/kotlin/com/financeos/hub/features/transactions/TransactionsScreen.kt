package com.financeos.hub.features.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun TransactionsScreen(vm: TransactionsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FosDimens.ScreenPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Операции", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
        }

        // Filter chips
        LazyRow(
            contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(FosDimens.ItemGap),
        ) {
            items(TxFilter.entries) { filter ->
                FilterChip(
                    selected = state.activeFilter == filter,
                    onClick  = { vm.setFilter(filter) },
                    label    = { Text(filter.label, style = FosType.Label) },
                    shape    = RoundedCornerShape(FosDimens.RadiusChip),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor  = FosColors.Positive.copy(alpha = 0.15f),
                        selectedLabelColor      = FosColors.Positive,
                        containerColor          = FosColors.Surface,
                        labelColor              = FosColors.TextSecondary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(FosDimens.ItemGap))

        // Grouped transaction list
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.grouped.forEach { (day, txList) ->
                item {
                    Text(
                        text     = FosFormatter.dayLabel(day),
                        style    = FosType.SectionCap,
                        color    = FosColors.TextMuted,
                        modifier = Modifier.padding(top = FosDimens.ItemGap, bottom = 4.dp),
                    )
                }
                items(txList) { tx ->
                    TransactionRow(
                        transaction  = tx,
                        categoryName = state.categoryName(tx.categoryId),
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
