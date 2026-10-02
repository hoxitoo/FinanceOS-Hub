package com.financeos.hub.features.investor.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.invest.BrokerEvent
import com.financeos.hub.core.invest.BrokerOrder
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.core.invest.contractKey
import com.financeos.hub.features.investor.ConfirmDelete
import com.financeos.hub.features.investor.FeedRow
import com.financeos.hub.features.investor.InvestAdd
import com.financeos.hub.features.investor.InvestManualOverlays
import com.financeos.hub.features.investor.InvestorViewModel
import com.financeos.hub.features.investor.OrderRow
import com.financeos.hub.features.investor.historyFeed
import com.financeos.hub.features.investor.incomingTo
import com.financeos.hub.features.investor.outgoingFrom
import com.financeos.hub.features.investor.pnlColor
import com.financeos.hub.features.investor.rememberInvestManualState
import com.financeos.hub.features.investor.signedPercent
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard

/*
 * Вкладки режима «Инвестор» (#50): Операции · Аналитика · [Портфель] · Календарь · Счета.
 * «Портфель» — главная в режиме инвестора (DashboardScreen). Здесь — только СВОИ данные: пример
 * живёт на «Портфеле» и сюда не попадает, потому что ни правка, ни аналитика к нему неприменимы.
 * Всё считается тем же Portfolio.compute, что и «Портфель», — две вкладки не могут разойтись.
 */

// ── Общий каркас ─────────────────────────────────────────────────────────────

@Composable
private fun InvestTab(
    title  : String,
    action : Pair<String, () -> Unit>? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.InvestBackground)
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
    ) {
        item(key = "top") { Spacer(Modifier.height(16.dp)) }
        item(key = "title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = FosType.ScreenTitle, color = FosColors.TextPrimary, modifier = Modifier.weight(1f))
                action?.let { (label, onClick) ->
                    Text(
                        label,
                        style    = FosType.Label,
                        color    = FosColors.Invest,
                        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
                    )
                }
            }
        }
        content()
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

/** Пустая вкладка — честно и с выходом: что сделать, чтобы здесь появились данные. */
private fun LazyListScope.emptyCard(key: String, title: String, text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    item(key = key) {
        Column(
            modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Invest),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(text, style = FosType.Body, color = FosColors.TextSecondary)
            actionLabel?.let {
                Text(it, style = FosType.Label, color = FosColors.Invest,
                    modifier = Modifier.clickable(onClick = onAction).padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, style = FosType.Label, maxLines = 1) },
        shape    = RoundedCornerShape(FosDimens.RadiusChip),
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = FosColors.Invest.copy(alpha = 0.18f),
            selectedLabelColor     = FosColors.Invest,
            containerColor         = FosColors.Surface2,
            labelColor             = FosColors.TextSecondary,
        ),
    )
}

// ── Операции ─────────────────────────────────────────────────────────────────

private enum class OpsFilter(val title: String) { ALL("Все"), MONEY("Деньги"), TRADES("Сделки") }

