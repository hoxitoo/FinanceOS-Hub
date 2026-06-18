package com.financeos.hub.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@Composable
fun SettingsScreen(
    onBack       : () -> Unit,
    viewModel    : SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FosColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FosDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
    ) {
        Spacer(Modifier.height(FosDimens.ItemGap))

        // Screen title
        Text("Настройки", style = FosType.ScreenTitle, color = FosColors.TextPrimary)

        // ── Hero variant ────────────────────────────────────────────────────────
        SettingsSection(title = "ГЛАВНЫЙ ЭКРАН") {
            Text(
                "Стиль дашборда",
                style = FosType.Micro,
                color = FosColors.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("CALM" to "Спокойный", "CONTRAST" to "Контраст", "MINIMAL" to "Minimal").forEach { (key, label) ->
                    val selected = state.heroVariant == key
                    Text(
                        text     = label,
                        style    = if (selected) FosType.SmallBold else FosType.Body,
                        color    = if (selected) FosColors.Positive else FosColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(FosDimens.RadiusChip))
                            .background(if (selected) FosColors.Surface2 else FosColors.Surface)
                            .clickable { viewModel.setHeroVariant(key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // ── Notifications ───────────────────────────────────────────────────────
        SettingsSection(title = "УВЕДОМЛЕНИЯ") {
            ToggleRow(
                label    = "Push-уведомления",
                sublabel = "Оповещения о бюджете и инсайтах",
                checked  = state.notificationsEnabled,
                onToggle = viewModel::setNotificationsEnabled,
            )
            if (state.notificationsEnabled) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Порог бюджета", style = FosType.Body, color = FosColors.TextSecondary)
                    Text("${state.budgetAlertThreshold}%", style = FosType.SmallBold, color = FosColors.Warning)
                }
                Slider(
                    value         = state.budgetAlertThreshold.toFloat(),
                    onValueChange = { viewModel.setBudgetAlertThreshold(it.toInt()) },
                    valueRange    = 50f..95f,
                    steps         = 8,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = SliderDefaults.colors(
                        thumbColor       = FosColors.Warning,
                        activeTrackColor = FosColors.Warning.copy(alpha = 0.6f),
                    ),
                )
                Text(
                    "Уведомлять когда расходы по категории достигают ${state.budgetAlertThreshold}% от лимита",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }

        // ── Security ────────────────────────────────────────────────────────────
        SettingsSection(title = "БЕЗОПАСНОСТЬ") {
            ToggleRow(
                label    = "Биометрия",
                sublabel = "Разблокировка по отпечатку / Face ID",
                checked  = state.biometricEnabled,
                onToggle = viewModel::setBiometricEnabled,
            )
        }

        // ── ML / AI ─────────────────────────────────────────────────────────────
        SettingsSection(title = "ИИ КЛАССИФИКАЦИЯ") {
            ToggleRow(
                label    = "ML-категоризация",
                sublabel = "Автоматически уточнять категории с помощью ИИ",
                checked  = state.mlClassificationEnabled,
                onToggle = viewModel::setMlClassificationEnabled,
            )
            if (!state.mlClassificationEnabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "При отключении используется встроенный словарный классификатор",
                    style = FosType.Micro,
                    color = FosColors.TextMuted,
                )
            }
        }

        // ── About ────────────────────────────────────────────────────────────────
        SettingsSection(title = "О ПРИЛОЖЕНИИ") {
            InfoRow("Версия",        "0.1.0-beta")
            InfoRow("База знаний",   "v1.0 (60 правил)")
            state.lastImportAt?.let { ts ->
                InfoRow("Последний импорт", ts.take(10))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FosDimens.ItemGap)) {
        Text(title, style = FosType.SectionCap, color = FosColors.TextMuted)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FosDimens.RadiusCard))
                .background(FosColors.Surface)
                .padding(FosDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label   : String,
    sublabel: String,
    checked : Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label,    style = FosType.BodySemi, color = FosColors.TextPrimary)
            Text(sublabel, style = FosType.Micro,    color = FosColors.TextMuted)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = FosColors.Background,
                checkedTrackColor  = FosColors.Positive,
                uncheckedThumbColor= FosColors.TextMuted,
                uncheckedTrackColor= FosColors.Surface2,
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = FosType.Body,      color = FosColors.TextSecondary)
        Text(value, style = FosType.SmallBold, color = FosColors.TextPrimary)
    }
}
