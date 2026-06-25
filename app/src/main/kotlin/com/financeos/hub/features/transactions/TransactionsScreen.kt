package com.financeos.hub.features.transactions

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.components.TransactionRow
import java.io.File
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.FosType
import com.financeos.hub.ui.theme.LocalShimmer

/** Red delete background revealed while swiping a row in either direction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteBackground(direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else                              -> Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall))
            .background(FosColors.Negative.copy(alpha = 0.18f))
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Text("🗑  Удалить", style = FosType.Label, color = FosColors.Negative)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: TransactionsViewModel = hiltViewModel()) {
    val state      by vm.state.collectAsState()
    val context    = LocalContext.current
    var showAddSheet  by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTx   by remember { mutableStateOf<com.financeos.hub.core.database.entities.TransactionEntity?>(null) }
    var showPdfSheet by remember { mutableStateOf(false) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pdfSheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = FosColors.Background,
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
                    TextButton(
                        onClick        = { showPdfSheet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("↓ PDF", style = FosType.Label, color = FosColors.Info)
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
                        Text("↑ CSV", style = FosType.Label, color = FosColors.TextSecondary)
                    }
                }
            }

            // Search bar
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
                    .padding(bottom = 8.dp),
            )

            // Active category filter banner
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

            // Filter chips
            LazyRow(
                contentPadding        = PaddingValues(horizontal = FosDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(FosDimens.ItemGap),
            ) {
                items(TxFilter.entries) { filter ->
                    FilterChip(
                        selected = state.activeFilter == filter,
                        onClick  = { vm.setFilter(filter) },
                        label    = { Text(filter.label, style = FosType.Label) },
                        shape    = RoundedCornerShape(FosDimens.RadiusChip),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor  = FosColors.Positive.copy(alpha = 0.15f),
                            selectedLabelColor      = FosColors.Positive,
                            containerColor          = FosColors.Surface,
                            labelColor              = FosColors.TextSecondary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(FosDimens.ItemGap))

            // Grouped transaction list
            if (state.grouped.isEmpty()) {
                Box(
                    modifier          = Modifier.fillMaxSize(),
                    contentAlignment  = Alignment.Center,
                ) {
                    Text(
                        text  = if (state.searchQuery.isBlank()) "Операций пока нет"
                                else "Ничего не найдено",
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
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value != SwipeToDismissBoxValue.Settled) {
                                            vm.deleteTransaction(tx.id)
                                            true
                                        } else false
                                    },
                                )
                                SwipeToDismissBox(
                                    state             = dismissState,
                                    modifier          = Modifier.graphicsLayer { alpha = depthAlpha },
                                    backgroundContent = { SwipeDeleteBackground(dismissState.dismissDirection) },
                                ) {
                                    TransactionRow(
                                        transaction  = tx,
                                        categoryName = state.categoryName(tx.categoryId),
                                        onClick      = { selectedTx = tx },
                                    )
                                }
                            }
                        }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
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
                sheetState = addSheetState,
                categories = state.categories,
                accounts   = state.accounts,
                onDismiss  = { showAddSheet = false },
                onSave     = { type, kopecks, merchant, catId, note, accountId, timestamp ->
                    vm.insertManual(type, kopecks, merchant, catId, note, accountId, timestamp)
                },
            )
        }

        selectedTx?.let { tx ->
            TransactionDetailSheet(
                transaction  = tx,
                categories   = state.categories,
                categoryName = state.categoryName(tx.categoryId),
                sheetState   = detailSheetState,
                onDismiss    = { selectedTx = null },
                onSave       = { type, merchant, catId, note ->
                    vm.updateTransaction(tx, type, merchant, catId, note)
                },
                onDelete     = { vm.deleteTransaction(tx.id) },
            )
        }
    }
}