/**
 * Лента всего у брокера: пополнения, выводы, переводы, прошлые предупреждения, заявки и сделки.
 * Фильтр по виду и по счёту; нажатие на строку — удалить; «+ Добавить» — ручной ввод (#49).
 * Сделки без счёта (пуш его не пишет) видны только при «Все счета».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvestOpsScreen(vm: InvestorViewModel = hiltViewModel()) {
    val portfolio by vm.portfolio.collectAsState()
    val manual = rememberInvestManualState()
    var filter  by rememberSaveable { mutableStateOf(OpsFilter.ALL) }
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    // Счёт мог исчезнуть (удалили) — тогда «Все счета».
    val accountKey = account?.takeIf { k -> portfolio.contracts.any { it.key == k } }

    val money  = if (filter == OpsFilter.TRADES) emptyList() else historyFeed(portfolio, accountKey)
    val orders = if (filter == OpsFilter.MONEY) emptyList() else
        (portfolio.activeOrders + portfolio.history)
            .filter { accountKey == null || contractKey(it.contract) == accountKey }
            .map { it.timestamp to (it as Any) }
    val rows = (money + orders).sortedByDescending { it.first }

    InvestTab("Операции", action = "+ Добавить" to { manual.add = InvestAdd.MENU }) {
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OpsFilter.values().forEach { f -> TabChip(f.title, f == filter) { filter = f } }
                }
                if (portfolio.contracts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TabChip("Все счета", accountKey == null) { account = null }
                        portfolio.contracts.forEach { c -> TabChip(c.label ?: c.contract, accountKey == c.key) { account = c.key } }
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            // Пустой список при выставленном фильтре — не «операций нет» (инвариант #26).
            val filtered = filter != OpsFilter.ALL || accountKey != null
            emptyCard(
                key   = "empty",
                title = if (filtered) "Под фильтр ничего не попало" else "Операций пока нет",
                text  = if (filtered) "Снимите фильтр по виду или по счёту." else
                    "Они появятся с пушами брокера или введите их сами: пополнение, вывод, перевод, покупку, продажу.",
                actionLabel = if (filtered) null else "Добавить вручную →",
                onAction = { manual.add = InvestAdd.MENU },
            )
        } else {
            item(key = "hint") {
                Text("Нажмите на операцию, чтобы удалить её.", style = FosType.Micro, color = FosColors.TextMuted)
            }
            itemsIndexed(rows, key = { i, (ts, _) -> "row_${i}_$ts" }) { _, (_, e) ->
                when (e) {
                    is BrokerOrder -> OrderRow(e) { manual.deleting = it }
                    else           -> FeedRow(e, portfolio) { manual.deleting = it }
                }
            }
        }
    }
    InvestManualOverlays(manual, vm, portfolio)
}

// ── Аналитика ────────────────────────────────────────────────────────────────

/**
 * Состав и результат портфеля. Всё — по цене своих сделок (или указанной вручную), без комиссий:
 * котировок у приложения нет, и это написано на вкладке. Валюты не складываются: состав считается
 * в основной валюте портфеля (той, где больше всего денег), остальные — отдельными строками.
 */
@Composable
fun InvestAnalyticsScreen(vm: InvestorViewModel = hiltViewModel()) {
    val portfolio by vm.portfolio.collectAsState()
    InvestTab("Аналитика") {
        if (portfolio.isEmpty || portfolio.summaries.isEmpty()) {
            emptyCard("empty", "Пока нечего считать",
                "Аналитика появится, когда в портфеле будут деньги или бумаги — из пушей брокера или введённые вручную.")
            return@InvestTab
        }
        item(key = "hero") {
            Column(
                modifier = Modifier.fillMaxWidth().fosHeroCard(FosTone.Invest),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("ВСЁ У БРОКЕРА", style = FosType.SectionCap, color = FosColors.Invest)
                portfolio.summaries.forEach { s ->
                    val sym = FosFormatter.currencySymbol(s.currency)
                    val invested = portfolio.accounts.filter { it.currency == s.currency }.sumOf { it.netDepositsKopecks }
                    Text(FosFormatter.amount(s.totalKopecks, sym), style = FosType.HeroAmount, color = FosColors.TextPrimary)
                    Text(
                        "Заведено ${FosFormatter.amount(invested, sym)} · бумаги ${FosFormatter.amount(s.valueKopecks, sym)} · деньги ${FosFormatter.amount(s.cashKopecks, sym)}",
                        style = FosType.MicroNum, color = FosColors.TextSecondary,
                    )
                }
                Text("по цене ваших сделок, без комиссий", style = FosType.Micro, color = FosColors.TextMuted)
            }
        }

        item(key = "periods_h") { FosSectionHeader("Результат", tone = FosTone.Invest) }
        item(key = "periods") {
            Column(
                modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Portfolio.ResultPeriod.values().forEach { p ->
                    portfolio.periods[p].orEmpty().forEach { r ->
                        val sym = FosFormatter.currencySymbol(r.currency)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label.replaceFirstChar { it.uppercase() }, style = FosType.Body, color = FosColors.TextSecondary, modifier = Modifier.weight(1f))
                            Text(
                                (if (r.pnlKopecks == 0L) FosFormatter.amount(0L, sym) else FosFormatter.signedAmount(r.pnlKopecks, sym)) +
                                    (r.percent?.let { " · ${signedPercent(it)}" } ?: ""),
                                style = FosType.SmallBold, color = pnlColor(r.pnlKopecks),
                            )
                        }
                    }
                }
            }
        }

        // Состав — в основной валюте: доли разных валют без курса не сравнить.
        val main = portfolio.summaries.maxBy { it.totalKopecks }
        val total = main.totalKopecks
        val shares = portfolio.groups.mapNotNull { g -> g.valueByCurrency[main.currency]?.takeIf { it != 0L }?.let { g to it } }
        if (total > 0 && shares.isNotEmpty()) {
            item(key = "mix_h") { FosSectionHeader("Состав", tone = FosTone.Invest) }
            items(shares, key = { "mix_${it.first.group.name}" }) { (g, value) ->
                ShareRow(g.group.title, value, total, FosFormatter.currencySymbol(main.currency))
            }
        }

        if (portfolio.positions.isNotEmpty()) {
            item(key = "pos_h") { FosSectionHeader("Бумаги по результату", tone = FosTone.Invest) }
            items(portfolio.positions.sortedByDescending { it.pnlKopecks }, key = { "pnl_${it.broker}_${it.ticker}_${it.currency}" }) { p ->
                val sym = FosFormatter.currencySymbol(p.currency)
                Row(
                    modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.ticker, style = FosType.BodySemi, color = FosColors.TextPrimary)
                        Text(FosFormatter.amount(p.valueKopecks, sym), style = FosType.MicroNum, color = FosColors.TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (p.pnlKopecks == 0L) FosFormatter.amount(0L, sym) else FosFormatter.signedAmount(p.pnlKopecks, sym),
                            style = FosType.SmallBold, color = pnlColor(p.pnlKopecks),
                        )
                        p.pnlPercent?.let { Text(signedPercent(it), style = FosType.MicroNum, color = pnlColor(p.pnlKopecks)) }
                    }
                }
            }
        }
    }
}

