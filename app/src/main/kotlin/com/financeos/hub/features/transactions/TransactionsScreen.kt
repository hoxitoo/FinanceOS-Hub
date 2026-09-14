package com.financeos.hub.features.transactions

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.AccountKind
import com.financeos.hub.ui.components.SwipeToRevealDelete
import com.financeos.hub.ui.components.TransactionRow
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: TransactionsViewModel = hiltViewModel()) {
    val state      by vm.state.collectAsState()
    // Считаем один раз на список, а не на строку: иначе на каждой из сотен строк шёл бы поиск по
    // списку счетов, и на прокрутке это заметно.
    val creditAccountIds = remember(state.accounts) {
        state.accounts.filter { it.kind == AccountKind.CREDIT }.map { it.id }.toSet()
    }
    val context    = LocalContext.current
    var showAddSheet  by remember { mutableStateOf(false) }

    var selectedTx   by remember { mutableStateOf<com.financeos.hub.core.database.entities.TransactionEntity?>(null) }
    var showPdfSheet by remember { mutableStateOf(false) }
    var searchOpen     by remember { mutableStateOf(false) }
    var typeMenuOpen   by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val pdfSheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = FosColors.Background,
        // Экран живёт ВНУТРИ Scaffold'а навигации, который уже отступил от системной панели.
        // Второй Scaffold по умолчанию отступает ещё раз, и снизу оставалась пустая чёрная
        // полоса высотой в навигацию — «плата», под которой ничего нет.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick          = { showAddSheet = true },
                containerColor   = FosColors.Positive,
                contentColor     = FosColors.Background,
                shape            = CircleShape,
                modifier         = Modifier.size(56.dp),
            ) {
                Text("+", style = FosType.ScreenTitle, color = FosColors.Background)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FosColors.Background)
                .padding(innerPadding),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FosDimens.ScreenPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Операции", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // «PDF» и «CSV» называли ФОРМАТ, а не действие: чтобы понять, что из них
                    // читает выписку, а что отдаёт историю, надо было помнить. Теперь названо
                    // действие; формат остаётся там, где он что-то значит, — внутри самого
                    // листа импорта («Импорт выписки PDF») и в имени выгруженного файла.
                    TextButton(
                        onClick        = { showPdfSheet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("↓ Импорт", style = FosType.Label, color = FosColors.Info)
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                val csv  = vm.buildCsvString()
                                val file = File(context.cacheDir, "financeos_export.csv")
                                file.writeText(csv, Charsets.UTF_8)
                                val uri  = FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("↑ Экспорт", style = FosType.Label, color = FosColors.TextSecondary)
                    }
                }
            }

            // Одна строка вместо трёх: поиск-поле во всю ширину, ряд чипов и баннер категории
            // занимали треть экрана до первой операции. Поиск свёрнут в лупу и раскрывается по
            // нажатию — он нужен изредка, а место отнимал всегда.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Прокрутка, а не перенос: выбранный отрезок дат делает третий чип длиннее
                    // остальных двух вместе взятых, и на узком экране он иначе обрезался бы.
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = FosDimens.ScreenPadding)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(FosDimens.ItemGap),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                FilterPill(
                    label      = "🔍",
                    labelStyle = FosType.Body,
                    active     = searchOpen || state.searchQuery.isNotBlank(),
                    onClick  = {
                        searchOpen = !searchOpen
                        // Закрыли — значит и запрос снимаем: свёрнутая лупа со скрытым фильтром
                        // молча прятала бы часть истории.
                        if (!searchOpen) vm.setSearch("")
                    },
                )
                FilterPill(
                    label   = if (state.activeFilter == TxFilter.ALL) "Тип операции"
                              else state.activeFilter.label,
                    active  = state.activeFilter != TxFilter.ALL,
                    trailing = "▾",
                    onClick = { typeMenuOpen = true },
                ) {
                    DropdownMenu(
                        expanded         = typeMenuOpen,
                        onDismissRequest = { typeMenuOpen = false },
                        modifier         = Modifier.background(FosColors.Surface),
                    ) {
                        TxFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        filter.label,
                                        style = FosType.Body,
                                        color = if (state.activeFilter == filter) FosColors.Positive
                                                else FosColors.TextPrimary,
                                    )
                                },
                                onClick = { vm.setFilter(filter); typeMenuOpen = false },
                            )
                        }
                    }
                }
                FilterPill(
                    label    = state.dateRange?.let { range ->
                        if (range.single) range.from.chipLabel()
                        else "${range.from.chipLabel()} – ${range.to.chipLabel()}"
                    } ?: "Дата",
                    active   = state.dateRange != null,
                    trailing = if (state.dateRange != null) "×" else "▾",
                    onClick  = {
                        // Нажатие на заполненный фильтр снимает его — иначе сбросить дату можно
                        // было бы только пройдя календарь заново.
                        if (state.dateRange != null) vm.setDateRange(null) else datePickerOpen = true
                    },
                )
            }

            if (searchOpen) {
                val searchFocus = remember { FocusRequester() }
                // Лупа раскрывает поле — значит человек уже собрался печатать. Без фокуса пришлось
                // бы нажимать второй раз по только что появившемуся полю.
                // LaunchedEffect выполняется ПОСЛЕ того, как кадр применён, поэтому поле уже
                // существует; catch — на случай, когда поле успели свернуть в том же кадре.
                LaunchedEffect(Unit) {
                    try {
                        searchFocus.requestFocus()
                    } catch (_: IllegalStateException) {
                    }
                }
                OutlinedTextField(
                    value         = state.searchQuery,
                    onValueChange = { vm.setSearch(it) },
                    placeholder   = { Text("Поиск...", style = FosType.Body, color = FosColors.TextMuted) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction    = ImeAction.Search,
                    ),
                    shape  = RoundedCornerShape(FosDimens.RadiusChip),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = FosColors.Info,
                        unfocusedBorderColor = FosColors.BorderStrong,
                        focusedTextColor     = FosColors.TextPrimary,
                        unfocusedTextColor   = FosColors.TextPrimary,
                        cursorColor          = FosColors.Info,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FosDimens.ScreenPadding)
                        .padding(bottom = 8.dp)
                        .focusRequester(searchFocus),
                )
            }

            // Активный фильтр категории приходит из аналитики по deep-link, и снять его больше
            // нечем — поэтому строка остаётся, но только когда фильтр действительно стоит.
            if (state.categoryFilter != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FosDimens.ScreenPadding)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        "Категория: ${state.categoryName(state.categoryFilter)}",
                        style = FosType.Label,
                        color = FosColors.Info,
                    )
                    TextButton(
                        onClick        = { vm.clearCategoryFilter() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text("× Сбросить", style = FosType.Micro, color = FosColors.TextMuted)
                    }
                }
            }

            Spacer(Modifier.height(FosDimens.ItemGap))

            // Grouped transaction list
            if (state.grouped.isEmpty()) {
                Box(
                    modifier          = Modifier.fillMaxSize(),
                    contentAlignment  = Alignment.Center,
                ) {
                    // «Операций пока нет» при выставленном фильтре — ложь: операции есть, их
                    // просто отсеяли. Человек, выбравший вчерашний день, решил бы, что история
                    // потерялась, и полез бы искать поломку там, где её нет.
                    val narrowed = state.searchQuery.isNotBlank() ||
                        state.activeFilter != TxFilter.ALL ||
                        state.dateRange != null ||
                        state.categoryFilter != null
                    Text(
                        text  = if (narrowed) "Ничего не найдено" else "Операций пока нет",
                        style = FosType.Body,
                        color = FosColors.TextMuted,
                    )
                }
            } else {
                // «Атмосфера» layer: older day-groups recede into the dark (depth-of-field).
                // Static per-position alpha — no animation, so it is safe under reduce-motion.
                val depthEnabled = LocalShimmer.current.depthTimeline
                LazyColumn(
                    contentPadding        = PaddingValues(horizontal = FosDimens.ScreenPadding, vertical = 4.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                ) {
                    state.grouped.entries
                        .sortedByDescending { it.key }
                        .withIndex()
                        .forEach { (groupIndex, entry) ->
                            val (day, txList) = entry
                            val depthAlpha = if (depthEnabled) (1f - groupIndex * 0.07f).coerceAtLeast(0.45f) else 1f
                            item(key = "header_$day") {
                                Text(
                                    text     = FosFormatter.dayLabelYear(day),
                                    style    = FosType.SectionCap,
                                    color    = FosColors.TextMuted,
                                    modifier = Modifier
                                        .graphicsLayer { alpha = depthAlpha }
                                        .padding(top = FosDimens.ItemGap, bottom = 4.dp),
                                )
                            }
                            items(txList.sortedByDescending { it.timestamp }, key = { it.id }) { tx ->
                                SwipeToRevealDelete(
                                    onDelete = { vm.deleteTransaction(tx.id) },
                                    modifier = Modifier.graphicsLayer { alpha = depthAlpha },
                                ) {
                                    TransactionRow(
                                        transaction  = tx,
                                        categoryName = state.categoryName(tx.categoryId),
                                        onCredit     = tx.accountId in creditAccountIds,
                                        onClick      = { selectedTx = tx },
                                    )
                                }
                            }
                        }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        if (datePickerOpen) {
            DateRangeDialog(
                initial    = state.dateRange,
                onDismiss  = { datePickerOpen = false },
                onSelected = { vm.setDateRange(it); datePickerOpen = false },
            )
        }

        if (showPdfSheet) {
            ImportPdfSheet(
                vm         = vm,
                sheetState = pdfSheetState,
                onDismiss  = { showPdfSheet = false },
            )
        }

        if (showAddSheet) {
            AddTransactionSheet(
                categories = state.categories,
                accounts   = state.accounts,
                cards      = state.cards,
                onDismiss  = { showAddSheet = false },
                onSave     = { type, kopecks, merchant, catId, note, accountId, sourceMask, destAccountId, timestamp ->
                    vm.insertManual(type, kopecks, merchant, catId, note, accountId, sourceMask, destAccountId, timestamp)
                },
            )
        }

        selectedTx?.let { tx ->
            TransactionDetailSheet(
                transaction  = tx,
                categories   = state.categories,
                categoryName = state.categoryName(tx.categoryId),
                linkedAccountName = state.accounts.firstOrNull { it.id == tx.accountId }?.name,
                onDismiss    = { selectedTx = null },
                onSave       = { type, merchant, catId, note ->
                    vm.updateTransaction(tx, type, merchant, catId, note)
                },
            )
        }
    }
}

/**
 * Чип-фильтр: подпись, стрелка-состояние и, при необходимости, выпадающее меню под ним.
 *
 * Меню лежит ВНУТРИ чипа, а не рядом: `DropdownMenu` привязывается к своему родителю, и вынесенное
 * наружу оно раскрывалось бы от края экрана, а не от кнопки, которую нажали.
 */
@Composable
private fun FilterPill(
    label     : String,
    active    : Boolean,
    onClick   : () -> Unit,
    trailing  : String? = null,
    labelStyle: TextStyle = FosType.Label,
    menu      : @Composable () -> Unit = {},
) {
    Box {
        // `FilterChip`, а не `clip + background` руками: огранка чипа — часть системы (правило
        // «никогда не рисуй карточку руками»), и ровно такими чипами набраны фильтры на всех
        // остальных экранах.
        FilterChip(
            selected = active,
            onClick  = onClick,
            label    = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(label, style = labelStyle, maxLines = 1)
                    if (trailing != null) {
                        Text(trailing, style = FosType.Micro, color = FosColors.TextMuted)
                    }
                }
            },
            shape  = RoundedCornerShape(FosDimens.RadiusChip),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = FosColors.Positive.copy(alpha = 0.15f),
                selectedLabelColor     = FosColors.Positive,
                containerColor         = FosColors.Surface,
                labelColor             = FosColors.TextSecondary,
            ),
        )
        menu()
    }
}

