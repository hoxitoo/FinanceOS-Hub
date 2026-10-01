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
import com.financeos.hub.core.invest.BrokerOrder
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
    portfolio    : Portfolio.Result,
    isSample     : Boolean,
    brokerPackage: String?,
    onShowSample : () -> Unit,
    onHideSample : () -> Unit,
) {
    if (portfolio.isEmpty) {
        item(key = "invest_empty") { InvestorEmpty(brokerPackage, onShowSample) }
        item(key = "invest_bottom") { Spacer(Modifier.height(24.dp)) }
        return
    }

    item(key = "invest_hero") { PortfolioHero(portfolio, isSample, onHideSample) }

    if (portfolio.accounts.isNotEmpty()) {
        item(key = "invest_brokers_h") { FosSectionHeader("Брокеры", tone = FosTone.Invest) }
        items(portfolio.accounts, key = { "acc_${it.broker}_${it.currency}" }) { BrokerCard(it) }
    }
    if (portfolio.positions.isNotEmpty()) {
        item(key = "invest_positions_h") { FosSectionHeader("Позиции", tone = FosTone.Invest) }
        items(portfolio.positions, key = { "pos_${it.broker}_${it.ticker}_${it.currency}" }) { PositionRow(it) }
    }
    val orders = portfolio.activeOrders + portfolio.history
    if (orders.isNotEmpty()) {
        item(key = "invest_orders_h") { FosSectionHeader("Заявки и сделки", tone = FosTone.Invest) }
        // Индекс в ключе: один и тот же пуш может прийти дважды, и одинаковый ключ уронил бы список.
        itemsIndexed(orders, key = { i, o -> "ord_${i}_${o.timestamp}" }) { _, o -> OrderRow(o) }
    }
    item(key = "invest_bottom") { Spacer(Modifier.height(24.dp)) }
}

// ── Блоки ────────────────────────────────────────────────────────────────────

@Composable
private fun InvestorEmpty(brokerPackage: String?, onShowSample: () -> Unit) {
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
                "Приложение брокера найдено: $brokerPackage. Записывать его пуши начнём в следующем обновлении."
            } else {
                "Приложение брокера ещё не найдено. Как только придёт любой пуш БКС — пополнение или " +
                    "заявка, — приложение его узнает."
            },
            style = FosType.Micro,
            color = if (brokerPackage != null) FosColors.Invest else FosColors.TextMuted,
        )
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
private fun PortfolioHero(portfolio: Portfolio.Result, isSample: Boolean, onHideSample: () -> Unit) {
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

@Composable
private fun BrokerCard(acc: Portfolio.BrokerAccount) {
    Row(
        modifier = Modifier.fillMaxWidth().fosCard(FosCardStyle.Rail, FosTone.Invest, FosDimens.RadiusCardSmall, FosDimens.CardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(acc.broker, style = FosType.BodySemi, color = FosColors.TextPrimary)
            acc.contract?.let { Text("Счёт №$it", style = FosType.MicroNum, color = FosColors.TextSecondary) }
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
