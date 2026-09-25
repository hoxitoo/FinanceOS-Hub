package com.financeos.hub.features.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.GoalEntity
import com.financeos.hub.core.finance.GoalPlan
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.bankBrand

/**
 * Иконки цели, разложенные по смыслу.
 *
 * Раньше это была одна бесконечная лента вбок: чтобы узнать, есть ли вообще нужная иконка, её
 * приходилось долистывать до конца, а что осталось за краем — не видно никак. Разложенные по
 * категориям, они все на экране сразу, и выбор становится узнаванием, а не поиском.
 *
 * Состав каждой группы согласован с `goalArtFor` (`ui/components/GoalArt.kt`): иконка выбирает не
 * только глиф, но и подложку карточки, поэтому новая иконка без темы молча получила бы подложку
 * «покупки».
 */
internal data class IconGroup(val title: String, val emojis: List<String>)

internal val ICON_GROUPS = listOf(
    IconGroup("Путешествия", listOf("✈", "🏖", "🗺")),
    IconGroup("Жильё",       listOf("🏠", "🛋", "🔑")),
    IconGroup("Транспорт",   listOf("🚗", "🏍", "🚲")),
    IconGroup("Техника",     listOf("📱", "💻", "🎸")),
    IconGroup("Образование", listOf("📚", "🎓", "🗣")),
    IconGroup("Здоровье",    listOf("💊", "🏋", "🦷")),
    IconGroup("Праздники",   listOf("🎁", "💍", "🎂")),
    IconGroup("Накопления",  listOf("💰", "⭐", "🐷")),
    IconGroup("Покупки",     listOf("🛍", "👟", "🛒")),
)

private val DEFAULT_EMOJI = ICON_GROUPS.first().emojis.first()

