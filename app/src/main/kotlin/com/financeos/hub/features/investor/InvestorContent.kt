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
    onDismissAlert  : (String?) -> Unit,
    onShowSample    : () -> Unit,
    onHideSample    : () -> Unit,
    onResetBroker   : () -> Unit,
) {
    if (portfolio.isEmpty) {
        item(key = "invest_empty") { InvestorEmpty(brokerPackage, onShowSample, onResetBroker) }
        item(key = "invest_bottom") { Spacer(Modifier.height(24.dp)) }
        return
    }

    // Выбранный счёт мог исчезнуть из данных (скрыли пример) — тогда это «весь портфель».
    val contract = portfolio.contracts.firstOrNull { it.key == selectedContract }

    // Предупреждения — выше всего: брокер грозит закрыть позиции, и это важнее итогов.
    val openAlerts = portfolio.openAlerts.filter { contract == null || contractKey(it.alert.contract) == contract.key }
    items(openAlerts, key = { "alert_${it.alert.timestamp}_${it.alert.contract}" }) { st ->
        MarginAlertCard(st, portfolio.titleOf(st.alert.contract), onDismiss = { onDismissAlert(st.alert.id) })
    }

    item(key = "invest_hero") {
        if (contract == null) {
            PortfolioHero(portfolio, isSample, onHideSample, onPickAccount)
        } else {
            ContractHero(portfolio, contract, onPickAccount)
        }
    }

    if (contract == null) {
        if (portfolio.accounts.isNotEmpty()) {
            item(key = "invest_brokers_h") { FosSectionHeader("Брокеры", tone = FosTone.Invest) }
            items(portfolio.accounts, key = { "acc_${it.broker}_${it.currency}" }) {
                BrokerCard(it, portfolio.contracts.count { c -> c.broker == it.broker })
            }
        }
        if (portfolio.positions.isNotEmpty()) {
            item(key = "invest_positions_h") { FosSectionHeader("Позиции", tone = FosTone.Invest) }
            items(portfolio.positions, key = { "pos_${it.broker}_${it.ticker}_${it.currency}" }) { PositionRow(it) }
        }
    }

    // Движения денег и прошлые предупреждения — по выбранному счёту или по всем.
    val movements = portfolio.movements.filter { contract == null || it.touches(contract.key) }
    val pastAlerts = portfolio.alerts.filter { !it.isOpen && (contract == null || contractKey(it.alert.contract) == contract.key) }
    val feed = (movements.map { it.timestamp to it } + pastAlerts.map { it.alert.timestamp to it })
        .sortedByDescending { it.first }
    if (feed.isNotEmpty()) {
        item(key = "invest_moves_h") { FosSectionHeader("Движения денег", tone = FosTone.Invest) }
        itemsIndexed(feed, key = { i, (ts, _) -> "mv_${i}_$ts" }) { _, (_, e) ->
            when (e) {
                is MarginAlerts.State -> PastAlertRow(e, portfolio.titleOf(e.alert.contract))
                is BrokerEvent        -> MovementRow(e, portfolio)
            }
        }
    }

    if (contract == null) {
        val orders = portfolio.activeOrders + portfolio.history
        if (orders.isNotEmpty()) {
            item(key = "invest_orders_h") { FosSectionHeader("Заявки и сделки", tone = FosTone.Invest) }
            // Индекс в ключе: один и тот же пуш может прийти дважды, и одинаковый ключ уронил бы список.
            itemsIndexed(orders, key = { i, o -> "ord_${i}_${o.timestamp}" }) { _, o -> OrderRow(o) }
        }
    } else if (portfolio.positions.isNotEmpty() || portfolio.history.isNotEmpty()) {
        item(key = "invest_orders_note") {
            Text(
                "Бумаги и сделки — во «Всём портфеле»: пуш о сделке не пишет, с какого счёта она прошла.",
                style    = FosType.Micro,
                color    = FosColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    item(key = "invest_bottom") { Spacer(Modifier.height(24.dp)) }
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
private fun InvestorEmpty(brokerPackage: String?, onShowSample: () -> Unit, onResetBroker: () -> Unit) {
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
                    .clip(RoundedCornerShape(FosDimens.RadiusChip))
                    .clickable(onClick = onResetBroker)
                    .padding(vertical = 10.dp),
            )
        }
        Text(
            "Показать на примере ваших пушей БКС →",
            style    = FosType.Label,
            color    = FosColors.Invest,
            modifier = Modifier
                .clip(RoundedCornerShape(FosDimens.RadiusChip))
                .clickable(onClick = onShowSample)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun PortfolioHero(
    portfolio    : Portfolio.Result,
    isSample     : Boolean,
    onHideSample : () -> Unit,
    onPickAccount: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().fosHeroCard(FosTone.Invest),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ПОРТФЕЛЬ", style = FosType.SectionCap, color = FosColors.Invest, modifier = Modifier.weight(1f))
            if (isSample) {
                Text(
                    "ПРИМЕР · скрыть",
                    style    = FosType.Micro,
                    color    = FosColors.Invest,
                    modifier = Modifier
                        .clip(RoundedCornerShape(FosDimens.RadiusChip))
                        .background(FosColors.Invest.copy(alpha = 0.14f))
                        .clickable(onClick = onHideSample)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (portfolio.contracts.isNotEmpty()) AccountChip("Весь портфель", onPickAccount)
        // Валюты не складываются — по строке на каждую, как «Состояние» кошелька.
        portfolio.summaries.forEach { s ->
            val sym = FosFormatter.currencySymbol(s.currency)
            Text(FosFormatter.amount(s.totalKopecks, sym), style = FosType.HeroAmount, color = FosColors.TextPrimary)
            Text(
                "Бумаги ${FosFormatter.amount(s.valueKopecks, sym)} · деньги ${FosFormatter.amount(s.cashKopecks, sym)}",
                style = FosType.MicroNum,
                color = FosColors.TextSecondary,
            )
            val pct = s.pnlPercent?.let { " (${signedPercent(it)})" } ?: ""
            // Ноль — без знака: «+0,00 ₽ (0,00 %)» спорит само с собой.
            val pnl = if (s.pnlKopecks == 0L) FosFormatter.amount(0L, sym) else FosFormatter.signedAmount(s.pnlKopecks, sym)
            Text(
                "Результат $pnl$pct",
                style = FosType.SmallBold,
                color = pnlColor(s.pnlKopecks),
            )
        }
        Text(
            "Стоимость — по цене вашей последней сделки, без комиссий",
            style = FosType.Micro,
            color = FosColors.TextMuted,
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
                    .clip(RoundedCornerShape(FosDimens.RadiusChip))
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
private fun MovementRow(e: BrokerEvent, portfolio: Portfolio.Result) {
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
        modifier = Modifier.fillMaxWidth()
            .fosCard(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(
                "$sub · ${FosFormatter.dayLabel(e.timestamp)}",
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

@Composable
private fun BrokerCard(acc: Portfolio.BrokerAccount, contractCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Rail, FosTone.Invest, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(acc.broker, style = FosType.BodySemi, color = FosColors.TextPrimary)
            val sub = acc.contract?.let { "Счёт №$it" }
                ?: if (contractCount > 1) "$contractCount ${accountsWord(contractCount)}" else null
            sub?.let { Text(it, style = FosType.MicroNum, color = FosColors.TextSecondary) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                FosFormatter.amount(acc.cashKopecks, FosFormatter.currencySymbol(acc.currency)),
                style = FosType.SmallBold,
                color = FosColors.TextPrimary,
            )
            Text("свободно", style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}

@Composable
private fun PositionRow(p: Portfolio.Position) {
    val sym = FosFormatter.currencySymbol(p.currency)
    Row(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.ticker, style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(
                "${grouped(p.quantity)} шт · ср. ${price(p.avgPriceMicros)} $sym",
                style = FosType.MicroNum,
                color = FosColors.TextSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(FosFormatter.amount(p.valueKopecks, sym), style = FosType.SmallBold, color = FosColors.TextPrimary)
            Text(
                if (p.pnlKopecks == 0L) FosFormatter.amount(0L, sym) else FosFormatter.signedAmount(p.pnlKopecks, sym),
                style = FosType.MicroNum,
                color = pnlColor(p.pnlKopecks),
            )
        }
    }
}

@Composable
private fun OrderRow(o: BrokerOrder) {
    val sym = FosFormatter.currencySymbol(o.currency)
    val (label, color) = when (o.status) {
        OrderStatus.ACTIVE    -> "Активна"   to FosColors.Invest
        OrderStatus.FILLED    -> "Исполнена" to FosColors.TextPrimary
        OrderStatus.CANCELLED -> "Отменена"  to FosColors.TextMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .fosCard(FosCardStyle.Plain, FosTone.Neutral, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${o.ticker} · ${if (o.side == OrderSide.BUY) "покупка" else "продажа"}",
                style = FosType.BodySemi,
                color = if (o.status == OrderStatus.CANCELLED) FosColors.TextMuted else FosColors.TextPrimary,
            )
            Text(
                "${grouped(o.lots)} лот. по ${price(o.priceMicros)} $sym · ${FosFormatter.dayLabel(o.timestamp)}",
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
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ── Форматирование ───────────────────────────────────────────────────────────

private fun accountsWord(n: Int): String {
    val m100 = n % 100; val m10 = n % 10
    return when {
        m100 in 11..14 -> "счетов"
        m10 == 1       -> "счёт"
        m10 in 2..4    -> "счёта"
        else           -> "счетов"
    }
}

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
