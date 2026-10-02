package com.financeos.hub.features.investor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.invest.BrokerCashMove
import com.financeos.hub.core.invest.BrokerEvent
import com.financeos.hub.core.invest.BrokerInternalTransfer
import com.financeos.hub.core.invest.BrokerOrder
import com.financeos.hub.core.invest.MarginAlerts
import com.financeos.hub.core.invest.SecurityGroups
import com.financeos.hub.core.invest.isManual
import com.financeos.hub.core.invest.contractKey
import com.financeos.hub.core.invest.OrderSide
import com.financeos.hub.core.invest.OrderStatus
import com.financeos.hub.core.invest.Portfolio
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard
import com.financeos.hub.ui.theme.fosCardSurface
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Содержимое главной в режиме «Инвестор» — элементы того же `LazyColumn`, что и у кошелька.
 *
 * Здесь нет ни одной операции кошелька, ни одной карты, ни одного банковского баланса: режим — это
 * ДРУГИЕ деньги, и смешивать их значит снова получить «потратил 10 000», которых никто не тратил.
 *
 * Цвет результата — Positive/Negative: на этом экране рост и убыток позиции законно зелёный и
 * красный (исключение из правила #2, записано в CLAUDE.md). Всё остальное — индиго режима.
 */
fun LazyListScope.investorItems(
    portfolio       : Portfolio.Result,
    isSample        : Boolean,
    brokerPackage   : String?,
    selectedContract: String?,
    onPickAccount   : () -> Unit,
    onOpenHistory   : () -> Unit,
    onOpenOrders    : () -> Unit,
    onAdd           : () -> Unit,
    onEventClick    : (BrokerEvent) -> Unit,
    onPositionClick : (Portfolio.Position) -> Unit,
    onDismissAlert  : (String?) -> Unit,
    onShowSample    : () -> Unit,
    onHideSample    : () -> Unit,
    onResetBroker   : () -> Unit,
) {
    if (portfolio.isEmpty) {
        item(key = "invest_empty") { InvestorEmpty(brokerPackage, onShowSample, onResetBroker, onAdd) }
        item(key = "invest_bottom") { Spacer(Modifier.height(24.dp)) }
        return
    }

    // Выбранный счёт мог исчезнуть из данных (скрыли пример) — тогда это «весь портфель».
    val contract = portfolio.contracts.firstOrNull { it.key == selectedContract }

    // Предупреждения — выше всего и при ЛЮБОМ выбранном счёте: брокер грозит закрыть позиции, и
    // требование по соседнему счёту не должно прятаться за выбором.
    val openAlerts = portfolio.openAlerts
    items(openAlerts, key = { "alert_${it.alert.timestamp}_${it.alert.contract}" }) { st ->
        MarginAlertCard(st, portfolio.titleOf(st.alert.contract), onDismiss = { onDismissAlert(st.alert.id) })
    }

    item(key = "invest_hero") {
        if (contract == null) {
            PortfolioHero(portfolio, isSample, onHideSample, onPickAccount, onOpenHistory, onOpenOrders, onAdd)
        } else {
            ContractHero(portfolio, contract, onPickAccount)
        }
    }

    if (contract == null) {
        // Группы, как у БКС: «Валюта» (деньги на счетах и валютные бумаги), «Акции», «Фонды»…
        // Движения денег и заявки — по кнопкам «История» и «Заявки» в главном блоке.
        items(portfolio.groups, key = { "grp_${it.group.name}" }) { GroupCard(it, onPositionClick) }
    } else {
        // Выбранный счёт: его движения и прошлые предупреждения прямо на экране — больше о нём
        // ничего не известно.
        val feed = historyFeed(portfolio, contract.key)
        if (feed.isNotEmpty()) {
            item(key = "invest_moves_h") { FosSectionHeader("Движения денег", tone = FosTone.Invest) }
            itemsIndexed(feed, key = { i, (ts, _) -> "mv_${i}_$ts" }) { _, (_, e) -> FeedRow(e, portfolio, onEventClick) }
        }
    }

    if (contract != null && (portfolio.positions.isNotEmpty() || portfolio.history.isNotEmpty())) {
        item(key = "invest_orders_note") {
            Text(
                "Бумаги и сделки — во «Всём портфеле»: пуш о сделке не пишет, с какого счёта она прошла.",
                style    = FosType.Micro,
                color    = FosColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    // Сброс найденного приложения — и когда данные уже есть: ошибочно найденное (мессенджер с
    // пересланным пушем) успело бы записать события, и пустого состояния с кнопкой больше нет.
    if (brokerPackage != null && !isSample) {
        item(key = "invest_broker_app") { BrokerAppLine(brokerPackage, onResetBroker) }
    }
    item(key = "invest_bottom") { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun BrokerAppLine(pkg: String, onReset: () -> Unit) {
    Text(
        "Приложение брокера: $pkg · Не то приложение? Сбросить",
        style    = FosType.Micro,
        color    = FosColors.TextMuted,
        modifier = Modifier
            .clickable(onClick = onReset)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    )
}

/**
 * Движения денег и прошлые предупреждения, новые сверху; [contractKey] — только по этому счёту.
 * Общая для экрана выбранного счёта и листа «История», чтобы они не разошлись.
 */
internal fun historyFeed(portfolio: Portfolio.Result, contractKey: String?): List<Pair<Long, Any>> {
    val movements  = portfolio.movements.filter { contractKey == null || it.touches(contractKey) }
    val pastAlerts = portfolio.alerts.filter {
        !it.isOpen && (contractKey == null || contractKey(it.alert.contract) == contractKey)
    }
    return (movements.map { it.timestamp to it as Any } + pastAlerts.map { it.alert.timestamp to it as Any })
        .sortedByDescending { it.first }
}

@Composable
internal fun FeedRow(e: Any, portfolio: Portfolio.Result, onEventClick: (BrokerEvent) -> Unit) {
    when (e) {
        is MarginAlerts.State -> PastAlertRow(e, portfolio.titleOf(e.alert.contract))
        is BrokerEvent        -> MovementRow(e, portfolio, onEventClick)
    }
}

/** Затрагивает ли движение счёт с ключом [key]. */
private fun BrokerEvent.touches(key: String): Boolean = when (this) {
    is BrokerCashMove         -> contractKey(contract) == key
    is BrokerInternalTransfer -> contractKey(fromContract) == key || contractKey(toContract) == key
    else                      -> false
}

/** «3468071/25 (Облигации)» — как счёт назван у брокера; если не назван — номер. */
private fun Portfolio.Result.titleOf(contract: String?): String {
    val key = contractKey(contract) ?: return "Счёт"
    return contracts.firstOrNull { it.key == key }?.title ?: contract!!
}

/** Короткое имя: название, если есть, иначе номер. «Облигации», «580922/19-м». */
private fun Portfolio.Result.shortOf(contract: String?): String {
    val key = contractKey(contract) ?: return "счёт"
    val c = contracts.firstOrNull { it.key == key }
    return c?.label ?: c?.contract ?: contract!!
}

// ── Блоки ────────────────────────────────────────────────────────────────────

@Composable
private fun InvestorEmpty(brokerPackage: String?, onShowSample: () -> Unit, onResetBroker: () -> Unit, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Invest),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Здесь будут ваши инвестиции", style = FosType.SubHeader, color = FosColors.TextPrimary)
        Text(
            "Брокерские счета, позиции, заявки и сделки — отдельно от кошелька. Покупки, переводы и " +
                "карты сюда не попадают, а пополнения брокера, заявки и сделки — не попадают в кошелёк.",
            style = FosType.Body,
            color = FosColors.TextSecondary,
        )
        Text(
            if (brokerPackage != null) {
                "Приложение брокера найдено: $brokerPackage. Его пуши записываются — портфель появится с " +
                    "первым пополнением, переводом или сделкой."
            } else {
                "Приложение брокера ещё не найдено. Как только придёт любой пуш БКС — пополнение или " +
                    "заявка, — приложение его узнает."
            },
            style = FosType.Micro,
            color = if (brokerPackage != null) FosColors.Invest else FosColors.TextMuted,
        )
        if (brokerPackage != null) {
            // Первое совпадение не перезаписывается, поэтому ошибочное (пересланный в мессенджер пуш)
            // иначе осталось бы навсегда.
            Text(
                "Не то приложение? Сбросить",
                style    = FosType.Micro,
                color    = FosColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onResetBroker)
                    .padding(vertical = 10.dp),
            )
        }
        // Пуши могут не прийти вовсе — ввести портфель руками можно сразу (#49).
        Text(
            "Добавить вручную: счёт, актив или операцию →",
            style    = FosType.Label,
            color    = FosColors.Invest,
            modifier = Modifier.clickable(onClick = onAdd).padding(vertical = 12.dp),
        )
        Text(
            "Показать на примере ваших пушей БКС →",
            style    = FosType.Label,
            color    = FosColors.Invest,
            modifier = Modifier
                .clickable(onClick = onShowSample)
                .padding(vertical = 12.dp),
        )
    }
}

/**
 * Главный блок, как у БКС: счёт сверху, крупная сумма, результат «за всё время», кнопки.
 *
 * Сумма — по цене ваших последних сделок, а не по рынку: котировок у приложения нет (бэклог). Это
 * написано прямо под суммой, иначе расхождение с приложением брокера выглядело бы ошибкой.
 */
@Composable
private fun PortfolioHero(
    portfolio    : Portfolio.Result,
    isSample     : Boolean,
    onHideSample : () -> Unit,
    onPickAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOrders : () -> Unit,
    onAdd        : () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().fosHeroCard(FosTone.Invest),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isSample) {
            Text(
                "ПРИМЕР · скрыть",
                style    = FosType.Micro,
                color    = FosColors.Invest,
                modifier = Modifier
                    .clip(RoundedCornerShape(FosDimens.RadiusChip))
                    .background(FosColors.Invest.copy(alpha = 0.14f))
                    .clickable(onClick = onHideSample)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
        if (portfolio.contracts.isNotEmpty()) AccountChip("Весь портфель", onPickAccount)
        else Text("ПОРТФЕЛЬ", style = FosType.SectionCap, color = FosColors.Invest)
        // Период пилюли — как у БКС. Хук без условий и до ветвлений (инвариант #4).
        var period by rememberSaveable { mutableStateOf(Portfolio.ResultPeriod.ALL) }
        // Валюты не складываются — по строке на каждую, как «Состояние» кошелька.
        portfolio.summaries.forEach { s ->
            val sym = FosFormatter.currencySymbol(s.currency)
            Text(FosFormatter.amount(s.totalKopecks, sym), style = FosType.HeroAmount, color = FosColors.TextPrimary)
            val r = portfolio.periods[period]?.firstOrNull { it.currency == s.currency }
            ResultPill(r?.pnlKopecks ?: 0L, r?.percent, sym, period.label)
        }
        PeriodChips(period) { period = it }
        Text(
            if (period == Portfolio.ResultPeriod.ALL) "по цене ваших сделок, без комиссий"
            // Без котировок стоимость меняется только на своих сделках — иначе ноль выглядел бы ошибкой.
            else "по цене ваших сделок, без комиссий; без сделок за период — ноль",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val active = portfolio.activeOrders.size
            HeroButton("История", Modifier.weight(1f), onOpenHistory)
            HeroButton(if (active > 0) "Заявки · $active" else "Заявки", Modifier.weight(1f), onOpenOrders)
            // Ручной ввод (#49): операция, актив, счёт — пуши могут не прийти.
            HeroButton("Добавить", Modifier.weight(1f), onAdd)
        }
    }
}

/** «24 часа · Месяц · Всё время» — какой результат показывать в пилюле. */
@Composable
private fun PeriodChips(selected: Portfolio.ResultPeriod, onSelect: (Portfolio.ResultPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Portfolio.ResultPeriod.values().forEach { p ->
            val on = p == selected
            Text(
                when (p) {
                    Portfolio.ResultPeriod.DAY   -> "24 часа"
                    Portfolio.ResultPeriod.MONTH -> "Месяц"
                    Portfolio.ResultPeriod.ALL   -> "Всё время"
                },
                style    = FosType.Label,
                color    = if (on) FosColors.TextPrimary else FosColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(FosDimens.RadiusChip))
                    .background(if (on) FosColors.Invest.copy(alpha = 0.22f) else FosColors.Surface2)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** «−1 527,81 ₽ · 10,51 % за всё время» — пилюля под суммой, как у БКС. */
@Composable
private fun ResultPill(pnl: Long, pct: Double?, sym: String, label: String) {
    val amount = if (pnl == 0L) FosFormatter.amount(0L, sym) else FosFormatter.signedAmount(pnl, sym)
    val percent = pct?.let { " · ${signedPercent(it)}" } ?: ""
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(FosDimens.RadiusChip))
            .background(FosColors.Surface2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$amount$percent", style = FosType.MicroNum, color = pnlColor(pnl))
        Text("  $label", style = FosType.Micro, color = FosColors.TextSecondary)
    }
}

@Composable
private fun HeroButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        // Нажатие — между заливкой и отступом (как велит FosSurface): иначе поле вокруг подписи мёртвое.
        modifier = modifier
            .fosCardSurface(FosCardStyle.Sunken, FosTone.Invest, FosDimens.RadiusInset)
            .clickable(onClick = onClick)
            .padding(FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        // Без значков (решение пользователя): эмодзи рисуется по-разному на разных телефонах и
        // спорит с подписью. Подпись — по центру кнопки.
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = FosType.BodySemi, color = FosColors.TextPrimary, maxLines = 1)
    }
}

/**
 * Группа бумаг — сворачиваемая карточка, как у БКС: заголовок с суммой и результатом, внутри строки.
 * Состояние «свёрнута» переживает прокрутку и поворот (`rememberSaveable` в элементе с ключом).
 */
@Composable
private fun GroupCard(g: Portfolio.Group, onPositionClick: (Portfolio.Position) -> Unit) {
    var open by rememberSaveable(g.group.name) { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Без clip: скругление чипа на высокой строке срезало первую букву заголовка и первую цифру
        // суммы («Валюта» читалось как «ʙалюта»). Нажатие — на всю строку, рябь прямоугольная.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(g.group.title, style = FosType.BodySemi, color = FosColors.TextPrimary)
                g.valueByCurrency.forEach { (cur, value) ->
                    val sym  = FosFormatter.currencySymbol(cur)
                    val pnl  = g.pnlByCurrency[cur]
                    val cost = g.costByCurrency[cur] ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(FosFormatter.amount(value, sym), style = FosType.SmallBold, color = FosColors.TextPrimary)
                        if (pnl != null && cost != 0L) {
                            Text(
                                "  ${signedPercent(pnl * 100.0 / cost)}",
                                style = FosType.MicroNum,
                                color = pnlColor(pnl),
                            )
                        }
                    }
                }
            }
            Text(if (open) "▲" else "▼", style = FosType.Label, color = FosColors.TextSecondary)
        }
        if (open) {
            g.cash.forEach { CashRow(it) }
            g.positions.forEach { p -> PositionRow(p) { onPositionClick(p) } }
        }
    }
}

