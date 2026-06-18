package com.financeos.hub.features.onboarding

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.core.sms.SmsReader
import com.financeos.hub.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { WELCOME, IMPORT, DONE }

data class OnboardingState(
    val step           : OnboardingStep = OnboardingStep.WELCOME,
    val importProgress : Float          = 0f,
    val done           : Boolean        = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application,
    private val smsReader: SmsReader,
    private val prefs: UserPreferences,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.onboardingComplete.collect { done ->
                if (done) _state.update { it.copy(done = true) }
            }
        }
    }

    fun onRequestSmsPermission() {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startImport()
        else _state.update { it.copy(step = OnboardingStep.IMPORT) }
    }

    fun onSmsPermissionGranted() = startImport()

    private fun startImport() {
        _state.update { it.copy(step = OnboardingStep.IMPORT) }
        viewModelScope.launch {
            smsReader.importLast90Days().collect { progress ->
                val pct = if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f
                _state.update { it.copy(importProgress = pct) }
                if (progress.done) {
                    prefs.setOnboardingComplete(true)
                    _state.update { it.copy(step = OnboardingStep.DONE, done = true) }
                }
            }
        }
    }
}
