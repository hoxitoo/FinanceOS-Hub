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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.financeos.hub.core.database.entities.TransferMatchType
import com.financeos.hub.core.database.entities.TransferRouteEntity
import com.financeos.hub.ui.components.FosFormSheet
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.bankBrand

/**
 * Лист автопополнения: какие переводы приложение само засчитает в цель.
 *
 * Прежняя версия вываливала всё сразу — плоскую ленту из десятка счетов, шестнадцать голых
 * четырёхзначных чисел, два поля ввода и список привязок в самом низу. Ответ на вопрос «а что
 * сейчас привязано» лежал ниже трёх экранов выбора, а номера карт без имён не значили ничего:
 * «•• 6703» опознаётся только по счёту, которому принадлежит.
 *
 * Теперь порядок обратный: сначала СОСТОЯНИЕ (что привязано), потом выбор, и уже потом — ручной
 * ввод, спрятанный за «Ещё». Счета сгруппированы по банкам и раскрываются нажатием, карты подписаны
 * именем своего счёта.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinkTransferRouteSheet(
    goal          : GoalEntity,
    routes        : List<TransferRouteEntity>,
    cardMasks     : List<String>,
    accounts      : List<AccountEntity> = emptyList(),
    /** Маска карты → имя счёта, которому она принадлежит. Голые цифры не опознаются. */
    cardOwners    : Map<String, String> = emptyMap(),
    onLinkCard    : (mask: String) -> Unit,
    onLinkKeyword : (keyword: String) -> Unit,
    onLinkAccount : (accountId: String) -> Unit = {},
    onUnlink      : (routeId: String) -> Unit,
    onDismiss     : () -> Unit,
) {
    var keyword     by remember { mutableStateOf("") }
    var cardInput   by remember { mutableStateOf("") }
    var moreOpen    by remember { mutableStateOf(false) }
    var expandedBank by remember { mutableStateOf<String?>(null) }

    val goalRoutes = routes.filter { it.goalId == goal.id }

    /** Маршрут этой цели по типу и значению — он же кнопка «отвязать» для чипа. */
    fun routeFor(type: TransferMatchType, value: String) = goalRoutes.firstOrNull {
        it.matchType == type && it.matchValue.equals(value, ignoreCase = true)
    }

    val dirty = { keyword.isNotBlank() || cardInput.isNotBlank() }

    FosFormSheet(
        onDismiss  = onDismiss,
        hasChanges = dirty,
    ) {
        Text(
            "Автопополнение «${goal.name}»",
            style = FosType.ScreenTitle,
            color = FosColors.TextPrimary,
        )
        Text(
            "Перевод НА привязанный счёт добавится к цели, перевод С него — вычтется. " +
                "Покупки и зачисления цель не двигают.",
            style = FosType.Micro,
            color = FosColors.TextSecondary,
        )

        // ── Что привязано сейчас ─────────────────────────────────────────────────
        if (goalRoutes.isEmpty()) {
            Text(
                "Пока ничего не привязано — цель пополняется только вручную через «±».",
                style = FosType.Micro,
                color = FosColors.TextMuted,
            )
        } else {
            Text("ПРИВЯЗАНО", style = FosType.SectionCap, color = FosColors.TextSecondary)
            goalRoutes.forEach { route ->
                val label = when (route.matchType) {
                    TransferMatchType.CARD    ->
                        "карта •• ${route.matchValue}" +
                            (cardOwners[route.matchValue]?.let { " · $it" } ?: "")
                    TransferMatchType.KEYWORD -> "слово «${route.matchValue}»"
                    TransferMatchType.ACCOUNT ->
                        "счёт ${accounts.find { it.id == route.matchValue }?.name ?: route.matchValue}"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FosDimens.RadiusButton))
                        .background(FosColors.Positive.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(label, style = FosType.Body, color = FosColors.TextPrimary, maxLines = 1)
                    Text(
                        "× Отвязать",
                        style    = FosType.Label,
                        color    = FosColors.Negative,
                        modifier = Modifier.clickable { onUnlink(route.id) },
                    )
                }
            }
        }

        // ── Счета: банк → его счета ──────────────────────────────────────────────
        if (accounts.isNotEmpty()) {
            Text("СЧЁТ", style = FosType.SectionCap, color = FosColors.TextSecondary)
            val banks = remember(accounts) { accounts.groupBy { it.bank }.toList() }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                banks.forEach { (bank, bankAccounts) ->
                    val brand      = bankBrand(bank)
                    val isExpanded = expandedBank == bank
                    val linkedHere = bankAccounts.count { routeFor(TransferMatchType.ACCOUNT, it.id) != null }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                            .background(
                                if (linkedHere > 0) FosColors.Positive.copy(alpha = 0.10f)
                                else FosColors.Surface2
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
                            Text(
                                bank.trim().take(1).uppercase(),
                                style = FosType.SmallBold,
                                color = brand.onBg,
                            )
                        }
                        Text(
                            bank,
                            style    = FosType.Body,
                            color    = FosColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (linkedHere > 0) {
                            Text(
                                "привязано: $linkedHere",
                                style = FosType.Micro,
                                color = FosColors.Positive,
                            )
                        }
                        Text(
                            if (isExpanded) "▲" else "▼",
                            style = FosType.Micro,
                            color = FosColors.TextMuted,
                        )
                    }

                    if (isExpanded) {
                        bankAccounts.forEach { acc ->
                            val route = routeFor(TransferMatchType.ACCOUNT, acc.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
                                    .background(
                                        if (route != null) FosColors.Positive.copy(alpha = 0.16f)
                                        else FosColors.Surface
                                    )
                                    // Повторное нажатие ОТВЯЗЫВАЕТ. Раньше привязанный чип просто
                                    // переставал нажиматься, и снять привязку можно было только
                                    // из списка внизу — то есть не там, где её поставили.
                                    .clickable {
                                        if (route != null) onUnlink(route.id) else onLinkAccount(acc.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (route != null) "✓" else "+",
                                    style = FosType.BodySemi,
                                    color = if (route != null) FosColors.Positive else FosColors.TextMuted,
                                )
                                Text(
                                    acc.name,
                                    style    = FosType.Body,
                                    color    = if (route != null) FosColors.Positive else FosColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                acc.cardMask?.let {
                                    Text("•• $it", style = FosType.Micro, color = FosColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Карты получателя ─────────────────────────────────────────────────────
        if (cardMasks.isNotEmpty()) {
            Text("КАРТА ПОЛУЧАТЕЛЯ", style = FosType.SectionCap, color = FosColors.TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
            ) {
                cardMasks.forEach { mask ->
                    val route = routeFor(TransferMatchType.CARD, mask)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(FosDimens.RadiusButton))
                            .background(
                                if (route != null) FosColors.Positive.copy(alpha = 0.12f)
                                else FosColors.Surface2
                            )
                            .border(
                                1.dp,
                                if (route != null) FosColors.Positive else FosColors.BorderStrong,
                                RoundedCornerShape(FosDimens.RadiusButton),
                            )
                            .clickable {
                                if (route != null) onUnlink(route.id) else onLinkCard(mask)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "•• $mask",
                            style = FosType.Label,
                            color = if (route != null) FosColors.Positive else FosColors.TextPrimary,
                        )
                        // Имя счёта рядом с маской: шестнадцать голых четырёхзначных чисел
                        // невозможно различить, и выбор превращался в угадывание.
                        cardOwners[mask]?.let {
                            Text(
                                it,
                                style    = FosType.Micro,
                                color    = FosColors.TextMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ── Ручной ввод — под «Ещё» ──────────────────────────────────────────────
        // Два поля ввода занимали треть листа ради случая «карты нет в списке». Случай редкий,
        // место постоянное.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FosDimens.RadiusButton))
                .clickable { moreOpen = !moreOpen }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "Ещё: карта вручную и слово в СМС",
                style = FosType.Label,
                color = FosColors.Info,
            )
            Text(if (moreOpen) "▲" else "▼", style = FosType.Micro, color = FosColors.TextMuted)
        }

        if (moreOpen) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value         = cardInput,
                    onValueChange = { cardInput = it.filter { c -> c.isDigit() }.take(4) },
                    placeholder   = { Text("Последние 4 цифры", style = FosType.Body, color = FosColors.TextMuted) },
                    singleLine    = true,
                    textStyle     = FosType.Body,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    colors   = sheetFieldColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                )
                Button(
                    onClick = {
                        if (cardInput.length == 4) {
                            onLinkCard(cardInput)
                            cardInput = ""
                        }
                    },
                    enabled = cardInput.length == 4,
                    shape   = RoundedCornerShape(FosDimens.RadiusButton),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = FosColors.Info,
                        contentColor   = FosColors.Background,
                    ),
                ) {
                    Text("Добавить", style = FosType.Label)
                }
            }

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value           = keyword,
                    onValueChange   = { keyword = it },
                    placeholder     = { Text("слово в СМС, например «вклад»", style = FosType.Body, color = FosColors.TextMuted) },
                    singleLine      = true,
                    textStyle       = FosType.Body,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors          = sheetFieldColors(),
                    modifier        = Modifier
                        .weight(1f)
                        .height(52.dp),
                )
                Button(
                    onClick = {
                        if (keyword.isNotBlank()) {
                            onLinkKeyword(keyword)
                            keyword = ""
                        }
                    },
                    enabled = keyword.isNotBlank(),
                    shape   = RoundedCornerShape(FosDimens.RadiusButton),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = FosColors.Positive,
                        contentColor   = FosColors.Background,
                    ),
                ) {
                    Text("Добавить", style = FosType.Label)
                }
            }
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = FosColors.Info,
    unfocusedBorderColor    = FosColors.Info.copy(alpha = 0.40f),
    focusedContainerColor   = FosColors.Surface2,
    unfocusedContainerColor = FosColors.Surface2,
    cursorColor             = FosColors.Info,
    focusedTextColor        = FosColors.TextPrimary,
    unfocusedTextColor      = FosColors.TextPrimary,
    errorBorderColor        = FosColors.Negative,
)
