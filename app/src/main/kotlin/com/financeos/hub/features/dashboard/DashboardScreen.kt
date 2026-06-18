package com.financeos.hub.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {},
    vm             : DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Главная", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    Text(
                        FosFormatter.monthYear(System.currentTimeMillis()),
                        style = FosType.Label,
                        color = FosColors.TextSecondary,
                    )
                }
                Text(
                    text     = "⚙",
                    style    = FosType.SubHeader,
                    color    = FosColors.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(FosDimens.RadiusIcon))
                        .clickable { onSettingsClick() }
                        .padding(8.dp),
                )
            }
        }

        // Hero — net worth card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCard))
                    .background(FosColors.Surface)
                    .padding(FosDimens.CardPadding),
            ) {
                Column {
                    Text("Состояние", style = FosType.Label, color = FosColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    val netWorth = state.netWorthKopecks
                    Text(
                        text  = FosFormatter.amount(netWorth),
                        style = FosType.HeroAmount,
                        color = if (netWorth >= 0) FosColors.TextPrimary else FosColors.Negative,
                    )
                    Spacer(Modifier.height(FosDimens.ItemGap))
                    Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
                        MetricChip(
                            label  = "Доходы",
                            value  = FosFormatter.compact(state.incomeKopecks),
                            color  = FosColors.Positive,
                            modifier = Modifier.weight(1f),
                        )
                        MetricChip(
                            label  = "Расходы",
                            value  = FosFormatter.compact(state.expenseKopecks),
                            color  = FosColors.Negative,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Accounts row
        if (state.accounts.isNotEmpty()) {
            item {
                Text("Счета", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
                    items(state.accounts) { account ->
                        AccountChipItem(
                            name    = account.name,
                            balance = FosFormatter.compact(account.balanceKopecks),
                        )
                    }
                }
            }
        }

        // Recent transactions
        if (state.recentTransactions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(FosDimens.ItemGap))
                Text("Недавние", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            items(state.recentTransactions) { tx ->
                TransactionRow(transaction = tx, categoryName = state.categoryName(tx.categoryId))
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Surface2)
            .padding(12.dp),
    ) {
        Text(label, style = FosType.Micro, color = FosColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = FosType.SmallBold, color = color)
    }
}

@Composable
private fun AccountChipItem(name: String, balance: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(FosDimens.RadiusChip))
            .background(FosColors.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = FosType.Micro, color = FosColors.TextSecondary)
        Text(balance, style = FosType.SmallBold, color = FosColors.TextPrimary)
    }
}
