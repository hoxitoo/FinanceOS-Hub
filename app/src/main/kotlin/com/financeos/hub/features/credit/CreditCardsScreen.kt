package com.financeos.hub.features.credit

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.financeos.hub.core.credit.MinimumPaymentOutlook
import com.financeos.hub.core.credit.PaymentSource
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.components.FosSectionHeader
import com.financeos.hub.ui.theme.FosCardStyle
import com.financeos.hub.ui.theme.FosTone
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.fosCard
import com.financeos.hub.ui.theme.fosHeroCard
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

/**
 * Everything about the user's credit cards, one level below the dashboard tile: what is owed, by
 * when, at what rate, and what has been spent on the cards.
 *
 * «Погасить» records a repayment as a TRANSFER off one of your own accounts — never an expense,
 * which would count the same money twice.
 *
 * The overpayment figures are ESTIMATES and are labelled as such on screen: a bank accrues daily
 * under tariff rules this app cannot see, so they will not match its app to the kopeck. The one
 * exact figure is the zero — inside the interest-free period, paid on time, the cost really is nil.
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
    var repaying by remember { mutableStateOf<String?>(null) }
    val repaySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    CreditCardBlock(
                        card        = card,
                        onEditTerms = { editing = card.account },
                        onRepay     = { repaying = card.account.id },
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Spacer(Modifier.height(FosDimens.ItemGap))
                FosSectionHeader("ОПЕРАЦИИ ПО КРЕДИТКАМ")
            }
            items(state.history, key = { it.id }) { tx ->
                TransactionRow(transaction = tx, categoryName = state.categoryName(tx.categoryId))
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // Look the card up by id rather than capturing it: the sheet must follow the live state, so
    // the debt it shows updates the moment a repayment lands.
    repaying?.let { id ->
        state.cards.firstOrNull { it.account.id == id }?.let { card ->
            RepaySheet(
                card       = card,
                payFrom    = state.payFrom,
                sheetState = repaySheetState,
                onDismiss  = { repaying = null },
                onConfirm  = { sourceId, amount -> vm.repay(card.account.id, sourceId, amount) },
            )
        }
    }

    editing?.let { account ->
        CreditTermsSheet(
            account    = account,
            sheetState = editSheetState,
            onDismiss  = { editing = null },
            onSave     = { debt, terms -> vm.saveCard(account, debt, terms) },
        )
    }
}

/** Free limit and total debt across every card, plus how much of the total line is in use. */
@Composable
private fun CreditTotals(state: CreditScreenState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fosHeroCard(),
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
                    "Использовано ${FosFormatter.percent(used.toDouble())}",
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
private fun CreditCardBlock(
    card       : CreditCardState,
    onEditTerms: () -> Unit,
    onRepay    : () -> Unit,
) {
    val urgency = dueUrgency(card.duePayment?.daysUntilDue)
    val accent  = dueUrgencyColor(urgency)

    // Огранка карточки = срочность платежа. Просрочка и «сегодня-завтра» получают красную полосу,
    // приближающийся срок — жёлтую, спокойная карта остаётся обычной.
    val tone = when (urgency) {
        DueUrgency.OVERDUE, DueUrgency.CRITICAL -> FosTone.Negative
        DueUrgency.SOON                         -> FosTone.Warning
        else                                    -> FosTone.Neutral
    }
    val style = if (tone == FosTone.Neutral) FosCardStyle.Plain else FosCardStyle.Rail

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fosCard(style, tone),
    ) {
        Text(card.account.name, style = FosType.BodySemi, color = FosColors.TextPrimary)
        Spacer(Modifier.height(10.dp))

        val due = card.duePayment
        if (due == null) {
            // Neither a bank reminder nor entered terms: state that plainly instead of showing a
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
                "Внести до ${due.dueDate.format(DUE_DATE_FORMAT)}",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FosFormatter.amount(due.amountKopecks),
                    style = FosType.HeroMinimal,
                    color = FosColors.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                dueLabel(due.daysUntilDue)?.let { label ->
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
            // Never let an inferred number pose as the bank's. The two differ in how much they can
            // be trusted, and the user is the one who has to decide whether to act on it.
            Spacer(Modifier.height(2.dp))
            Text(
                when (due.source) {
                    PaymentSource.BANK     -> "сумма и дата — из уведомления банка"
                    PaymentSource.INFERRED -> "расчёт по вашим условиям карты, банк это не подтверждал"
                },
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
            if (due.source == PaymentSource.INFERRED) {
                card.minPayment?.let { min ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "минимум ${FosFormatter.amount(min)}",
                        style = FosType.Micro,
                        color = FosColors.TextMuted,
                    )
                }
            }
            if (card.cycle != null) {
                Spacer(Modifier.height(12.dp))
                Text("Беспроцентный период", style = FosType.Micro, color = FosColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                InterestFreeTimeline(card)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = FosColors.Border)

        card.aprPercent?.let { apr ->
            TermRow("Ставка", "${FosFormatter.percent(apr / 100.0, decimals = 1)} годовых")
        }
        card.account.interestFreeDays?.let { days ->
            TermRow(
                "Беспроцентный период",
                pluralDays(days),
                hint = "на покупки, от даты покупки",
            )
        }
        card.account.penaltyAprBp?.takeIf { it > 0 }?.let { bp ->
            TermRow(
                "Неустойка",
                "${FosFormatter.percent(bp / 10_000.0, decimals = 1)} годовых",
                hint = "если пропустить обязательный платёж",
                valueColor = FosColors.Warning,
            )
        }
        card.freeLimit?.let { free ->
            TermRow("Свободный лимит", FosFormatter.amount(free))
        }
        card.utilization?.let { used ->
            TermRow("Использовано", FosFormatter.percent(used.toDouble()))
        }
        if (card.spentSinceStatement > 0) {
            TermRow(
                "Потрачено после выписки",
                FosFormatter.amount(card.spentSinceStatement),
                hint = "войдёт в следующую выписку",
            )
        }

        InterestBlock(card)

        // A cash withdrawal is the one move that quietly costs the most on a credit card: the fee
        // is charged up front AND the interest-free period usually does not apply to it at all.
        card.account.cashFeeBp?.takeIf { it > 0 }?.let { bp ->
            val fixed = card.account.cashFeeFixedKopecks ?: 0L
            TermRow(
                "Снятие наличных",
                buildString {
                    append(FosFormatter.percent(bp / 10_000.0, decimals = 1))
                    if (fixed > 0) append(" + ${FosFormatter.amount(fixed)}")
                },
                hint = "и обычно без беспроцентного периода",
                valueColor = FosColors.Warning,
            )
        }

        Spacer(Modifier.height(10.dp))
        // Only offered when there is something to pay off — a settled card showing «Погасить»
        // invites a repayment that would push the balance into a meaningless positive.
        if (card.debt > 0) {
            Button(
                onClick  = onRepay,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(FosDimens.RadiusCardSmall),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = FosColors.Positive,
                    contentColor   = FosColors.Background,
                ),
            ) {
                Text("Погасить", style = FosType.BodySemi)
            }
        }
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
 * Where we are between the statement close and the payment deadline — the stretch during which the
 * debt costs nothing if it gets paid. The filled part is time already spent, so a nearly-full bar
 * means the deadline is nearly here.
 */
@Composable
private fun InterestFreeTimeline(card: CreditCardState) {
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

/**
 * What the debt costs. Two figures with very different standing, so they are never blurred:
 *
 *  - «Переплата сейчас» is EXACT at zero — inside the interest-free period, paid on time, the card
 *    costs nothing, and that is a fact rather than a guess. Once the deadline passes it becomes an
 *    estimate like everything else.
 *  - «Если платить только минимум» is unavoidably an estimate, and says so. It is also the number
 *    that makes a credit card's real price visible, which is why it is worth showing at all.
 *
 * Hidden entirely without a rate: an interest figure invented from no rate would be pure fiction.
 */
@Composable
private fun InterestBlock(card: CreditCardState) {
    val interest = card.interestSoFar ?: return
    // Without a known deadline there is no interest-free period to be inside, so «Переплата сейчас
    // 0 ₽ — вы в беспроцентном периоде» would be a confident falsehood on a card that may be months
    // late. The minimum-payment outlook does not depend on a date, so it still stands.
    val due      = card.duePayment
    val overdue  = (due?.daysUntilDue ?: 0) < 0

    if (due != null) {
        TermRow(
            label = "Переплата сейчас",
            value = if (interest > 0) "≈ ${FosFormatter.amount(interest)}" else FosFormatter.amount(0L),
            hint  = if (overdue) "срок прошёл, проценты идут" else "вы в беспроцентном периоде",
            valueColor = if (interest > 0) FosColors.Negative else FosColors.TextPrimary,
        )
    }

    when (val outlook = card.minimumOutlook) {
        is MinimumPaymentOutlook.PaysOff -> if (outlook.months > 0) {
            TermRow(
                label = "Если платить только минимум",
                value = "≈ ${FosFormatter.amount(outlook.totalInterestKopecks)}",
                hint  = "за ${pluralMonths(outlook.months)} · оценка",
                valueColor = FosColors.Warning,
            )
        }
        MinimumPaymentOutlook.NeverPaysOff -> TermRow(
            label = "Если платить только минимум",
            value = "долг не уменьшится",
            hint  = "минимальный платёж не покрывает проценты",
            valueColor = FosColors.Negative,
        )
        null -> Unit   // no rate or no minimum % entered — say nothing rather than guess
    }
}

/** «1 месяц» / «3 месяца» / «14 месяцев». */
private fun pluralMonths(n: Int): String {
    val word = when {
        n % 10 == 1 && n % 100 != 11         -> "месяц"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "месяца"
        else                                 -> "месяцев"
    }
    return "$n $word"
}

@Composable
private fun TermRow(
    label     : String,
    value     : String,
    hint      : String? = null,
    valueColor: androidx.compose.ui.graphics.Color = FosColors.TextPrimary,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Text(label, style = FosType.Micro, color = FosColors.TextSecondary)
        Column(horizontalAlignment = Alignment.End) {
            // SmallBold carries fontFeatureSettings = "tnum"; Label does not, and these
            // values are money (Critical Design Rule #3).
            Text(value, style = FosType.SmallBold, color = valueColor)
            if (hint != null) Text(hint, style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}