/** Свободные деньги на счетах в одной валюте — «Российский рубль 11,14 ₽». */
@Composable
private fun CashRow(acc: Portfolio.BrokerAccount) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(SecurityGroups.currencyName(acc.currency), style = FosType.BodySemi, color = FosColors.TextPrimary)
            // Комиссий в пушах нет, поэтому цифра приблизительна и может уйти в минус — подпись это говорит.
            Text(
                if (acc.cashKopecks < 0) "деньги · ${acc.broker} · без комиссий, приблизительно"
                else "свободные деньги · ${acc.broker}",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }
        Text(
            FosFormatter.amount(acc.cashKopecks, FosFormatter.currencySymbol(acc.currency)),
            style = FosType.SmallBold,
            color = FosColors.TextPrimary,
        )
    }
}

/** «Весь портфель ▾» / «3468071/25 (Облигации) ▾» — как выбор счёта в приложении брокера. */
@Composable
private fun AccountChip(title: String, onClick: () -> Unit) {
    Text(
        "$title  ▾",
        style    = FosType.Label,
        color    = FosColors.TextPrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(FosDimens.RadiusChip))
            .background(FosColors.Invest.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Один счёт брокера. Остатка по нему нет — пуш о сделке не говорит, с какого счёта она прошла
 * (инвариант #47), — поэтому показано, сколько пришло и ушло по пушам, и это так и подписано.
 */
@Composable
private fun ContractHero(portfolio: Portfolio.Result, contract: Portfolio.Contract, onPickAccount: () -> Unit) {
    val moves = portfolio.movements.filter { it.touches(contract.key) }
    val byCurrency = moves.groupBy {
        when (it) { is BrokerCashMove -> it.currency; is BrokerInternalTransfer -> it.currency; else -> "RUB" }
    }
    Column(
        modifier = Modifier.fillMaxWidth().fosHeroCard(FosTone.Invest),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("СЧЁТ У БРОКЕРА · ${contract.broker}", style = FosType.SectionCap, color = FosColors.Invest)
        AccountChip(contract.title, onPickAccount)
        byCurrency.forEach { (cur, list) ->
            val sym = FosFormatter.currencySymbol(cur)
            val inflow = list.sumOf { e -> incomingTo(e, contract.key) }
            val outflow = list.sumOf { e -> outgoingFrom(e, contract.key) }
            Text(
                "Пришло ${FosFormatter.amount(inflow, sym)} · ушло ${FosFormatter.amount(outflow, sym)}",
                style = FosType.SmallBold,
                color = FosColors.TextPrimary,
            )
        }
        Text(
            "По пушам брокера. Остаток счёта отсюда не виден: сделки приходят без номера счёта.",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )
    }
}

private fun incomingTo(e: BrokerEvent, key: String): Long = when (e) {
    is BrokerCashMove         -> if (contractKey(e.contract) == key && e.amountKopecks > 0) e.amountKopecks else 0L
    is BrokerInternalTransfer -> if (contractKey(e.toContract) == key) e.amountKopecks else 0L
    else                      -> 0L
}

private fun outgoingFrom(e: BrokerEvent, key: String): Long = when (e) {
    is BrokerCashMove         -> if (contractKey(e.contract) == key && e.amountKopecks < 0) -e.amountKopecks else 0L
    is BrokerInternalTransfer -> if (contractKey(e.fromContract) == key) e.amountKopecks else 0L
    else                      -> 0L
}

/**
 * Требование брокера пополнить счёт. Янтарный, не красный: красный в приложении — трата и
 * перерасход (правило #2), а это предупреждение о риске.
 */
@Composable
private fun MarginAlertCard(st: MarginAlerts.State, title: String, onDismiss: () -> Unit) {
    val sym = FosFormatter.currencySymbol(st.alert.currency)
    Column(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Rail, FosTone.Warning),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Критично низкий баланс", style = FosType.BodySemi, color = FosColors.Warning)
        Text(title, style = FosType.SmallBold, color = FosColors.TextPrimary)
        Text(
            "Пополните счёт от ${FosFormatter.amount(st.remainingKopecks, sym)}. Если стоимость портфеля " +
                "станет ниже нуля, брокер начнёт закрывать позиции.",
            style = FosType.Body,
            color = FosColors.TextSecondary,
        )
        if (st.coveredKopecks > 0L) {
            Text(
                "Уже пришло ${FosFormatter.amount(st.coveredKopecks, sym)} из ${FosFormatter.amount(st.alert.requiredKopecks, sym)}",
                style = FosType.MicroNum,
                color = FosColors.TextSecondary,
            )
        }
        Text(
            "${FosFormatter.dayLabel(st.alert.timestamp)} · ${timeOf(st.alert.timestamp)}",
            style = FosType.MicroNum,
            color = FosColors.TextMuted,
        )
        // Закрывается само, когда на счёт придут деньги; эта кнопка — для того, чего приложение не
        // увидит (пополнение без пуша, закрытая позиция).
        if (st.alert.id != null) {
            Text(
                "Закрыть",
                style    = FosType.Label,
                color    = FosColors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PastAlertRow(st: MarginAlerts.State, title: String) {
    val sym = FosFormatter.currencySymbol(st.alert.currency)
    val status = when {
        st.isCovered     -> "покрыто"
        st.superseded    -> "заменено новым"
        else             -> "закрыто"
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .fosCard(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Низкий баланс · $title", style = FosType.BodySemi, color = FosColors.TextSecondary, maxLines = 1)
            Text(
                "требовалось ${FosFormatter.amount(st.alert.requiredKopecks, sym)} · ${FosFormatter.dayLabel(st.alert.timestamp)}",
                style = FosType.MicroNum,
                color = FosColors.TextMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(status, style = FosType.Micro, color = FosColors.TextMuted)
    }
}

/** Пополнение, вывод или перевод между счетами. Перевод — нейтральный «↔», как в кошельке. */
@Composable
private fun MovementRow(e: BrokerEvent, portfolio: Portfolio.Result, onClick: (BrokerEvent) -> Unit) {
    val (title, sub, amount, color) = when (e) {
        is BrokerInternalTransfer -> {
            val sym = FosFormatter.currencySymbol(e.currency)
            Quad(
                "Перевод между счетами",
                "${portfolio.shortOf(e.fromContract)} → ${portfolio.shortOf(e.toContract)}",
                "↔ ${FosFormatter.amount(e.amountKopecks, sym)}",
                FosColors.TextPrimary,
            )
        }
        is BrokerCashMove -> {
            val sym = FosFormatter.currencySymbol(e.currency)
            Quad(
                if (e.amountKopecks >= 0) "Пополнение" else "Вывод",
                e.contract?.let { "счёт ${portfolio.shortOf(it)}" } ?: e.broker,
                FosFormatter.signedAmount(e.amountKopecks, sym),
                FosColors.TextPrimary,
            )
        }
        else -> return
    }
    Row(
        // Нажатие — удалить (у примера id нет, нажимать нечего). Нажатие между огранкой и отступом.
        modifier = Modifier.fillMaxWidth()
            .fosCardSurface(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall)
            .clickable(enabled = e.id != null) { onClick(e) }
            .padding(FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(
                "$sub · ${FosFormatter.dayLabel(e.timestamp)}${if (e.isManual) " · вручную" else ""}",
                style = FosType.MicroNum,
                color = FosColors.TextSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(amount, style = FosType.SmallBold, color = color)
    }
}

private data class Quad(val a: String, val b: String, val c: String, val d: Color)

private fun timeOf(ts: Long): String =
    java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

/** «LQDT · 4 760 шт. · 2,0985 ₽ → 2,0985 ₽» слева, стоимость и результат справа — как у БКС. */
@Composable
private fun PositionRow(p: Portfolio.Position, onClick: () -> Unit) {
    val sym = FosFormatter.currencySymbol(p.currency)
    // Нажатие — цена и удаление актива (#49).
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(p.ticker, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text("${grouped(p.quantity)} шт.", style = FosType.MicroNum, color = FosColors.TextSecondary)
            Text(
                "${price(p.avgPriceMicros)} $sym → ${price(p.lastPriceMicros)} $sym",
                style = FosType.MicroNum,
                color = FosColors.TextMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(FosFormatter.amount(p.valueKopecks, sym), style = FosType.SmallBold, color = FosColors.TextPrimary)
            Text(
                if (p.pnlKopecks == 0L) FosFormatter.amount(0L, sym) else FosFormatter.signedAmount(p.pnlKopecks, sym),
                style = FosType.MicroNum,
                color = pnlColor(p.pnlKopecks),
            )
            p.pnlPercent?.let {
                Text(signedPercent(it), style = FosType.MicroNum, color = pnlColor(p.pnlKopecks))
            }
        }
    }
}

@Composable
internal fun OrderRow(o: BrokerOrder, onClick: ((BrokerOrder) -> Unit)? = null) {
    val sym = FosFormatter.currencySymbol(o.currency)
    val (label, color) = when (o.status) {
        OrderStatus.ACTIVE    -> "Активна"   to FosColors.Invest
        OrderStatus.FILLED    -> "Исполнена" to FosColors.TextPrimary
        OrderStatus.CANCELLED -> "Отменена"  to FosColors.TextMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .fosCardSurface(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall)
            // Активную заявку не удаляют: она ещё может исполниться, и её следующий пуш придёт.
            .clickable(enabled = onClick != null && o.id != null && o.status != OrderStatus.ACTIVE) { onClick?.invoke(o) }
            .padding(FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${o.ticker} · ${if (o.side == OrderSide.BUY) "покупка" else "продажа"}",
                style = FosType.BodySemi,
                color = if (o.status == OrderStatus.CANCELLED) FosColors.TextMuted else FosColors.TextPrimary,
            )
            Text(
                "${grouped(o.lots)} ${if (o.isManual) "шт." else "лот."} по ${price(o.priceMicros)} $sym · ${FosFormatter.dayLabel(o.timestamp)}" +
                    if (o.isManual) " · вручную" else "",
                style = FosType.MicroNum,
                color = FosColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style    = FosType.Micro,
            color    = color,
            modifier = Modifier
                .clip(RoundedCornerShape(FosDimens.RadiusChip))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

// ── Форматирование ───────────────────────────────────────────────────────────


private fun pnlColor(kopecks: Long): Color = when {
    kopecks > 0L -> FosColors.Positive
    kopecks < 0L -> FosColors.Negative
    else         -> FosColors.TextSecondary   // ноль — не рост и не убыток
}

private fun signedPercent(p: Double): String {
    val text = String.format(Locale("ru"), "%.2f %%", abs(p))
    return when {
        p > 0.005  -> "+$text"
        p < -0.005 -> "−$text"
        else       -> "0,00 %"
    }
}

private fun grouped(n: Long): String = NumberFormat.getIntegerInstance(Locale("ru")).format(n)

/** Цена бумаги с биржевой точностью: «2,0985», «250,12», не меньше двух знаков. */
internal fun price(micros: Long): String {
    val whole = micros / 1_000_000
    val frac  = (micros % 1_000_000).toString().padStart(6, '0').trimEnd('0').padEnd(2, '0')
    return "${grouped(whole)},$frac"
}