/**
 * Bottom sheet for creating OR editing a goal.
 * Pass [existing] to pre-fill the fields and switch to edit mode.
 *
 * [linkedAccountIds] — счета, уже привязанные к цели (маршруты `transfer_routes`). Форма правит их
 * наравне с остальными полями: до этого выбор счёта в режиме правки просто игнорировался при
 * сохранении, то есть элемент управления был, а действия за ним не было.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddGoalSheet(
    existing        : GoalEntity? = null,
    accounts        : List<AccountEntity> = emptyList(),
    linkedAccountIds: Set<String> = emptySet(),
    /** Собственный темп накопления, ₽/мес — для строки «вашим темпом» в расчёте. */
    paceKopecks     : Long? = null,
    onDismiss       : () -> Unit,
    onSave          : (
        name            : String,
        emoji           : String,
        targetKopecks   : Long,
        deadlineAt      : Long?,
        startedAt       : Long?,
        linkedAccountIds: Set<String>,
    ) -> Unit,
) {
    val editing = existing != null

    // Key on existing?.id so the form resets correctly when the sheet is reused for a
    // different goal (e.g. edit goal A → dismiss → edit goal B) while still in composition.
    var name          by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var targetDigits  by remember(existing?.id) { mutableStateOf(existing?.let { (it.targetKopecks / 100).toString() } ?: "") }
    var selectedEmoji by remember(existing?.id) { mutableStateOf(existing?.emoji ?: DEFAULT_EMOJI) }
    var deadline      by remember(existing?.id) { mutableStateOf(existing?.deadlineAt) }
    // Новой цели начало подставляется сегодняшним днём: «начал копить сегодня» — верно почти
    // всегда, а поправить одним нажатием проще, чем вспомнить заполнить пустое поле.
    var startedAt     by remember(existing?.id) {
        mutableStateOf(existing?.startedAt ?: if (existing == null) System.currentTimeMillis() else null)
    }
    // Ключ обязателен (инвариант #4): без него лист, переоткрытый для ДРУГОЙ цели, показывал бы
    // привязки предыдущей — и сохранил бы их.
    var picked        by remember(existing?.id) { mutableStateOf(linkedAccountIds) }
    var datePicking   by remember { mutableStateOf<DateField?>(null) }

    val targetKopecks = (targetDigits.toLongOrNull() ?: 0L) * 100L
    val canSave = name.isNotBlank() && targetKopecks > 0

    // Для правки «грязным» считается отличие от сохранённой цели, для новой — любой ввод. Эмодзи
    // сравнивается с первым в списке: он подставлен за человека и сам по себе выбором не является.
    // Начало у новой цели тоже подставлено, поэтому в «грязность» оно идёт только при правке.
    val dirty = {
        if (existing == null) {
            name.isNotBlank() || targetDigits.isNotBlank() || deadline != null ||
                selectedEmoji != DEFAULT_EMOJI || picked.isNotEmpty()
        } else {
            name != existing.name ||
                targetDigits != (existing.targetKopecks / 100).toString() ||
                selectedEmoji != existing.emoji ||
                deadline != existing.deadlineAt ||
                startedAt != existing.startedAt ||
                picked != linkedAccountIds
        }
    }

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = dirty,
    ) {
        Text(
            if (editing) "Редактировать цель" else "Новая цель",
            style = FosType.ScreenTitle,
            color = FosColors.TextPrimary,
        )

        // ── Иконка: категории в два столбца, без бокового скролла ────────────────
        FieldCaption("ИКОНКА")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Два столбца вместо одного списка: девять групп в один столбец занимают полтора
            // экрана, и поля формы уезжают за сгиб.
            listOf(0, 1).forEach { column ->
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ICON_GROUPS.filterIndexed { i, _ -> i % 2 == column }.forEach { group ->
                        Text(group.title, style = FosType.Micro, color = FosColors.TextMuted)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement   = Arrangement.spacedBy(6.dp),
                        ) {
                            group.emojis.forEach { emoji ->
                                val selected = emoji == selectedEmoji
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(FosDimens.RadiusIcon))
                                        .background(
                                            if (selected) FosColors.Positive.copy(alpha = 0.15f)
                                            else FosColors.Surface2
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) FosColors.Positive else FosColors.BorderStrong,
                                            RoundedCornerShape(FosDimens.RadiusIcon),
                                        )
                                        .clickable { selectedEmoji = emoji },
                                ) {
                                    Text(emoji, style = FosType.BodySemi)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Название и сумма ─────────────────────────────────────────────────────
        // Подпись вынесена НАД полем, а не плавает внутри рамкой: без встроенного label поле
        // становится на четверть ниже, а сама подпись читается всегда, а не только когда пусто.
        FieldCaption("НАЗВАНИЕ ЦЕЛИ")
        CompactField(
            value         = name,
            onValueChange = { name = it },
            placeholder   = "Например, Греция",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        FieldCaption("ЦЕЛЕВАЯ СУММА, ₽")
        CompactField(
            value         = FosFormatter.groupDigits(targetDigits),
            onValueChange = { input -> targetDigits = input.filter { it.isDigit() }.take(12) },
            placeholder   = "200 000",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction    = ImeAction.Done,
            ),
        )

        // ── Две даты в один ряд ──────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateCell(
                caption  = "НАЧАЛО",
                value    = startedAt,
                empty    = "Не указано",
                modifier = Modifier.weight(1f),
                onPick   = { datePicking = DateField.START },
                onClear  = { startedAt = null },
            )
            DateCell(
                caption  = "СРОК",
                value    = deadline,
                empty    = "Без срока",
                modifier = Modifier.weight(1f),
                onPick   = { datePicking = DateField.DEADLINE },
                onClear  = { deadline = null },
            )
        }

        // ── Расчёт ───────────────────────────────────────────────────────────────
        // Считается прямо здесь и пересчитывается на каждый ввод: вопрос «сколько откладывать»
        // возникает ровно в тот момент, когда ставишь сумму и срок, а не на отдельном экране,
        // куда надо ещё раз перенести те же цифры.
        val savedNow = existing?.savedKopecks ?: 0L
        if (targetKopecks > 0) {
            val plan = GoalPlan.outlook(
                savedKopecks  = savedNow,
                targetKopecks = targetKopecks,
                startedAt     = startedAt,
                deadlineAt    = deadline,
                paceKopecks   = paceKopecks,
            )
            GoalPlanBlock(plan, paceKopecks)
        }

        // ── Привязка счетов ──────────────────────────────────────────────────────
        if (accounts.isNotEmpty()) {
            FieldCaption("ПРИВЯЗАТЬ СЧЁТ")
            Text(
                "Цель будет следовать за деньгами на этом счёте: приход прибавляется, расход вычитается.",
                style = FosType.Micro,
                color = FosColors.TextSecondary,
            )
            GoalAccountPicker(
                accounts = accounts,
                picked   = picked,
                onToggle = { id -> picked = if (id in picked) picked - id else picked + id },
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick  = {
                onSave(name.trim(), selectedEmoji, targetKopecks, deadline, startedAt, picked)
                onDismiss()
            },
            enabled  = canSave,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(FosDimens.RadiusCard),
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
        ) {
            Text(if (editing) "Сохранить" else "Создать цель", style = FosType.BodySemi)
        }
    }

    datePicking?.let { field ->
        val initial = when (field) {
            DateField.START    -> startedAt
            DateField.DEADLINE -> deadline
        }
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = initial ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { datePicking = null },
            confirmButton = {
                TextButton(onClick = {
                    when (field) {
                        DateField.START    -> startedAt = dpState.selectedDateMillis
                        DateField.DEADLINE -> deadline  = dpState.selectedDateMillis
                    }
                    datePicking = null
                }) { Text("ОК", color = FosColors.Positive) }
            },
            dismissButton = {
                TextButton(onClick = { datePicking = null }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
        ) {
            DatePicker(state = dpState)
        }
    }
}