/** Доля группы: подпись, сумма, процент и полоса. Полоса — индиго режима, не цвет «доход/расход». */
@Composable
private fun ShareRow(title: String, value: Long, total: Long, sym: String) {
    val share = (value.toDouble() / total).coerceIn(0.0, 1.0)
    Column(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("${FosFormatter.amount(value, sym)} · ${String.format(java.util.Locale("ru"), "%.1f %%", share * 100)}",
                style = FosType.MicroNum, color = FosColors.TextSecondary)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(FosColors.Surface2)) {
            Box(Modifier.fillMaxWidth(share.toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(FosColors.Invest))
        }
    }
}

// ── Календарь ────────────────────────────────────────────────────────────────

/**
 * Календарь выплат. Купоны, дивиденды, погашения и оферты появятся, когда придут их пуши или
 * подключатся котировки из сети (бэклог) — угадывать даты нельзя. Сейчас здесь честное пустое
 * состояние и то, что УЖЕ требует действия: открытые предупреждения брокера.
 */
@Composable
fun InvestCalendarScreen(vm: InvestorViewModel = hiltViewModel()) {
    val portfolio by vm.portfolio.collectAsState()
    InvestTab("Календарь") {
        val open = portfolio.openAlerts
        if (open.isNotEmpty()) {
            item(key = "alerts_h") { FosSectionHeader("Требует внимания", tone = FosTone.Warning) }
            items(open, key = { "al_${it.alert.timestamp}_${it.alert.contract}" }) { st ->
                val sym = FosFormatter.currencySymbol(st.alert.currency)
                Column(
                    modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Rail, FosTone.Warning, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Пополнить счёт ${st.alert.contract}", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text("от ${FosFormatter.amount(st.remainingKopecks, sym)} · требование от ${FosFormatter.dayLabel(st.alert.timestamp)}",
                        style = FosType.MicroNum, color = FosColors.Warning)
                }
            }
        }
        emptyCard(
            key   = "plan",
            title = "Купоны, дивиденды, погашения",
            text  = "Даты выплат появятся здесь, когда брокер пришлёт о них пуши или когда подключатся " +
                "котировки из сети. Пришлите скриншот пуша о купоне или дивиденде — добавим его разбор.",
        )
    }
}

