package com.financeos.hub.features.onboarding

import android.app.Application
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
    val step             : OnboardingStep = OnboardingStep.WELCOME,
    val importProgress   : Float          = 0f,
    val done             : Boolean        = false,
    val permissionDenied : Boolean        = false,
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

    /** Called by the UI after the system permission dialog returns GRANTED. */
    fun onSmsPermissionGranted() = startImport()

    /** Called by the UI when the system dialog returns DENIED. */
    fun onPermissionDenied() {
        _state.update { it.copy(permissionDenied = true) }
    }

    /** Skip SMS import entirely — still marks onboarding complete. */
    fun onSkip() {
        viewModelScope.launch {
            prefs.setOnboardingComplete(true)
            _state.update { it.copy(step = OnboardingStep.DONE, done = true) }
        }
    }

    private fun startImport() {
        _state.update { it.copy(step = OnboardingStep.IMPORT, permissionDenied = false) }
        viewModelScope.launch {
            // The user explicitly chose to read SMS — turn on real-time capture too so new
            // operations keep flowing in. (Skipping leaves it off; see onSkip.)
            prefs.setSmsRealtimeEnabled(true)
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
