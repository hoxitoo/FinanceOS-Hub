package com.financeos.hub.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.calendar.CalendarEvent
import com.financeos.hub.core.calendar.EventConfidence
import com.financeos.hub.core.calendar.EventDirection
import com.financeos.hub.core.calendar.EventKind
import com.financeos.hub.core.calendar.FreeMoney
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import com.financeos.hub.features.dashboard.accountSheetFieldColors
import com.financeos.hub.ui.components.FosExplain
import com.financeos.hub.ui.components.FosHairline
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.components.ParticleLayer
import com.financeos.hub.ui.components.PawParticleLayer
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosCardSurface
import com.financeos.hub.ui.theme.fosHeroCard
import com.financeos.hub.ui.theme.fosInset
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Календарь платежей: что уже обещано другим и сколько после этого остаётся.
 *
 * Экран отвечает на вопрос, которого в приложении до сих пор не было. Остаток говорит «сколько у
 * меня есть», прогноз — «сколько я потрачу к концу месяца». Ни то, ни другое не годится в магазине:
 * чтобы решить, можно ли купить, надо знать, что уже занято.
 *
 * Арифметика показана построчно и НЕ спрятана за «?». Проза объясняет метод, а четыре строки,
 * которые складываются в заголовок, дают его проверить — это разные вещи, и вторая надёжнее.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit = {},
    vm    : CalendarViewModel = hiltViewModel(),
) {
    val state   by vm.state.collectAsState()
    val shimmer = LocalShimmer.current

    var editing     by remember { mutableStateOf<PlannedPaymentEntity?>(null) }
    var reserveOpen by remember { mutableStateOf(false) }
    var sheetOpen  by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(FosColors.Background)) {
        // Кот-режим не наследуется экранами сам: он читается через LocalShimmer в каждом отдельно.
        // Без этих трёх строк кота на календаре просто не было бы.
        if (shimmer.catPawParticles) {
            PawParticleLayer(count = 12, animated = shimmer.catParticlePulse, modifier = Modifier.matchParentSize())
        } else if (shimmer.particles) {
            ParticleLayer(count = 20, animated = shimmer.particlePulse, modifier = Modifier.matchParentSize())
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Календарь", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    TextButton(onClick = onBack) {
                        Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                    }
                }
            }

            state.free?.let { free ->
                item { FreeMoneyCard(free) { reserveOpen = true } }
                item { Timeline(state.upcoming, state.today, free.horizon) }
            }

            if (state.upcoming.isNotEmpty()) {
                val byDate = state.upcoming.groupBy { it.date }
                byDate.forEach { (date, events) ->
                    item(key = "h_$date") {
                        FosSectionHeader(
                            title = dayHeader(date, state.today).uppercase(),
                            tone  = if (date.isBefore(state.today)) FosTone.Negative else FosTone.Neutral,
                        )
                    }
                    items(events, key = { it.id }) { event ->
                        // Открыть на правку можно только объявленное: кредитку и подписку
                        // приложение выводит само, и «править» там нечего.
                        val editable = state.planned.firstOrNull {
                            event.kind == EventKind.PLANNED && it.id == event.sourceId
                        }
                        EventRow(event, onClick = editable?.let { p -> { editing = p; sheetOpen = true } })
                    }
                }
            } else if (!state.isLoading) {
                item {
                    Column(
                        modifier            = Modifier.fillMaxWidth().fosCard(FosCardStyle.Outline, FosTone.Info),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Обязательств не запланировано", style = FosType.BodySemi, color = FosColors.TextPrimary)
                        Text(
                            "Пока календарь пуст, «Свободно» — это просто остаток за вычетом резерва. " +
                                "Добавьте аренду и подтвердите найденные подписки — тогда число начнёт " +
                                "показывать то, ради чего оно нужно.",
                            style = FosType.Micro,
                            color = FosColors.TextSecondary,
                        )
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                item { FosSectionHeader("ПОХОЖЕ НА РЕГУЛЯРНЫЙ ПЛАТЁЖ", tone = FosTone.Info) }
                items(state.suggestions, key = { "s_${it.key}" }) { sub ->
                    SuggestionRow(
                        title    = sub.title,
                        amount   = FosFormatter.compact(sub.typicalKopecks, FosFormatter.currencySymbol(sub.currency)),
                        subtitle = sub.period?.label ?: "промежуток пока неизвестен",
                        onAdd    = { vm.confirmSubscription(sub) },
                    )
                }
            }

            if (state.settled.isNotEmpty()) {
                item { FosSectionHeader("УЖЕ ПРОШЛО") }
                items(state.settled, key = { "d_${it.id}" }) { event ->
                    SettledRow(event) { event.sourceId?.let(vm::unmatch) }
                }
            }

            if (!state.isLoading) {
                item {
                    FosExplain(
                        text = "«Свободно» — это деньги на счетах минус ещё не оплаченные обязательства " +
                            "до горизонта минус резерв.\n\n" +
                            "Считаются только ваши счета: кредитный лимит — деньги банка, а не ваши.\n\n" +
                            "Ожидаемые поступления показаны отдельной строкой, но НЕ прибавляются: " +
                            "незаработанная зарплата не делает деньги тратимыми, а этот экран нужен " +
                            "ровно чтобы не было перерасхода.\n\n" +
                            "Не всё в календаре двигает деньги. Конец беспроцентного периода и срок " +
                            "цели — это даты, а не списания, и из «Свободно» они не вычитаются.\n\n" +
                            "Горизонт — до ближайшего ожидаемого поступления; если его нет, до конца " +
                            "месяца.\n\n" +
                            "Валюты не складываются: курса у приложения нет, оно работает без сети.",
                        label = "как считается «Свободно»",
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick        = { editing = null; sheetOpen = true },
            containerColor = FosColors.Positive,
            contentColor   = FosColors.Background,
            shape          = CircleShape,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(FosDimens.ScreenPadding)
                .size(56.dp),
        ) {
            Text("+", style = FosType.ScreenTitle, color = FosColors.Background)
        }
    }

    if (reserveOpen) {
        ReserveDialog(
            current   = state.free?.reserveKopecks ?: 0L,
            onDismiss = { reserveOpen = false },
            onSave    = { vm.setReserve(it); reserveOpen = false },
        )
    }

    if (sheetOpen) {
        AddPlannedPaymentSheet(
            sheetState = sheetState,
            accounts   = state.accounts,
            existing   = editing,
            onDismiss  = { sheetOpen = false; editing = null },
            onSave     = vm::save,
            onDelete   = vm::deactivate,
        )
    }
}

// ── Герой ────────────────────────────────────────────────────────────────────

@Composable
private fun FreeMoneyCard(free: FreeMoney.FreeMoneyBreakdown, onEditReserve: () -> Unit) {
    val tone = if (free.freeKopecks >= 0) FosTone.Positive else FosTone.Negative
    val accent = tone.accent ?: FosColors.TextPrimary

    Column(
        modifier            = Modifier.fillMaxWidth().fosHeroCard(tone),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "СВОБОДНО ДО ${FosFormatter.date(free.horizon).uppercase()}",
            style = FosType.SectionCap,
            color = FosColors.TextMuted,
        )
        Text(FosFormatter.amount(free.freeKopecks), style = FosType.HeroAmountMulti, color = accent)

        free.dailyAllowanceKopecks?.let { perDay ->
            Text(
                "≈ ${FosFormatter.compact(perDay)} в день на ${pluralDays(free.daysLeft)}",
                style = FosType.MicroNum,
                color = FosColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(2.dp))
        FosHairline()

        // Арифметика на виду. Четыре строки, которые складываются в заголовок, позволяют его
        // проверить — в отличие от объяснения словами.
        Line("Деньги на счетах", FosFormatter.amount(free.onAccountsKopecks), FosColors.TextPrimary)
        if (free.obligationsKopecks > 0) {
            Line(
                "Обязательства до ${FosFormatter.date(free.horizon)}",
                "−" + FosFormatter.amount(free.obligationsKopecks),
                FosColors.Negative,
            )
        }
        // Резерв правится прямо здесь, а не в настройках: он не значит ничего в отрыве от числа,
        // на которое влияет, и менять его хочется ровно в тот момент, когда смотришь на это число.
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onEditReserve() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                if (free.reserveKopecks > 0) "Резерв" else "Резерв не задан",
                style = FosType.Body,
                color = FosColors.TextSecondary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (free.reserveKopecks > 0) "−" + FosFormatter.amount(free.reserveKopecks) else "указать",
                style = FosType.SmallBold,
                color = if (free.reserveKopecks > 0) FosColors.TextSecondary else FosColors.Info,
            )
        }

        if (free.expectedIncomeKopecks > 0) {
            Text(
                "Ждём поступлений на ${FosFormatter.compact(free.expectedIncomeKopecks)} — " +
                    "в расчёт не входят",
                style = FosType.MicroNum,
                color = FosColors.TextMuted,
            )
        }
        free.foreignObligations.forEach { (currency, amount) ->
            Text(
                "Плюс обязательства на ${FosFormatter.compact(amount, FosFormatter.currencySymbol(currency))} — " +
                    "другая валюта, в расчёт не входят",
                style = FosType.MicroNum,
                color = FosColors.TextMuted,
            )
        }
    }
}

