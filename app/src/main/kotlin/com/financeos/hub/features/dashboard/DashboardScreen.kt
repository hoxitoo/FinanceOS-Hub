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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.financeos.hub.ui.components.LineChart
import com.financeos.hub.ui.components.ScoreRing
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.bankBrand
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
                    style    = FosType.IconAction,
                    color    = FosColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(FosDimens.RadiusIcon))
                        .clickable { onSettingsClick() }
                        .padding(8.dp),
                )
            }
        }

        // Hero — variant-based (CALM / CONTRAST / MINIMAL)
        item { HeroBlock(state = state) }

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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
                    items(state.accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
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
                            unfocusedBorderColor = FosColors.BorderStrong,
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
private fun AccountCard(account: AccountEntity, onClick: () -> Unit = {}) {
    val brand = bankBrand(account.bank)
    Column(
        modifier = Modifier
            .width(248.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(brand.bg)
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(account.bank, style = FosType.BodySemi, color = brand.onBg)
            account.cardMask?.takeIf { it.isNotBlank() }?.let { mask ->
                Text("•• $mask", style = FosType.Label, color = brand.onBg.copy(alpha = 0.75f))
            }
        }

        Spacer(Modifier.weight(1f))

        Text(FosFormatter.amount(account.balanceKopecks), style = FosType.CardAmount, color = brand.onBg)
        Spacer(Modifier.height(2.dp))
        Text(account.name, style = FosType.Micro, color = brand.onBg.copy(alpha = 0.8f))
    }
}

// ── Hero variant composables ──────────────────────────────────────────────────

@Composable
private fun HeroBlock(state: DashboardState) {
    when (state.heroVariant) {
        "CONTRAST" -> ContrastHero(state)
        "MINIMAL"  -> MinimalHero(state)
        else       -> CalmHero(state)   // "CALM" + default
    }
}

/** CALM: ScoreRing + net worth + metric chips */
@Composable
private fun CalmHero(state: DashboardState) {
    val scoreColor = when {
        state.financialScore >= 70 -> FosColors.Positive
        state.financialScore >= 40 -> FosColors.Warning
        else                       -> FosColors.Negative
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
    ) {
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                ScoreRing(score = state.financialScore, modifier = Modifier.size(100.dp))
                Column {
                    Text("Финансовое здоровье", style = FosType.Micro, color = FosColors.TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("${state.financialScore} / 100", style = FosType.BodySemi, color = scoreColor)
                    Spacer(Modifier.height(12.dp))
                    Text("Состояние", style = FosType.Micro, color = FosColors.TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    val netWorth = state.netWorthKopecks
                    Text(
                        FosFormatter.amount(netWorth),
                        style = FosType.HeroAmount,
                        color = if (netWorth >= 0) FosColors.TextPrimary else FosColors.Negative,
                    )
                }
            }
            Spacer(Modifier.height(FosDimens.ItemGap))
            Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
                MetricChip("Доходы",  FosFormatter.compact(state.incomeKopecks),  FosColors.Positive, Modifier.weight(1f))
                MetricChip("Расходы", FosFormatter.compact(state.expenseKopecks), FosColors.Negative, Modifier.weight(1f))
                if (state.forecastKopecks > 0) {
                    MetricChip("Прогноз", FosFormatter.compact(state.forecastKopecks), FosColors.Warning, Modifier.weight(1f))
                }
            }
        }
    }
}

/** CONTRAST: bold income/expense side-by-side, net worth + forecast below */
@Composable
private fun ContrastHero(state: DashboardState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
    ) {
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Доходы", style = FosType.Label, color = FosColors.TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(FosFormatter.compact(state.incomeKopecks), style = FosType.HeroLarge, color = FosColors.Positive)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Расходы", style = FosType.Label, color = FosColors.TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(FosFormatter.compact(state.expenseKopecks), style = FosType.HeroLarge, color = FosColors.Negative)
                }
            }
            // Sparkline — 30-day expense trend
            if (state.sparkline.size >= 2) {
                Spacer(Modifier.height(FosDimens.ItemGap))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                        .background(FosColors.Surface2),
                ) {
                    LineChart(
                        data     = state.sparkline,
                        color    = FosColors.Negative,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(FosDimens.ItemGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FosColors.Border),
            )
            Spacer(Modifier.height(FosDimens.ItemGap))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Состояние", style = FosType.Micro, color = FosColors.TextMuted)
                    val netWorth = state.netWorthKopecks
                    Text(
                        FosFormatter.amount(netWorth),
                        style = FosType.CardAmount,
                        color = if (netWorth >= 0) FosColors.TextPrimary else FosColors.Negative,
                    )
                }
                if (state.forecastKopecks > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Прогноз", style = FosType.Micro, color = FosColors.TextMuted)
                        Text(
                            "−${FosFormatter.compact(state.forecastKopecks)}",
                            style = FosType.CardAmount,
                            color = FosColors.Warning,
                        )
                    }
                }
            }
        }
    }
}

/** MINIMAL: compact net worth + income/expense chips */
@Composable
private fun MinimalHero(state: DashboardState) {
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
                FosFormatter.amount(netWorth),
                style = FosType.HeroMinimal,
                color = if (netWorth >= 0) FosColors.TextPrimary else FosColors.Negative,
            )
            Spacer(Modifier.height(FosDimens.ItemGap))
            Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
                MetricChip("Доходы",  FosFormatter.compact(state.incomeKopecks),  FosColors.Positive, Modifier.weight(1f))
                MetricChip("Расходы", FosFormatter.compact(state.expenseKopecks), FosColors.Negative, Modifier.weight(1f))
            }
        }
    }
}
