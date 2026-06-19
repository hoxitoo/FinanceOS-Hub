package com.financeos.hub.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {},
    vm             : DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    var showAddAccountSheet  by remember { mutableStateOf(false) }
    val addAccountSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedAccount      by remember { mutableStateOf<AccountEntity?>(null) }
    var editBalanceText      by remember { mutableStateOf("") }

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
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
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
                            label    = "Доходы",
                            value    = FosFormatter.compact(state.incomeKopecks),
                            color    = FosColors.Positive,
                            modifier = Modifier.weight(1f),
                        )
                        MetricChip(
                            label    = "Расходы",
                            value    = FosFormatter.compact(state.expenseKopecks),
                            color    = FosColors.Negative,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Accounts section — always shown
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Счета", style = FosType.SectionCap, color = FosColors.TextMuted)
                TextButton(
                    onClick        = { showAddAccountSheet = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("+ Добавить", style = FosType.Label, color = FosColors.Positive)
                }
            }
        }
        item {
            if (state.accounts.isEmpty()) {
                Text(
                    "Добавьте счёт чтобы отслеживать состояние",
                    style = FosType.Body,
                    color = FosColors.TextMuted,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
                    items(state.accounts, key = { it.id }) { account ->
                        AccountChipItem(
                            name    = account.name,
                            balance = FosFormatter.compact(account.balanceKopecks),
                            onClick = {
                                selectedAccount = account
                                editBalanceText = (account.balanceKopecks / 100).toString()
                            },
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
            items(state.recentTransactions, key = { it.id }) { tx ->
                TransactionRow(transaction = tx, categoryName = state.categoryName(tx.categoryId))
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showAddAccountSheet) {
        AddAccountSheet(
            sheetState = addAccountSheetState,
            onDismiss  = { showAddAccountSheet = false },
            onSave     = { name, bank, cardMask, balanceKopecks ->
                vm.createAccount(name, bank, cardMask, balanceKopecks)
            },
        )
    }

    selectedAccount?.let { account ->
        val kopecks = editBalanceText.replace(",", ".").toDoubleOrNull()
            ?.let { (it * 100).toLong() } ?: account.balanceKopecks
        AlertDialog(
            onDismissRequest = { selectedAccount = null },
            containerColor   = FosColors.Surface,
            title = {
                Text(account.name, style = FosType.BodySemi, color = FosColors.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.bank, style = FosType.Body, color = FosColors.TextSecondary)
                    OutlinedTextField(
                        value           = editBalanceText,
                        onValueChange   = { editBalanceText = it },
                        label           = { Text("Баланс, ₽", style = FosType.Label) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = FosColors.Info,
                            unfocusedBorderColor = FosColors.Border,
                            focusedLabelColor    = FosColors.Info,
                            unfocusedLabelColor  = FosColors.TextMuted,
                            cursorColor          = FosColors.Info,
                            focusedTextColor     = FosColors.TextPrimary,
                            unfocusedTextColor   = FosColors.TextPrimary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateAccountBalance(account, kopecks)
                    selectedAccount = null
                }) {
                    Text("Сохранить", color = FosColors.Positive)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        vm.deleteAccount(account.id)
                        selectedAccount = null
                    }) {
                        Text("Удалить", color = FosColors.Negative)
                    }
                    TextButton(onClick = { selectedAccount = null }) {
                        Text("Отмена", color = FosColors.TextSecondary)
                    }
                }
            },
        )
    }
}

@Composable
private fun MetricChip(
    label   : String,
    value   : String,
    color   : androidx.compose.ui.graphics.Color,
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
private fun AccountChipItem(name: String, balance: String, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(FosDimens.RadiusChip))
            .background(FosColors.Surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name,    style = FosType.Micro,      color = FosColors.TextSecondary)
        Text(balance, style = FosType.SmallBold,  color = FosColors.TextPrimary)
    }
}