/** Какое из двух полей даты сейчас выбирают — одно состояние на один календарь. */
private enum class DateField { START, DEADLINE }

/**
 * Расчёт по цели: сколько осталось, сколько откладывать, успеваете ли своим темпом.
 *
 * Каждая строка появляется, только когда есть из чего её посчитать. Пустая заглушка вроде
 * «— ₽ в месяц» выглядит как сломанный расчёт, хотя на деле не хватает срока или истории.
 */
@Composable
private fun GoalPlanBlock(plan: GoalPlan.Outlook, paceKopecks: Long?) {
    FieldCaption("РАСЧЁТ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusButton))
            .background(FosColors.Surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (plan.remainingKopecks == 0L) {
            Text("Цель набрана", style = FosType.BodySemi, color = FosColors.Positive)
            return@Column
        }

        PlanLine("Осталось собрать", FosFormatter.amount(plan.remainingKopecks), FosColors.TextPrimary)

        when {
            plan.monthsLeft == null ->
                Text(
                    "Укажите срок — и здесь появится, сколько откладывать в месяц.",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            plan.monthsLeft <= 0 ->
                PlanLine(
                    "Срок уже наступил",
                    FosFormatter.amount(plan.requiredMonthly ?: plan.remainingKopecks),
                    FosColors.Warning,
                )
            else -> {
                PlanLine(
                    "Откладывать в месяц",
                    FosFormatter.amount(plan.requiredMonthly ?: 0L),
                    FosColors.Info,
                )
                PlanLine(
                    "Месяцев до срока",
                    plan.monthsLeft.toString(),
                    FosColors.TextSecondary,
                )
            }
        }

        // Собственный темп — не прогноз, а факт: средний остаток за три закрытых месяца.
        if (paceKopecks != null && paceKopecks > 0L) {
            plan.monthsAtCurrentPace?.let { months ->
                PlanLine(
                    "Вашим темпом (${FosFormatter.compact(paceKopecks)}/мес)",
                    "$months мес.",
                    if (plan.onTrack == false) FosColors.Warning else FosColors.TextSecondary,
                )
            }
            if (plan.onTrack == false) {
                Text(
                    "Текущего темпа к сроку не хватает.",
                    style = FosType.Micro,
                    color = FosColors.Warning,
                )
            }
        }

        plan.elapsedShare?.let { share ->
            Text(
                "Прошло ${(share * 100).toInt()} % срока",
                style = FosType.MicroNum,
                color = FosColors.TextMuted,
            )
        }

        Text(
            "Без учёта процентов: цель — копилка, а не вклад.",
            style = FosType.Micro,
            color = FosColors.TextMuted,
        )
    }
}

@Composable
private fun PlanLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = FosType.Micro, color = FosColors.TextSecondary)
        Text(value, style = FosType.SmallBold, color = valueColor)
    }
}

@Composable
private fun FieldCaption(text: String) {
    Text(text, style = FosType.SectionCap, color = FosColors.TextSecondary)
}