/**
 * Календарь выбора даты или отрезка.
 *
 * Один экран на оба случая намеренно: «показать один день» и «показать неделю» — это один и тот же
 * вопрос с разной шириной, и два отдельных режима заставляли бы выбирать между ними ДО того, как
 * человек посмотрел на календарь. Выбрал одну дату — фильтр на день; выбрал вторую — на отрезок.
 *
 * Даты `DateRangePicker` приходят в UTC-полуночи, поэтому обратно берётся именно дата по UTC:
 * перевод через системную зону сдвинул бы выбранный день на сутки восточнее Гринвича.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initial   : DateRange?,
    onDismiss : () -> Unit,
    onSelected: (DateRange?) -> Unit,
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.from?.toUtcMillis(),
        initialSelectedEndDateMillis   = initial?.to?.toUtcMillis(),
        selectableDates = object : SelectableDates {
            // Операции в будущем невозможны, а выбранный «завтрашний» день дал бы пустой список
            // без объяснения причины.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= LocalDate.now().toUtcMillis()
        },
    )

    // Собственный `Dialog`, а не `DatePickerDialog`: тот держит содержимое в коробке 360×568 dp,
    // а календарь отрезка требует ровно столько же ПОД СЕБЯ — кнопки «Готово»/«Отмена» уезжали бы
    // за нижний край, и диалог нельзя было бы закрыть иначе как вне его. Здесь календарь получает
    // `weight(1f)`, то есть всё оставшееся место, а кнопки — своё, всегда видимое.
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color    = FosColors.Surface,
            shape    = RoundedCornerShape(FosDimens.RadiusCard),
            modifier = Modifier
                .fillMaxSize()
                .padding(FosDimens.ItemGap),
        ) {
            Column(Modifier.fillMaxSize()) {
                DateRangePicker(
                    state = pickerState,
                    title = {
                        Text(
                            "Период",
                            style    = FosType.BodySemi,
                            color    = FosColors.TextPrimary,
                            modifier = Modifier.padding(start = FosDimens.ScreenPadding, top = 12.dp),
                        )
                    },
                    showModeToggle = false,
                    colors   = DatePickerDefaults.colors(containerColor = FosColors.Surface),
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FosDimens.ItemGap, vertical = FosDimens.ItemGap),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onSelected(null) }) {
                        Text("Сбросить", style = FosType.Label, color = FosColors.TextMuted)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", style = FosType.Label, color = FosColors.TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            val from = pickerState.selectedStartDateMillis?.toUtcDate()
                            // Одна выбранная дата — фильтр на этот день: конец совпадает с началом.
                            val to   = pickerState.selectedEndDateMillis?.toUtcDate() ?: from
                            onSelected(if (from != null && to != null) DateRange(from, to) else null)
                        },
                    ) {
                        Text("Готово", style = FosType.Label, color = FosColors.Positive)
                    }
                }
            }
        }
    }
}

/**
 * Дата для подписи чипа: «14.09», и год — только если он не текущий.
 *
 * `FosFormatter.date` здесь не годится: «14 сентября 2026» умещается в срок цели, но отрезок из двух
 * таких дат шире экрана телефона, и чип пришлось бы обрезать ровно на том, ради чего он нужен.
 */
private fun LocalDate.chipLabel(): String =
    format(if (year == LocalDate.now().year) chipDateFmt else chipDateYearFmt)

private val chipDateFmt     = DateTimeFormatter.ofPattern("dd.MM")
private val chipDateYearFmt = DateTimeFormatter.ofPattern("dd.MM.yy")

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
