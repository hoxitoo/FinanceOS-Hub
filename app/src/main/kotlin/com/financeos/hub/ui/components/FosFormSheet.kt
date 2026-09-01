package com.financeos.hub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

/**
 * Лист с формой, из которого нельзя случайно выйти, потеряв ввод.
 *
 * Смахивание вниз — самый лёгкий жест на экране, и он же был необратимым: заполненная анкета
 * закрывалась молча, без сохранения и без вопроса. Потерянный ввод не восстановить ничем, а сам
 * жест часто выходит случайно при прокрутке длинной формы.
 *
 * Перехват сделан через `confirmValueChange`, а не через `onDismissRequest`. Разница видна на
 * устройстве: `confirmValueChange` **отклоняет сам переход** в скрытое состояние, и лист остаётся
 * на месте. Перехват в `onDismissRequest` срабатывает уже после того, как лист уехал вниз, и его
 * пришлось бы возвращать обратно — получался бы отскок.
 *
 * Спрашиваем только когда есть что терять: [hasChanges] = false (человек ничего не трогал или уже
 * сохранил) закрывает лист сразу. Диалог на каждое закрытие быстро приучает жать «да» не глядя, и
 * защита перестаёт работать ровно тогда, когда нужна.
 *
 * Лист владеет своим `SheetState` сам: `confirmValueChange` задаётся при создании состояния и
 * должен видеть [hasChanges], которое известно только внутри формы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FosFormSheet(
    onDismiss : () -> Unit,
    hasChanges: () -> Boolean,
    /** Текст под заголовком диалога. По умолчанию — про несохранённый ввод. */
    warning   : String = "Введённые данные не сохранятся.",
    content   : @Composable ColumnScope.() -> Unit,
) {
    var askExit by remember { mutableStateOf(false) }
    // Лямбда живёт внутри SheetState и переживает рекомпозиции: без rememberUpdatedState она
    // навсегда запомнила бы первую версию и спрашивала бы по устаревшему состоянию формы.
    val dirty = rememberUpdatedState(hasChanges)

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange    = { target ->
            if (target == SheetValue.Hidden && dirty.value()) {
                askExit = true
                false          // переход отклонён — лист остаётся на месте, без отскока
            } else {
                true
            }
        },
    )

    ModalBottomSheet(
        // Сюда приходят закрытия, которые confirmValueChange пропустил (формы без изменений) и
        // нажатие на затемнение. Проверка повторяется: путей закрытия больше одного.
        onDismissRequest = { if (dirty.value()) askExit = true else onDismiss() },
        sheetState       = sheetState,
        containerColor   = FosColors.Surface,
        contentColor     = FosColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Формы выше экрана (чипы, суммы, выбор счёта, поля, кнопка). Без verticalScroll
                // всё ниже сгиба просто недостижимо — нельзя ни раскрыть банк, ни нажать
                // «Сохранить». imePadding обязателен из-за edge-to-edge: окно не ужимается под
                // клавиатуру, и та накрыла бы поле, в которое печатают.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FosDimens.ScreenPadding)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
            content             = content,
        )
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            containerColor   = FosColors.Surface,
            title            = {
                Text("Выйти без сохранения?", style = FosType.BodySemi, color = FosColors.TextPrimary)
            },
            text = { Text(warning, style = FosType.Body, color = FosColors.TextSecondary) },
            confirmButton = {
                // «Выйти» — разрушающее действие, поэтому красное. Продолжить заполнение
                // безопаснее, и эта кнопка стоит там, где палец лежит по умолчанию.
                TextButton(onClick = { askExit = false; onDismiss() }) {
                    Text("Выйти", color = FosColors.Negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { askExit = false }) {
                    Text("Продолжить", color = FosColors.Info)
                }
            },
        )
    }
}