/**
 * Поле ввода без плавающей подписи.
 *
 * Высота задана явно: `OutlinedTextField` держит минимум 56 dp под подпись, которая здесь стоит
 * снаружи, и поля выходили заметно выше, чем им нужно. Рамка и фон ярче обычных — в форме это
 * главные элементы, а не фон под ними.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactField(
    value          : String,
    onValueChange  : (String) -> Unit,
    placeholder    : String,
    keyboardOptions: KeyboardOptions,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        placeholder     = { Text(placeholder, style = FosType.Body, color = FosColors.TextMuted) },
        singleLine      = true,
        textStyle       = FosType.Body,
        keyboardOptions = keyboardOptions,
        shape           = RoundedCornerShape(FosDimens.RadiusButton),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = FosColors.Info,
            unfocusedBorderColor    = FosColors.Info.copy(alpha = 0.40f),
            focusedContainerColor   = FosColors.Surface2,
            unfocusedContainerColor = FosColors.Surface2,
            cursorColor             = FosColors.Info,
            focusedTextColor        = FosColors.TextPrimary,
            unfocusedTextColor      = FosColors.TextPrimary,
            errorBorderColor        = FosColors.Negative,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    )
}

/** Ячейка даты: подпись сверху, значение внутри, крестик — когда есть что убирать. */
@Composable
private fun DateCell(
    caption : String,
    value   : Long?,
    empty   : String,
    modifier: Modifier = Modifier,
    onPick  : () -> Unit,
    onClear : () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldCaption(caption)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(FosDimens.RadiusButton))
                .background(FosColors.Surface2)
                .border(
                    1.dp,
                    if (value != null) FosColors.Info.copy(alpha = 0.40f) else FosColors.BorderStrong,
                    RoundedCornerShape(FosDimens.RadiusButton),
                )
                .clickable { onPick() }
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                value?.let { FosFormatter.dayLabelYear(it) } ?: empty,
                style    = FosType.Body,
                color    = if (value != null) FosColors.TextPrimary else FosColors.TextMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (value != null) {
                Text(
                    "×",
                    style    = FosType.BodySemi,
                    color    = FosColors.Negative,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * Выбор счетов: чипы банков, внутри чипа — его счета.
 *
 * Лента всех счетов вбок прятала и сам список, и сделанный выбор: привязанный счёт мог оказаться
 * за краем экрана, и цель выглядела непривязанной. Банк раскрывается нажатием, выбранные счета
 * пересчитаны в подписи, а выбор — множественный: у цели вполне может быть и накопительный счёт,
 * и карта, с которой на него переводят.
 */
@Composable
private fun GoalAccountPicker(
    accounts: List<AccountEntity>,
    picked  : Set<String>,
    onToggle: (String) -> Unit,
) {
    val banks = remember(accounts) { accounts.groupBy { it.bank }.toList() }
    var expandedBank by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        banks.forEach { (bank, bankAccounts) ->
            val brand      = bankBrand(bank)
            val isExpanded = expandedBank == bank
            val pickedHere = bankAccounts.count { it.id in picked }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                    .background(
                        if (pickedHere > 0) FosColors.Positive.copy(alpha = 0.10f) else FosColors.Surface2
                    )
                    .clickable { expandedBank = if (isExpanded) null else bank }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brand.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(bank.trim().take(1).uppercase(), style = FosType.SmallBold, color = brand.onBg)
                }
                Text(
                    bank,
                    style    = FosType.Body,
                    color    = FosColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // Свёрнутый чип обязан говорить, что внутри выбрано: иначе «привязать» выглядит
                // несделанным ровно после того, как его сделали.
                if (pickedHere > 0) {
                    Text("выбрано: $pickedHere", style = FosType.Micro, color = FosColors.Positive)
                }
                Text(if (isExpanded) "▲" else "▼", style = FosType.Micro, color = FosColors.TextMuted)
            }

            if (isExpanded) {
                bankAccounts.forEach { acc ->
                    val on = acc.id in picked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                            .background(
                                if (on) FosColors.Positive.copy(alpha = 0.16f) else FosColors.Surface
                            )
                            .clickable { onToggle(acc.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (on) "✓" else "+",
                            style = FosType.BodySemi,
                            color = if (on) FosColors.Positive else FosColors.TextMuted,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                acc.name,
                                style    = FosType.Body,
                                color    = if (on) FosColors.Positive else FosColors.TextPrimary,
                                maxLines = 1,
                            )
                            acc.cardMask?.let {
                                Text("•• $it", style = FosType.Micro, color = FosColors.TextMuted)
                            }
                        }
                        Text(
                            FosFormatter.compact(
                                acc.balanceKopecks,
                                FosFormatter.currencySymbol(acc.currency),
                            ),
                            style = FosType.MicroNum,
                            color = FosColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
