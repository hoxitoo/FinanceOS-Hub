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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.CardEntity
import com.financeos.hub.ui.components.LineChart
import com.financeos.hub.ui.components.ScoreRing
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.bankBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {},
    vm             : DashboardViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    var showAddAccountSheet  by remember { mutableStateOf(false) }
    val addAccountSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedBank         by remember { mutableStateOf<String?>(null) }
    val bankSheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

        // Accounts section — grouped by bank
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
                val byBank = state.accounts.groupBy { it.bank }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
                    items(byBank.entries.toList(), key = { it.key }) { (bank, accounts) ->
                        val bankCards = state.cards.filter { c -> accounts.any { a -> a.id == c.accountId } }
                        BankCard(
                            bank     = bank,
                            accounts = accounts,
                            cards    = bankCards,
                            onClick  = { selectedBank = bank },
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
            onSave     = { name, bank, cardMask, balanceKopecks, currency ->
                vm.createAccount(name, bank, cardMask, balanceKopecks, currency)
            },
        )
    }

    selectedBank?.let { bank ->
        val bankAccounts = state.accounts.filter { it.bank == bank }
        val bankCards    = state.cards.filter { c -> bankAccounts.any { a -> a.id == c.accountId } }
        AccountDetailSheet(
            bank         = bank,
            accounts     = bankAccounts,
            cards        = bankCards,
            sheetState   = bankSheetState,
            onDismiss    = { selectedBank = null },
            onAddAccount = { name, b, mask, kopecks, currency ->
                vm.createAccount(name, b, mask, kopecks, currency)
            },
            onAddCard    = { card -> vm.addCard(card) },
            onEditBalance = { account, newKopecks ->
                vm.updateAccountBalance(account, newKopecks)
            },
            onDelete     = { id -> vm.deleteAccount(id) },
        )
    }
}

