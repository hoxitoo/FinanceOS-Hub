package com.financeos.hub.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("fos_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object Keys {
        val ONBOARDING_COMPLETE       = booleanPreferencesKey("onboarding_complete")
        val HERO_VARIANT              = stringPreferencesKey("hero_variant")  // CALM | CONTRAST | MINIMAL
        val BIOMETRIC_ENABLED         = booleanPreferencesKey("biometric_enabled")
        val DEFAULT_CURRENCY          = stringPreferencesKey("default_currency")
        val LAST_IMPORT_AT            = stringPreferencesKey("last_import_at")
        val NOTIFICATIONS_ENABLED     = booleanPreferencesKey("notifications_enabled")
        val BUDGET_ALERT_THRESHOLD    = stringPreferencesKey("budget_alert_threshold") // "80" default
        val ML_CLASSIFICATION_ENABLED = booleanPreferencesKey("ml_classification_enabled")
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETE] ?: false }

    val heroVariant: Flow<String> = context.dataStore.data
        .map { it[HERO_VARIANT] ?: "CALM" }

    val biometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[BIOMETRIC_ENABLED] ?: false }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIFICATIONS_ENABLED] ?: true }

    val budgetAlertThreshold: Flow<Int> = context.dataStore.data
        .map { it[BUDGET_ALERT_THRESHOLD]?.toIntOrNull() ?: 80 }

    val mlClassificationEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[ML_CLASSIFICATION_ENABLED] ?: false }

    val lastImportAt: Flow<String?> = context.dataStore.data
        .map { it[LAST_IMPORT_AT] }

    suspend fun setOnboardingComplete(done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = done }
    }

    suspend fun setHeroVariant(variant: String) {
        context.dataStore.edit { it[HERO_VARIANT] = variant }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBudgetAlertThreshold(pct: Int) {
        context.dataStore.edit { it[BUDGET_ALERT_THRESHOLD] = pct.toString() }
    }

    suspend fun setMlClassificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ML_CLASSIFICATION_ENABLED] = enabled }
    }

    suspend fun setLastImportAt(iso: String) {
        context.dataStore.edit { it[LAST_IMPORT_AT] = iso }
    }
}