// ── Счета ────────────────────────────────────────────────────────────────────

/**
 * Счета у брокера: что пришло и ушло по каждому, открыть счёт на «Портфеле», удалить, добавить;
 * найденное приложение брокера. Остатка по счёту нет — сделки приходят без номера счёта (#47).
 */
@Composable
fun InvestAccountsScreen(onOpenAccount: () -> Unit, vm: InvestorViewModel = hiltViewModel()) {
    val portfolio by vm.portfolio.collectAsState()
    val brokerPackage by vm.brokerPackage.collectAsState()
    val manual = rememberInvestManualState()
    var toHide by remember { mutableStateOf<Portfolio.Contract?>(null) }

    InvestTab("Счета", action = "+ Счёт" to { manual.add = InvestAdd.ACCOUNT }) {
        if (portfolio.contracts.isEmpty()) {
            emptyCard("empty", "Счетов пока нет",
                "Счёт появится, когда брокер упомянет его в пуше, или добавьте его сами.",
                "Добавить счёт →") { manual.add = InvestAdd.ACCOUNT }
        } else {
            items(portfolio.contracts, key = { "c_${it.broker}_${it.key}" }) { c ->
                val moves = portfolio.movements
                val byCur = moves.groupBy {
                    when (it) {
                        is com.financeos.hub.core.invest.BrokerCashMove -> it.currency
                        is com.financeos.hub.core.invest.BrokerInternalTransfer -> it.currency
                        else -> "RUB"
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(c.title, style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text(c.broker, style = FosType.Micro, color = FosColors.TextSecondary)
                    byCur.forEach { (cur, list) ->
                        val inflow = list.sumOf { incomingTo(it, c.key) }
                        val outflow = list.sumOf { outgoingFrom(it, c.key) }
                        if (inflow != 0L || outflow != 0L) {
                            val sym = FosFormatter.currencySymbol(cur)
                            Text("Пришло ${FosFormatter.amount(inflow, sym)} · ушло ${FosFormatter.amount(outflow, sym)}",
                                style = FosType.MicroNum, color = FosColors.TextSecondary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Открыть в портфеле", style = FosType.Label, color = FosColors.Invest,
                            modifier = Modifier.clickable { vm.selectContract(c.key); onOpenAccount() }.padding(vertical = 8.dp))
                        Text("Удалить", style = FosType.Label, color = FosColors.TextMuted,
                            modifier = Modifier.clickable { toHide = c }.padding(vertical = 8.dp))
                    }
                }
            }
        }
        item(key = "app") {
            Column(
                modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Приложение брокера", style = FosType.BodySemi, color = FosColors.TextPrimary)
                Text(
                    brokerPackage?.let { "Найдено: $it. Его пуши записываются." }
                        ?: "Ещё не найдено — приложение узнает его по первому пушу о пополнении или заявке.",
                    style = FosType.Micro, color = FosColors.TextSecondary,
                )
                if (brokerPackage != null) {
                    Text("Не то приложение? Сбросить", style = FosType.Label, color = FosColors.TextMuted,
                        modifier = Modifier.clickable { vm.resetBrokerPackage() }.padding(vertical = 8.dp))
                }
            }
        }
        item(key = "note") {
            Text(
                "Остатка по каждому счёту нет: сделки приходят без номера счёта. Лоты бумаг и котировки из сети — в планах.",
                style = FosType.Micro, color = FosColors.TextMuted,
            )
        }
    }
    InvestManualOverlays(manual, vm, portfolio)
    toHide?.let { c ->
        ConfirmDelete(
            title = "Удалить счёт ${c.title}?",
            text  = "Счёт пропадёт из списка, даже если брокер упомянет его в пуше. Операции по нему " +
                "останутся в истории и в итоге — ошибочные удалите в «Операциях». Вернуть счёт можно, " +
                "добавив его снова.",
            onConfirm = { vm.hideAccount(c.broker, c.contract); toHide = null },
            onDismiss = { toHide = null },
        )
    }
}