@Composable
private fun BankCard(
    bank     : String,
    accounts : List<AccountEntity>,
    cards    : List<CardEntity>,
    onClick  : () -> Unit,
) {
    val brand    = bankBrand(bank)
    val allMasks = (accounts.mapNotNull { it.cardMask } + cards.map { it.cardMask }).distinct()
    val totals   = accounts.groupBy { it.currency }
        .mapValues { (_, list) -> list.sumOf { it.balanceKopecks } }

    Column(modifier = Modifier.width(264.dp)) {
        // Peek row of card chips
        if (allMasks.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding        = PaddingValues(start = 8.dp, bottom = 4.dp),
            ) {
                items(allMasks) { mask ->
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FosColors.Surface2)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("•• $mask", style = FosType.Micro, color = FosColors.TextSecondary)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(32.dp))
        }
        // Main bank card — volumetric with gradient depth + symbol badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(FosDimens.RadiusCard))
                .background(brand.bg)
                .clickable { onClick() },
        ) {
            // Diagonal depth gradient: top-left light, bottom-right dark
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.13f),
                                0.45f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.22f),
                            ),
                            start = Offset(0f, 0f),
                            end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        )
                    )
            )
            // Top-edge gloss highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.28f)),
            )
            // Card content
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(bank, style = FosType.BodySemi, color = brand.onBg)
                    BankSymbolBadge(bank = bank, onBg = brand.onBg)
                }
                Spacer(Modifier.weight(1f))
                totals.entries.take(3).forEach { (currency, kopecks) ->
                    Text(
                        FosFormatter.amount(kopecks, FosFormatter.currencySymbol(currency)),
                        style = FosType.CardAmount,
                        color = brand.onBg,
                    )
                }
                if (accounts.size > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${accounts.size} счёт${accountSuffix(accounts.size)}",
                        style = FosType.Micro,
                        color = brand.onBg.copy(alpha = 0.70f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BankSymbolBadge(bank: String, onBg: Color) {
    val b      = bank.lowercase()
    val symbol = when {
        "альфа" in b || "alfa" in b || "alpha" in b                          -> "А"
        "сбер"  in b || "sber" in b                                          -> "С"
        "т-банк" in b || "тинь" in b || "tbank" in b || "tinkoff" in b      -> "Т"
        "втб"   in b || "vtb"  in b                                          -> "В"
        "газпром" in b || "гпб" in b                                         -> "Г"
        "мбанк" in b || "mbank" in b || "кыргыз" in b || "kicb" in b        -> "М"
        "мтс"   in b                                                         -> "М"
        "почта" in b || "posta" in b                                         -> "П"
        "россельхоз" in b || "рсхб" in b                                     -> "Р"
        "росбанк" in b || "rosbank" in b                                     -> "Р"
        "открыт" in b || "otkritie" in b                                     -> "О"
        "райф"  in b || "raiff" in b                                         -> "Р"
        else -> bank.firstOrNull()?.uppercase() ?: "?"
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = FosType.BadgeSymbol, color = onBg)
    }
}

private fun accountSuffix(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> ""
    n % 10 in 2..4 && n % 100 !in 12..14 -> "а"
    else -> "ов"
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

// ── Hero variant composables ──────────────────────────────────────────────────

@Composable
private fun HeroBlock(state: DashboardState) {
    when (state.heroVariant) {
        "CONTRAST" -> ContrastHero(state)
        "MINIMAL"  -> MinimalHero(state)
        else       -> CalmHero(state)
    }
}

/** CALM: compact score row → full-width net worth → metric chips */
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
            // Score row — ring is compact (72dp) so text fits alongside
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                ScoreRing(score = state.financialScore, modifier = Modifier.size(72.dp))
                Column {
                    Text("Финансовое здоровье", style = FosType.Micro, color = FosColors.TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text("${state.financialScore} / 100", style = FosType.BodySemi, color = scoreColor)
                }
            }
            Spacer(Modifier.height(14.dp))
            // Net worth — FULL width so multi-currency never wraps
            Text("Состояние", style = FosType.Micro, color = FosColors.TextSecondary)
            Spacer(Modifier.height(2.dp))
            val byCur = state.netWorthByCurrency
            if (byCur.size <= 1) {
                val nw = state.netWorthKopecks
                Text(
                    FosFormatter.amount(nw),
                    style = FosType.HeroAmount,
                    color = if (nw >= 0) FosColors.TextPrimary else FosColors.Negative,
                )
            } else {
                val amtStyle = if (byCur.size >= 3) FosType.HeroAmountMulti else FosType.HeroAmount
                byCur.entries.take(3).forEach { (cur, kopecks) ->
                    Text(
                        FosFormatter.amount(kopecks, FosFormatter.currencySymbol(cur)),
                        style = amtStyle,
                        color = if (kopecks >= 0) FosColors.TextPrimary else FosColors.Negative,
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
                    val byCur = state.netWorthByCurrency
                    if (byCur.size <= 1) {
                        val netWorth = state.netWorthKopecks
                        Text(
                            FosFormatter.amount(netWorth),
                            style = FosType.CardAmount,
                            color = if (netWorth >= 0) FosColors.TextPrimary else FosColors.Negative,
                        )
                    } else {
                        val amtStyle = if (byCur.size >= 3) FosType.HeroAmountMulti else FosType.CardAmount
                        byCur.entries.take(3).forEach { (cur, kopecks) ->
                            Text(
                                FosFormatter.amount(kopecks, FosFormatter.currencySymbol(cur)),
                                style = amtStyle,
                                color = if (kopecks >= 0) FosColors.TextPrimary else FosColors.Negative,
                            )
                        }
                    }
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
            val byCur = state.netWorthByCurrency
            if (byCur.size <= 1) {
                val netWorth = state.netWorthKopecks
                Text(
                    FosFormatter.amount(netWorth),
                    style = FosType.HeroMinimal,
                    color = if (netWorth >= 0) FosColors.TextPrimary else FosColors.Negative,
                )
            } else {
                // HeroMinimal (42sp) would overflow with 3 currencies — use compact multi style
                val amtStyle = if (byCur.size >= 3) FosType.HeroAmountMulti else FosType.HeroMinimal
                byCur.entries.take(3).forEach { (cur, kopecks) ->
                    Text(
                        FosFormatter.amount(kopecks, FosFormatter.currencySymbol(cur)),
                        style = amtStyle,
                        color = if (kopecks >= 0) FosColors.TextPrimary else FosColors.Negative,
                    )
                }
            }
            Spacer(Modifier.height(FosDimens.ItemGap))
            Row(horizontalArrangement = Arrangement.spacedBy(FosDimens.CardGap)) {
                MetricChip("Доходы",  FosFormatter.compact(state.incomeKopecks),  FosColors.Positive, Modifier.weight(1f))
                MetricChip("Расходы", FosFormatter.compact(state.expenseKopecks), FosColors.Negative, Modifier.weight(1f))
            }
        }
    }
}
