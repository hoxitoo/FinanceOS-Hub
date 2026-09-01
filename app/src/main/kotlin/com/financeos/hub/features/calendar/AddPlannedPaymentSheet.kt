package com.financeos.hub.features.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeos.hub.core.database.entities.AccountEntity
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.core.database.entities.PaymentDirection
import com.financeos.hub.core.database.entities.PaymentSchedule
import com.financeos.hub.core.database.entities.PlannedPaymentEntity
import com.financeos.hub.features.dashboard.accountSheetFieldColors
import com.financeos.hub.ui.components.AccountPicker
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.components.SourceOption
import com.financeos.hub.ui.theme.AmountVisualTransformation
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Объявление обязательства: аренда, налог, разовый платёж, ожидаемое поступление.
 *
 * Это единственный способ положить в календарь то, чего приложение не может вывести само. Кредитку,
 * подписки и цели оно берёт из своих данных, а про аренду знает только человек.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPlannedPaymentSheet(
    accounts  : List<AccountEntity>,
    existing  : PlannedPaymentEntity? = null,
    onDismiss : () -> Unit,
    onSave    : (PlannedPaymentEntity) -> Unit,
    onDelete  : ((String) -> Unit)? = null,
) {
    val zone = remember { ZoneId.systemDefault() }

    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var amountText by remember(existing?.id) {
        mutableStateOf(existing?.let { FosFormatter.plainAmountInput(it.amountKopecks) }.orEmpty())
    }
    var direction by remember(existing?.id) {
        mutableStateOf(existing?.direction ?: PaymentDirection.OUT)
    }
    var schedule by remember(existing?.id) {
        mutableStateOf(existing?.schedule ?: PaymentSchedule.MONTHLY)
    }
    var date by remember(existing?.id) {
        mutableStateOf(
            existing?.anchorDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: LocalDate.now()
        )
    }
    var accountId by remember(existing?.id) { mutableStateOf(existing?.accountId) }
    var showPicker by remember { mutableStateOf(false) }

    val amountKopecks = FosFormatter.parseAmountInput(amountText) ?: 0L
    val canSave = title.isNotBlank() && amountKopecks > 0

    // «Есть что терять» = форма отличается от того, с чего начали. Для новой строки это любой
    // непустой ввод, для правки — отличие от сохранённого: закрывать открытую и не тронутую
    // карточку вопросом «выйти без сохранения?» значит приучить отвечать «да» не глядя.
    val dirty = {
        if (existing == null) {
            title.isNotBlank() || amountText.isNotBlank() || accountId != null
        } else {
            title.trim() != existing.title ||
                amountKopecks != existing.amountKopecks ||
                direction != existing.direction ||
                schedule != existing.schedule ||
                accountId != existing.accountId ||
                date != Instant.ofEpochMilli(existing.anchorDate).atZone(zone).toLocalDate()
        }
    }

    FosFormSheet(onDismiss = onDismiss, hasChanges = dirty) {
        Text(
            if (existing == null) "Новое обязательство" else "Обязательство",
            style = FosType.ScreenTitle,
            color = FosColors.TextPrimary,
        )

        OutlinedTextField(
            value         = title,
            onValueChange = { title = it },
            label         = { Text("Название", style = FosType.Label) },
            placeholder   = { Text("Аренда", style = FosType.Body, color = FosColors.TextMuted) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors        = accountSheetFieldColors(),
            modifier      = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value                = amountText,
            onValueChange        = { amountText = FosFormatter.sanitizeAmountInput(it) },
            visualTransformation  = AmountVisualTransformation,
            label                = { Text("Сумма, ₽", style = FosType.Label) },
            singleLine           = true,
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            colors               = accountSheetFieldColors(),
            modifier             = Modifier.fillMaxWidth(),
        )

        Text("Направление", style = FosType.SectionCap, color = FosColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DirectionChip("Платёж", direction == PaymentDirection.OUT, FosColors.Negative) {
                direction = PaymentDirection.OUT
            }
            // Ожидаемое поступление задаёт горизонт «до следующих денег». В «Свободно» оно
            // намеренно не прибавляется — см. пояснение на экране.
            DirectionChip("Поступление", direction == PaymentDirection.IN, FosColors.Positive) {
                direction = PaymentDirection.IN
            }
        }

        Text("Повторяется", style = FosType.SectionCap, color = FosColors.TextMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp),
            modifier              = Modifier.fillMaxWidth(),
        ) {
            PaymentSchedule.entries.forEach { s ->
                FilterChip(
                    selected = schedule == s,
                    onClick  = { schedule = s },
                    label    = { Text(scheduleLabel(s), style = FosType.Micro) },
                    shape    = RoundedCornerShape(FosDimens.RadiusChip),
                    colors   = chipColors(FosColors.Info),
                    border   = null,
                )
            }
        }

        Text(
            if (schedule == PaymentSchedule.ONCE) "Дата" else "Первый платёж",
            style = FosType.SectionCap,
            color = FosColors.TextMuted,
        )
        TextButton(
            onClick        = { showPicker = true },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) {
            Text(FosFormatter.date(date), style = FosType.BodySemi, color = FosColors.Info)
        }
        if (schedule != PaymentSchedule.ONCE && schedule != PaymentSchedule.WEEKLY) {
            Text(
                "Дальше — ${date.dayOfMonth}-го числа. В коротких месяцах платёж сдвинется на " +
                    "последний день и в следующем длинном вернётся на ${date.dayOfMonth}-е.",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        }

        val ownAccounts = remember(accounts) {
            accounts.filter { it.isActive && it.kind != AccountKind.CREDIT }
        }
        if (ownAccounts.isNotEmpty()) {
            Text(
                "Если указать счёт, обязательство закроется только операцией с него — " +
                    "сопоставление станет точнее. Не указывать тоже нормально.",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
            // Тот же выбор «банк → счёт», что и при ручном добавлении операции. Плоская лента
            // из всех счетов подряд читалась как однообразная простыня: имена вроде «текущий»
            // или «для vip» ничего не говорят, пока не видно, чей это банк и сколько там денег.
            val options = remember(ownAccounts) {
                ownAccounts.map { acc ->
                    SourceOption(
                        accountId      = acc.id,
                        accountName    = acc.name,
                        bank           = acc.bank,
                        mask           = acc.cardMask,
                        balanceKopecks = acc.balanceKopecks,
                        currency       = acc.currency,
                    )
                }
            }
            AccountPicker(
                title       = "СЧЁТ (НЕОБЯЗАТЕЛЬНО)",
                options     = options,
                selectedKey = options.firstOrNull { it.accountId == accountId }?.key,
                accent      = FosColors.Info,
                onSelect    = { key ->
                    accountId = options.firstOrNull { it.key == key }?.accountId
                },
            )
        }

        Button(
            onClick = {
                onSave(
                    (existing ?: PlannedPaymentEntity(
                        id            = UUID.randomUUID().toString(),
                        title         = title.trim(),
                        amountKopecks = amountKopecks,
                        anchorDate    = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    )).copy(
                        title         = title.trim(),
                        amountKopecks = amountKopecks,
                        direction     = direction,
                        schedule      = schedule,
                        anchorDate    = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                        // Задуманное число месяца хранится отдельно от даты — иначе платёж
                        // 31-го после первого февраля навсегда уезжает на 28-е.
                        dayOfMonth    = date.dayOfMonth,
                        accountId     = accountId,
                    )
                )
                onDismiss()
            },
            enabled  = canSave,
            colors   = ButtonDefaults.buttonColors(
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
            ),
            shape    = RoundedCornerShape(FosDimens.RadiusCardSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить", style = FosType.BodySemi)
        }

        if (existing != null && onDelete != null) {
            TextButton(
                onClick  = { onDelete(existing.id); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить обязательство", style = FosType.Label, color = FosColors.Negative)
            }
        }
        }

    if (showPicker) {
        // Как и в форме операции: календарь работает в UTC, и день читается обратно напрямую,
        // чтобы часовой пояс не сдвинул дату на сутки.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("Готово", color = FosColors.Info) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Отмена", color = FosColors.TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = FosColors.Surface),
        ) {
            // Ограничения «не в будущем» здесь НЕТ намеренно: обязательство — это как раз про
            // будущее, в отличие от операции, которая уже случилась.
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionChip(
    label   : String,
    selected: Boolean,
    accent  : androidx.compose.ui.graphics.Color,
    onClick : () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, style = FosType.Micro) },
        shape    = RoundedCornerShape(FosDimens.RadiusChip),
        colors   = chipColors(accent),
        border   = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun chipColors(accent: androidx.compose.ui.graphics.Color) =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = accent.copy(alpha = 0.15f),
        selectedLabelColor     = accent,
        containerColor         = FosColors.Surface2,
        labelColor             = FosColors.TextSecondary,
    )

private fun scheduleLabel(s: PaymentSchedule): String = when (s) {
    PaymentSchedule.ONCE      -> "Один раз"
    PaymentSchedule.WEEKLY    -> "Каждую неделю"
    PaymentSchedule.MONTHLY   -> "Каждый месяц"
    PaymentSchedule.QUARTERLY -> "Раз в квартал"
    PaymentSchedule.YEARLY    -> "Раз в год"
}
