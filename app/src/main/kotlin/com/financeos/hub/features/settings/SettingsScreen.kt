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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.financeos.hub.core.notifications.PushNotificationListener
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType

@Composable
fun SettingsScreen(
    onBack            : () -> Unit,
    onCategoriesClick : () -> Unit = {},
    viewModel         : SettingsViewModel = hiltViewModel(),
) {
    val state     by viewModel.state.collectAsState()
    val smsImport by viewModel.smsImport.collectAsState()
    val backup    by viewModel.backup.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Request READ_SMS + RECEIVE_SMS, then run the manual 90-day import on grant.
    val smsImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[android.Manifest.permission.READ_SMS] == true) {
            viewModel.setSmsRealtimeEnabled(true)
            viewModel.importSmsHistory()
        }
    }
    fun startSmsImport() {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setSmsRealtimeEnabled(true)
            viewModel.importSmsHistory()
        } else {
            smsImportLauncher.launch(arrayOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_SMS,
            ))
        }
    }

    // Storage Access Framework: write/read the backup to a user-chosen location.
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.restoreBackup(it) } }

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

        // ── Customization («Мерцание») ───────────────────────────────────────────
        SettingsSection(title = "КАСТОМИЗАЦИЯ") {
            ToggleRow(
                label    = "Анимации",
                sublabel = "Плавные переходы, счётчики чисел, объёмные карты и отклик касания",
                checked  = state.animationsEnabled,
                onToggle = viewModel::setAnimationsEnabled,
            )
            // Conditional sub-toggle: only relevant when animations (and thus the cards) are on.
            androidx.compose.animation.AnimatedVisibility(visible = state.animationsEnabled) {
                Column {
                    HorizontalDivider(
                        color = FosColors.Border, thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Row(modifier = Modifier.padding(start = 12.dp)) {
                        ToggleRow(
                            label    = "Карты: глубокое стекло",
                            sublabel = "Спокойнее и экономнее для батареи (выкл — голографик)",
                            checked  = state.cardsVariantB,
                            onToggle = viewModel::setCardsVariantB,
                        )
                    }
                }
            }
            HorizontalDivider(
                color = FosColors.Border, thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            ToggleRow(
                label    = "Атмосфера «Мерцание»",
                sublabel = "Светлячки, свечение и глубина — загадочная атмосфера",
                checked  = state.atmosphereEnabled,
                onToggle = viewModel::setAtmosphereEnabled,
            )
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

        // ── Push notification reader ─────────────────────────────────────────────
        SettingsSection(title = "УВЕДОМЛЕНИЯ ОТ БАНКОВ") {
            val pushGranted = PushNotificationListener.isPermissionGranted(context)
            ToggleRow(
                label    = "Читать уведомления банков",
                sublabel = "Т-Банк, Сбербанк, ВТБ, Альфа-Банк, Газпромбанк",
                checked  = state.pushListenerEnabled,
                onToggle = viewModel::setPushListenerEnabled,
            )
            if (state.pushListenerEnabled) {
                Spacer(Modifier.height(8.dp))
                if (pushGranted) {
                    Text(
                        "Доступ разрешён — уведомления обрабатываются",
                        style = FosType.Micro,
                        color = FosColors.Positive,
                    )
                } else {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Требуется доступ к уведомлениям",
                            style    = FosType.Micro,
                            color    = FosColors.Negative,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick        = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp, vertical = 0.dp
                            ),
                        ) {
                            Text("Открыть", style = FosType.Label, color = FosColors.Info)
                        }
                    }
                }
            }
        }

        // ── Operations from SMS ──────────────────────────────────────────────────
        SettingsSection(title = "ОПЕРАЦИИ ИЗ SMS") {
            ToggleRow(
                label    = "Читать входящие SMS",
                sublabel = "Новые операции добавляются автоматически",
                checked  = state.smsRealtimeEnabled,
                onToggle = { enabled ->
                    viewModel.setSmsRealtimeEnabled(enabled)
                    if (enabled) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.READ_SMS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) smsImportLauncher.launch(arrayOf(
                            android.Manifest.permission.READ_SMS,
                            android.Manifest.permission.RECEIVE_SMS,
                        ))
                    }
                },
            )
            HorizontalDivider(
                color = FosColors.Border, thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            val importing = smsImport is SmsImportUi.Running
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !importing) { startSmsImport() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Импортировать за 90 дней", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text(
                        when (val s = smsImport) {
                            is SmsImportUi.Running -> "Импорт… ${(s.progress * 100).toInt()}%"
                            is SmsImportUi.Done    -> "Готово — добавлено операций: ${s.imported}"
                            SmsImportUi.Idle       -> "Разовая загрузка истории из SMS банка"
                        },
                        style = FosType.Micro,
                        color = if (smsImport is SmsImportUi.Done) FosColors.Positive else FosColors.TextMuted,
                    )
                }
                Text(
                    if (importing) "…" else "↻",
                    style = FosType.BodySemi,
                    color = FosColors.Info,
                )
            }
        }

        // ── Backup / restore ─────────────────────────────────────────────────────
        SettingsSection(title = "РЕЗЕРВНАЯ КОПИЯ") {
            val working = backup is BackupUi.Working
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !working) {
                        viewModel.dismissBackup()
                        createBackupLauncher.launch(viewModel.suggestedBackupName)
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Создать резервную копию", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text("Счета, карты, операции, цели, бюджеты", style = FosType.Micro, color = FosColors.TextMuted)
                }
                Text("↑", style = FosType.BodySemi, color = FosColors.Info)
            }
            HorizontalDivider(
                color = FosColors.Border, thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !working) {
                        viewModel.dismissBackup()
                        restoreBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Восстановить из файла", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text("Загрузить данные из резервной копии", style = FosType.Micro, color = FosColors.TextMuted)
                }
                Text("↓", style = FosType.BodySemi, color = FosColors.Info)
            }
            when (val b = backup) {
                BackupUi.Working      -> StatusLine("Обработка…", FosColors.TextSecondary)
                is BackupUi.Success   -> StatusLine(b.message, FosColors.Positive)
                is BackupUi.Error     -> StatusLine(b.message, FosColors.Negative)
                BackupUi.Idle         -> {}
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

        // ── Data ────────────────────────────────────────────────────────────────
        SettingsSection(title = "ДАННЫЕ") {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoriesClick() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Категории", style = FosType.BodySemi, color = FosColors.TextPrimary)
                    Text("Создавать и удалять категории", style = FosType.Micro, color = FosColors.TextMuted)
                }
                Text("›", style = FosType.BodySemi, color = FosColors.TextSecondary)
            }
            HorizontalDivider(
                color     = FosColors.Border,
                thickness = 0.5.dp,
                modifier  = Modifier.padding(vertical = 4.dp),
            )
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { showDeleteConfirm = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Удалить историю операций", style = FosType.BodySemi, color = FosColors.Negative)
                    Text("Безвозвратно удалит все транзакции", style = FosType.Micro, color = FosColors.TextMuted)
                }
                Text("›", style = FosType.BodySemi, color = FosColors.Negative)
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor   = FosColors.Surface,
                title = {
                    Text("Удалить всю историю?", style = FosType.BodySemi, color = FosColors.TextPrimary)
                },
                text = {
                    Text(
                        "Все операции будут удалены без возможности восстановления. " +
                        "Повторный импорт SMS вернёт историю из банка.",
                        style = FosType.Body,
                        color = FosColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteAllHistory()
                        showDeleteConfirm = false
                    }) {
                        Text("Удалить", color = FosColors.Negative)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Отмена", color = FosColors.TextSecondary)
                    }
                },
            )
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
private fun StatusLine(text: String, color: Color) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = FosType.Micro, color = color)
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
