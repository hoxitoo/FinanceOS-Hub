package com.financeos.hub.features.credit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

/**
 * Everything about the user's credit cards, one level below the dashboard tile: what is owed, by
 * when, at what rate, and what has been spent on the cards.
 *
 * Deliberately absent: a «Погасить» button and any interest/переплата figure. Repayment needs the
 * transfer plumbing that books it against the card instead of as an expense, and an overpayment
 * estimate needs an interest model — both are separate pieces of work, and a button that silently
 * does nothing is worse than no button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardsScreen(
    onBack: () -> Unit = {},
    vm    : CreditCardsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(FosColors.Background),
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
                Text("Кредитные карты", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                TextButton(onClick = onBack) {
                    Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                }
            }
        }

        if (!state.isLoading && state.cards.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Кредитных карт пока нет.\nДобавьте счёт типа «Кредитная карта» на главной.",
                        style     = FosType.Body,
                        color     = FosColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (state.cards.isNotEmpty()) {
            item { CreditTotals(state) }

            state.cards.forEach { card ->
                item(key = "cap_${card.account.id}") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append(card.account.bank)
                            val masks = (listOfNotNull(card.account.cardMask) + card.cards.map { it.cardMask }).distinct()
                            if (masks.isNotEmpty()) append(" ··${masks.first()}")
                        },
                        style = FosType.SectionCap,
                        color = FosColors.TextMuted,
                    )
                }
                item(key = "card_${card.account.id}") {
                    CreditCardBlock(card = card, onEditTerms = { editing = card.account })
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Spacer(Modifier.height(FosDimens.ItemGap))
                Text("Операции по кредиткам", style = FosType.SectionCap, color = FosColors.TextMuted)
            }
            items(state.history, key = { it.id }) { tx ->
                TransactionRow(transaction = tx, categoryName = state.categoryName(tx.categoryId))
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    editing?.let { account ->
        CreditTermsSheet(
            account    = account,
            sheetState = editSheetState,
            onDismiss  = { editing = null },
            onSave     = { debt, limit, aprBp, statementDay, dueDays, minBp ->
                vm.saveCard(account, debt, limit, aprBp, statementDay, dueDays, minBp)
            },
        )
    }
}

/** Free limit and total debt across every card, plus how much of the total line is in use. */
@Composable
private fun CreditTotals(state: CreditScreenState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column {
                Text("Свободный лимит", style = FosType.Micro, color = FosColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.totalLimit > 0) FosFormatter.amount(state.totalFree) else "—",
                    style = FosType.HeroAmountMulti,
                    color = FosColors.TextPrimary,
                )
            }
            Spacer(Modifier.width(FosDimens.CardGap))
            Column {
                Text("Долг", style = FosType.Micro, color = FosColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    FosFormatter.amount(state.totalDebt),
                    style = FosType.HeroAmountMulti,
                    color = if (state.totalDebt > 0) FosColors.Negative else FosColors.TextPrimary,
                )
            }
        }

        state.utilization?.let { used ->
            Spacer(Modifier.height(12.dp))
            UtilizationBar(used)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Использовано ${FosFormatter.percent(used)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
                Text(
                    "Лимит ${FosFormatter.amount(state.totalLimit)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun CreditCardBlock(card: CreditCardState, onEditTerms: () -> Unit) {
    val urgency = dueUrgency(card.cycle?.daysUntilDue)
    val accent  = dueUrgencyColor(urgency)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .border(
                1.dp,
                if (urgency == DueUrgency.OVERDUE || urgency == DueUrgency.CRITICAL)
                    accent.copy(alpha = 0.35f) else FosColors.Border,
                RoundedCornerShape(FosDimens.RadiusCard),
            )
            .padding(FosDimens.CardPadding),
    ) {
        Text(card.account.name, style = FosType.BodySemi, color = FosColors.TextPrimary)
        Spacer(Modifier.height(10.dp))

        val cycle = card.cycle
        if (cycle == null) {
            // No statement day / days-to-pay entered: state that plainly instead of showing a
            // made-up deadline. The debt itself is still known and worth showing.
            Text("Долг", style = FosType.Micro, color = FosColors.TextSecondary)
            Text(
                FosFormatter.amount(card.debt),
                style = FosType.HeroMinimal,
                color = if (card.debt > 0) FosColors.Negative else FosColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Дата платежа не рассчитана: не заданы день выписки и число дней на оплату.",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        } else {
            Text(
                "Внести до ${cycle.dueDate.format(DUE_DATE_FORMAT)}",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FosFormatter.amount(card.statementDebt),
                    style = FosType.HeroMinimal,
                    color = FosColors.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                dueLabel(cycle.daysUntilDue)?.let { label ->
                    Text(
                        text     = label,
                        style    = FosType.Micro,
                        color    = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(FosDimens.RadiusChip))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            card.minPayment?.let { min ->
                Spacer(Modifier.height(2.dp))
                Text(
                    "минимум ${FosFormatter.amount(min)}",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
            Spacer(Modifier.height(12.dp))
            GraceTimeline(card)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = FosColors.Border)

        card.aprPercent?.let { apr ->
            TermRow("Ставка", "${FosFormatter.percent(apr / 100.0, decimals = 1)} годовых")
        }
        card.freeLimit?.let { free ->
            TermRow("Свободный лимит", FosFormatter.amount(free))
        }
        card.utilization?.let { used ->
            TermRow("Использовано", FosFormatter.percent(used))
        }
        if (card.spentSinceStatement > 0) {
            TermRow(
                "Потрачено после выписки",
                FosFormatter.amount(card.spentSinceStatement),
                hint = "войдёт в следующую выписку",
            )
        }

        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick        = onEditTerms,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                if (card.termsMissing) "Заполнить условия карты" else "Изменить условия",
                style = FosType.Label,
                color = FosColors.Info,
            )
        }
    }
}

/**
 * Where we are between the statement close and the payment deadline. The filled part is time
 * already spent, so a nearly-full bar means the deadline is nearly here.
 */
@Composable
private fun GraceTimeline(card: CreditCardState) {
    val cycle   = card.cycle ?: return
    val urgency = dueUrgency(cycle.daysUntilDue)
    val accent  = dueUrgencyColor(urgency)

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(FosColors.Surface2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(cycle.windowProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "выписка ${cycle.statementDate.format(DUE_DATE_FORMAT)}",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
            Text(
                "оплата до ${cycle.dueDate.format(DUE_DATE_FORMAT)}",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }
    }
}

@Composable
private fun UtilizationBar(used: Float) {
    // Amber past 70%: high utilisation is a warning sign, not an error — Negative is reserved for
    // overrun, which on a credit line means a missed payment, not a heavily used limit.
    val color = if (used >= 0.70f) FosColors.Warning else FosColors.Info
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(FosColors.Surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(used.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
private fun TermRow(label: String, value: String, hint: String? = null) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Text(label, style = FosType.Micro, color = FosColors.TextSecondary)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = FosType.Label, color = FosColors.TextPrimary)
            if (hint != null) Text(hint, style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}