// ── Полоса времени ───────────────────────────────────────────────────────────

/**
 * Полоса от сегодня до горизонта с точкой на каждое событие.
 *
 * Выведенное приложением событие рисуется ПОЛОЙ точкой, объявленное и присланное банком —
 * сплошной. Форма говорит «это оценка» раньше, чем взгляд дойдёт до подписи под списком.
 */
@Composable
private fun Timeline(events: List<CalendarEvent>, today: LocalDate, horizon: LocalDate) {
    if (events.isEmpty()) return
    val span = ChronoUnit.DAYS.between(today, horizon).coerceAtLeast(1L).toFloat()

    Column(
        modifier            = Modifier.fillMaxWidth().fosCard(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(22.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(1.dp))
                    .background(FosColors.Border)
            )
            events.forEach { event ->
                // Нижняя граница не нулевая: fillMaxWidth(0f) даёт коробку без ширины, и точка
                // сегодняшнего события уехала бы за левый край полосы.
                val offset = (ChronoUnit.DAYS.between(today, event.date).toFloat() / span)
                    .coerceIn(0.02f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(offset)
                        .height(22.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    EventDot(event)
                }
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("сегодня", style = FosType.Micro, color = FosColors.TextMuted)
            Text(FosFormatter.date(horizon), style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}

@Composable
private fun EventDot(event: CalendarEvent) {
    val color = kindColor(event)
    if (event.confidence == EventConfidence.INFERRED) {
        // Полая — «мы это вывели, а не узнали».
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(FosColors.Background)
                .border(1.5.dp, color, CircleShape)
        )
    } else {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
    }
}

// ── Строки ───────────────────────────────────────────────────────────────────

@Composable
private fun EventRow(event: CalendarEvent, onClick: (() -> Unit)? = null) {
    val tone = when {
        !event.affectsFree            -> FosTone.Info
        event.kind == EventKind.CREDIT_DUE -> FosTone.Warning
        else                          -> FosTone.Neutral
    }
    val style = if (tone == FosTone.Neutral) FosCardStyle.Plain else FosCardStyle.Rail
    val symbol = FosFormatter.currencySymbol(event.currency)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(style, tone, FosDimens.RadiusCardSmall)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(FosDimens.CardPaddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.title, style = FosType.BodySemi, color = FosColors.TextPrimary, maxLines = 1)
            Text(
                confidenceLabel(event),
                style = FosType.Micro,
                color = FosColors.TextMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (event.affectsFree) {
                    val sign = if (event.direction == EventDirection.OUT) "−" else "+"
                    sign + FosFormatter.compact(event.amountKopecks, symbol)
                } else {
                    // У срока сумма — справка, а не движение денег: без знака, приглушённо.
                    FosFormatter.compact(event.amountKopecks, symbol)
                },
                style = FosType.SmallBold,
                color = when {
                    !event.affectsFree                     -> FosColors.TextMuted
                    event.direction == EventDirection.IN   -> FosColors.Positive
                    else                                   -> FosColors.Negative
                },
            )
            if (!event.affectsFree) {
                Text("срок", style = FosType.Micro, color = FosColors.TextMuted)
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, amount: String, subtitle: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fosCardSurface(FosCardStyle.Outline, FosTone.Info, FosDimens.RadiusCardSmall)
            .padding(FosDimens.CardPaddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FosType.BodySemi, color = FosColors.TextPrimary, maxLines = 1)
            Text("$amount · $subtitle", style = FosType.MicroNum, color = FosColors.TextSecondary, maxLines = 1)
        }
        TextButton(onClick = onAdd) {
            Text("В календарь", style = FosType.Label, color = FosColors.Info)
        }
    }
}

@Composable
private fun SettledRow(event: CalendarEvent, onUnmatch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().fosInset(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.title, style = FosType.Body, color = FosColors.TextSecondary, maxLines = 1)
            Text(
                "закрыто · ${FosFormatter.date(event.date)}",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }
        TextButton(onClick = onUnmatch) {
            Text("Отвязать", style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = FosType.Body, color = FosColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(value, style = FosType.SmallBold, color = color, textAlign = TextAlign.End)
    }
}

// ── Подписи ──────────────────────────────────────────────────────────────────

private fun kindColor(event: CalendarEvent): Color = when {
    !event.affectsFree                      -> FosColors.Info
    event.kind == EventKind.CREDIT_DUE      -> FosColors.Warning
    event.direction == EventDirection.IN    -> FosColors.Positive
    else                                    -> FosColors.Negative
}

private fun confidenceLabel(event: CalendarEvent): String = when (event.confidence) {
    EventConfidence.BANK     -> "банк прислал"
    EventConfidence.DECLARED -> "вы указали"
    EventConfidence.INFERRED -> when (event.kind) {
        EventKind.SUBSCRIPTION -> "оценка по вашим списаниям"
        EventKind.CREDIT_GRACE -> "оценка по самой старой непогашенной покупке"
        else                   -> "расчёт по условиям карты"
    }
}

internal fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today               -> "Сегодня"
    today.plusDays(1)   -> "Завтра"
    else                -> if (date.isBefore(today)) "Просрочено · ${FosFormatter.date(date)}"
                           else FosFormatter.date(date)
}

private fun pluralDays(n: Int): String {
    val mod100 = n % 100
    val mod10  = n % 10
    return when {
        mod100 in 11..14 -> "$n дней"
        mod10 == 1       -> "$n день"
        mod10 in 2..4    -> "$n дня"
        else             -> "$n дней"
    }
}


/**
 * Неприкосновенный остаток.
 *
 * Это не цель: цель копят, чтобы однажды потратить, а резерв — пол, который не пробивают. По
 * умолчанию ноль: приложение не решает за человека, сколько ему нужно на чёрный день.
 */
@Composable
private fun ReserveDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(FosFormatter.plainAmountInput(current)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = FosColors.Surface,
        title = { Text("Резерв", style = FosType.BodySemi, color = FosColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Сумма, ниже которой «Свободно» опускаться не должно. Она вычитается из расчёта.",
                    style = FosType.Micro,
                    color = FosColors.TextSecondary,
                )
                OutlinedTextField(
                    value                = text,
                    onValueChange        = { text = FosFormatter.sanitizeAmountInput(it) },
                    visualTransformation = AmountVisualTransformation,
                    label                = { Text("Сумма, ₽", style = FosType.Label) },
                    singleLine           = true,
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors               = accountSheetFieldColors(),
                    modifier             = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(FosFormatter.parseAmountInput(text) ?: 0L) }) {
                Text("Сохранить", color = FosColors.Info)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = FosColors.TextSecondary) }
        },
    )
}
