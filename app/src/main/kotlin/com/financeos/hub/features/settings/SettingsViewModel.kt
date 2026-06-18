package com.financeos.hub.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financeos.hub.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val heroVariant           : String  = "CALM",
    val biometricEnabled      : Boolean = false,
    val notificationsEnabled  : Boolean = true,
    val budgetAlertThreshold  : Int     = 80,
    val mlClassificationEnabled: Boolean = false,
    val lastImportAt          : String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    val state = combine(
        prefs.heroVariant,
        prefs.biometricEnabled,
        prefs.notificationsEnabled,
        combine(
            prefs.budgetAlertThreshold,
            prefs.mlClassificationEnabled,
            prefs.lastImportAt,
        ) { threshold, ml, last -> Triple(threshold, ml, last) },
    ) { hero, bio, notif, rest ->
        SettingsState(
            heroVariant             = hero,
            biometricEnabled        = bio,
            notificationsEnabled    = notif,
            budgetAlertThreshold    = rest.first,
            mlClassificationEnabled = rest.second,
            lastImportAt            = rest.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

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
}
