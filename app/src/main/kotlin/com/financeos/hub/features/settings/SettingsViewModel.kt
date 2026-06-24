package com.financeos.hub.features.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.backup.BackupManager
import com.financeos.hub.core.sms.SmsReader
import com.financeos.hub.core.update.UpdateChecker
import com.financeos.hub.data.preferences.UserPreferences
import com.financeos.hub.data.repositories.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val heroVariant            : String  = "CALM",
    val biometricEnabled       : Boolean = false,
    val notificationsEnabled   : Boolean = true,
    val budgetAlertThreshold   : Int     = 80,
    val mlClassificationEnabled: Boolean = false,
    val pushListenerEnabled    : Boolean = false,
    val smsRealtimeEnabled     : Boolean = false,
    val lastImportAt           : String? = null,
    // Кастомизация («Мерцание»)
    val animationsEnabled      : Boolean = false,
    val atmosphereEnabled      : Boolean = false,
    val cardsVariantB          : Boolean = false,
    val catModeEnabled         : Boolean = false,
)

/** Transient status of the manual 90-day SMS import. */
sealed interface SmsImportUi {
    data object Idle : SmsImportUi
    data class Running(val progress: Float) : SmsImportUi
    data class Done(val imported: Int) : SmsImportUi
}

/** Transient status of a backup export / restore. */
sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    data class Success(val message: String) : BackupUi
    data class Error(val message: String) : BackupUi
}

/** Transient status of the «Проверить обновления» flow. */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val release: UpdateChecker.Release) : UpdateUi
    data class Downloading(val progress: Float) : UpdateUi
    data class ReadyToInstall(val release: UpdateChecker.Release) : UpdateUi
    data class Error(val message: String) : UpdateUi
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs        : UserPreferences,
    private val txRepo       : TransactionRepository,
    private val smsReader    : SmsReader,
    private val backupManager: BackupManager,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    val state = combine(
        prefs.heroVariant,
        prefs.biometricEnabled,
        prefs.notificationsEnabled,
        combine(
            prefs.budgetAlertThreshold,
            prefs.mlClassificationEnabled,
            prefs.pushListenerEnabled,
            prefs.smsRealtimeEnabled,
            prefs.lastImportAt,
        ) { threshold, ml, push, sms, last ->
            listOf<Any?>(threshold, ml, push, sms, last)
        },
        combine(
            prefs.animationsEnabled,
            prefs.atmosphereEnabled,
            prefs.cardsVariantB,
            prefs.catModeEnabled,
        ) { anim, atmo, cardsB, cat ->
            listOf(anim, atmo, cardsB, cat)
        },
    ) { hero, bio, notif, rest, custom ->
        SettingsState(
            heroVariant             = hero,
            biometricEnabled        = bio,
            notificationsEnabled    = notif,
            budgetAlertThreshold    = rest[0] as Int,
            mlClassificationEnabled = rest[1] as Boolean,
            pushListenerEnabled     = rest[2] as Boolean,
            smsRealtimeEnabled      = rest[3] as Boolean,
            lastImportAt            = rest[4] as String?,
            animationsEnabled       = custom[0],
            atmosphereEnabled       = custom[1],
            cardsVariantB           = custom[2],
            catModeEnabled          = custom[3],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _smsImport = MutableStateFlow<SmsImportUi>(SmsImportUi.Idle)
    val smsImport = _smsImport.asStateFlow()

    private val _backup = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val backup = _backup.asStateFlow()

    fun setHeroVariant(variant: String) = viewModelScope.launch {
        prefs.setHeroVariant(variant)
    }

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setBiometricEnabled(enabled)
    }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setNotificationsEnabled(enabled)
    }

    fun setBudgetAlertThreshold(pct: Int) = viewModelScope.launch {
        prefs.setBudgetAlertThreshold(pct)
    }

    fun setMlClassificationEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setMlClassificationEnabled(enabled)
    }

    fun setPushListenerEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setPushListenerEnabled(enabled)
    }

    fun setSmsRealtimeEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSmsRealtimeEnabled(enabled)
    }

    fun setAnimationsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAnimationsEnabled(enabled)
    }

    fun setAtmosphereEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAtmosphereEnabled(enabled)
    }

    fun setCardsVariantB(enabled: Boolean) = viewModelScope.launch {
        prefs.setCardsVariantB(enabled)
    }

    fun setCatModeEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCatModeEnabled(enabled)
    }

    fun deleteAllHistory() = viewModelScope.launch(Dispatchers.IO) {
        txRepo.deleteAllHistory()
    }

    /** Manual 90-day SMS import — call after the READ_SMS permission has been granted. */
    fun importSmsHistory() = viewModelScope.launch {
        _smsImport.value = SmsImportUi.Running(0f)
        smsReader.importLast90Days().collect { progress ->
            val pct = if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f
            _smsImport.value = SmsImportUi.Running(pct)
            if (progress.done) _smsImport.value = SmsImportUi.Done(progress.imported)
        }
    }

    fun dismissSmsImport() { _smsImport.value = SmsImportUi.Idle }

    // ─── Backup / restore ─────────────────────────────────────────────────────

    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        _backup.value = BackupUi.Working
        _backup.value = runCatching { backupManager.exportTo(uri) }
            .fold(
                onSuccess = { BackupUi.Success("Резервная копия сохранена") },
                onFailure = { BackupUi.Error(it.message ?: "Не удалось сохранить") },
            )
    }

    fun restoreBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        _backup.value = BackupUi.Working
        _backup.value = runCatching { backupManager.restoreFrom(uri) }
            .fold(
                onSuccess = { c -> BackupUi.Success("Восстановлено: ${c.accounts} счетов, ${c.transactions} операций") },
                onFailure = { BackupUi.Error(it.message ?: "Не удалось восстановить") },
            )
    }

    fun dismissBackup() { _backup.value = BackupUi.Idle }

    val suggestedBackupName: String get() = BackupManager.suggestedFileName()

    // ─── App updates ──────────────────────────────────────────────────────────

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update = _update.asStateFlow()

    private var downloadedApk: java.io.File? = null

    /** Version currently installed (suffix-stripped), shown in the «О приложении» row. */
    val currentVersion: String get() = updateChecker.currentVersion

    fun checkForUpdates() = viewModelScope.launch {
        _update.value = UpdateUi.Checking
        _update.value = when (val r = updateChecker.check()) {
            is UpdateChecker.CheckResult.UpToDate  -> UpdateUi.UpToDate
            is UpdateChecker.CheckResult.Available -> UpdateUi.Available(r.release)
            is UpdateChecker.CheckResult.Error     -> UpdateUi.Error(r.message)
            else                                   -> UpdateUi.Idle
        }
    }

    fun downloadUpdate(release: UpdateChecker.Release) = viewModelScope.launch {
        _update.value = UpdateUi.Downloading(0f)
        runCatching {
            updateChecker.download(release) { p -> _update.value = UpdateUi.Downloading(p) }
        }.fold(
            onSuccess = { file ->
                downloadedApk = file
                _update.value = UpdateUi.ReadyToInstall(release)
            },
            onFailure = { _update.value = UpdateUi.Error(it.message ?: "Не удалось загрузить обновление") },
        )
    }

    /** Returns true if the install intent was launched; false if the OS blocks unknown sources. */
    fun installUpdate(): Boolean {
        val file = downloadedApk ?: return false
        if (!updateChecker.canInstall()) {
            updateChecker.openUnknownSourcesSettings()
            return false
        }
        updateChecker.installApk(file)
        return true
    }

    fun dismissUpdate() { _update.value = UpdateUi.Idle }
}
