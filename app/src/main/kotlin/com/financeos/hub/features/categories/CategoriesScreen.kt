package com.financeos.hub.features.categories

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.database.entities.CategoryEntity
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit = {},
    vm    : CategoriesViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsState()
    var showAddSheet  by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val systemCats = categories.filter { it.isSystem }
    val customCats = categories.filter { !it.isSystem }

    Scaffold(
        containerColor = FosColors.Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddSheet = true },
                containerColor = FosColors.Positive,
                contentColor   = FosColors.Background,
                shape          = CircleShape,
                modifier       = Modifier.size(56.dp),
            ) {
                Text("+", style = FosType.ScreenTitle, color = FosColors.Background)
            }
        },
    ) { inner ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .background(FosColors.Background)
                .padding(inner),
            contentPadding      = PaddingValues(horizontal = FosDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FosDimens.CardGap),
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            // Header with back button
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Категории", style = FosType.ScreenTitle, color = FosColors.TextPrimary)
                    TextButton(onClick = onBack) {
                        Text("← Назад", style = FosType.Label, color = FosColors.TextSecondary)
                    }
                }
            }

            // System categories (read-only)
            if (systemCats.isNotEmpty()) {
                item {
                    Text("СИСТЕМНЫЕ", style = FosType.SectionCap, color = FosColors.TextMuted)
                }
                items(systemCats, key = { it.id }) { cat ->
                    CategoryRow(cat = cat, onDelete = null)
                }
            }

            // Custom categories
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "ПОЛЬЗОВАТЕЛЬСКИЕ",
                    style = FosType.SectionCap,
                    color = FosColors.TextMuted,
                )
            }
            if (customCats.isEmpty()) {
                item {
                    Text(
                        "Нажмите + чтобы создать категорию",
                        style    = FosType.Body,
                        color    = FosColors.TextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                items(customCats, key = { it.id }) { cat ->
                    CategoryRow(cat = cat, onDelete = { vm.deleteCategory(cat.id) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSheet) {
        AddCategorySheet(
            sheetState = addSheetState,
            onDismiss  = { showAddSheet = false },
            onSave     = { name, emoji, color ->
                vm.createCategory(name, emoji, color)
            },
        )
    }
}

@Composable
private fun CategoryRow(cat: CategoryEntity, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCard))
            .background(FosColors.Surface)
            .padding(FosDimens.CardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        runCatching { Color(android.graphics.Color.parseColor(cat.color)) }
                            .getOrDefault(FosColors.TextMuted)
                    ),
            )
            Text(cat.emoji, style = FosType.BodySemi)
            Text(cat.name,  style = FosType.BodySemi, color = FosColors.TextPrimary)
        }

        if (onDelete != null) {
            TextButton(
                onClick        = onDelete,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text("×", style = FosType.BodySemi, color = FosColors.Negative)
            }
        } else {
            Text("🔒", style = FosType.Micro, color = FosColors.TextMuted)
        }
    }
}
