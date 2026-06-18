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
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val HERO_VARIANT        = stringPreferencesKey("hero_variant")  // CALM | CONTRAST | MINIMAL
        val BIOMETRIC_ENABLED   = booleanPreferencesKey("biometric_enabled")
        val DEFAULT_CURRENCY    = stringPreferencesKey("default_currency")
        val LAST_IMPORT_AT      = stringPreferencesKey("last_import_at")
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETE] ?: false }

    val heroVariant: Flow<String> = context.dataStore.data
        .map { it[HERO_VARIANT] ?: "CALM" }

    val biometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[BIOMETRIC_ENABLED] ?: false }

    suspend fun setOnboardingComplete(done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = done }
    }

    suspend fun setHeroVariant(variant: String) {
        context.dataStore.edit { it[HERO_VARIANT] = variant }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setLastImportAt(iso: String) {
        context.dataStore.edit { it[LAST_IMPORT_AT] = iso }
    }
}
