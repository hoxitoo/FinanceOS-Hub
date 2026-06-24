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
        val ML_CLASSIFICATION_ENABLED  = booleanPreferencesKey("ml_classification_enabled")
        val PUSH_LISTENER_ENABLED      = booleanPreferencesKey("push_listener_enabled")
        val SMS_REALTIME_ENABLED       = booleanPreferencesKey("sms_realtime_enabled")

        // ── Кастомизация («Мерцание») ─────────────────────────────────────────
        /** Tumbler 1: event-driven motion (count-up, transitions, holo cards, touch ripple). */
        val ANIMATIONS_ENABLED         = booleanPreferencesKey("animations_enabled")
        /** Tumbler 2: ambient atmosphere (fireflies, glow, breathing, depth). */
        val ATMOSPHERE_ENABLED         = booleanPreferencesKey("atmosphere_enabled")
        /** Conditional sub-tumbler under #1: bank cards use variant B (deep glass) instead of A (holographic). */
        val CARDS_VARIANT_B            = booleanPreferencesKey("cards_variant_b")
        /** «Кот-режим»: a mood-matched cat mascot in the hero + paw-print particles. */
        val CAT_MODE_ENABLED           = booleanPreferencesKey("cat_mode_enabled")

        // ── Обновления ─────────────────────────────────────────────────────────
        /** Background check for a new GitHub release → push notification. Default ON. */
        val UPDATE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("update_notifications_enabled")
        /** Last release version we already notified about — prevents re-notifying every cycle. */
        val LAST_NOTIFIED_VERSION        = stringPreferencesKey("last_notified_version")
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

    val pushListenerEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[PUSH_LISTENER_ENABLED] ?: false }

    /** Real-time capture of incoming bank SMS. Off by default so a fresh install never
     *  silently fills up with operations before the user has set anything up. */
    val smsRealtimeEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[SMS_REALTIME_ENABLED] ?: false }

    val lastImportAt: Flow<String?> = context.dataStore.data
        .map { it[LAST_IMPORT_AT] }

    /** «Анимации» — плавные переходы, счётчики чисел, объёмные карты, отклик касания. Off by default. */
    val animationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[ANIMATIONS_ENABLED] ?: false }

    /** «Атмосфера Мерцание» — светлячки, свечение, глубина. Off by default. */
    val atmosphereEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[ATMOSPHERE_ENABLED] ?: false }

    /** Bank cards: variant B (deep glass) when true, variant A (holographic) when false. */
    val cardsVariantB: Flow<Boolean> = context.dataStore.data
        .map { it[CARDS_VARIANT_B] ?: false }

    /** «Кот-режим» — мяу-маскот в герое + следы лапок вместо светлячков. Off by default. */
    val catModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[CAT_MODE_ENABLED] ?: false }

    /** Уведомлять о новой версии приложения. On by default. */
    val updateNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[UPDATE_NOTIFICATIONS_ENABLED] ?: true }

    /** Last release version the user was already notified about (null if never). */
    val lastNotifiedVersion: Flow<String?> = context.dataStore.data
        .map { it[LAST_NOTIFIED_VERSION] }

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

    suspend fun setPushListenerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PUSH_LISTENER_ENABLED] = enabled }
    }

    suspend fun setSmsRealtimeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SMS_REALTIME_ENABLED] = enabled }
    }

    suspend fun setLastImportAt(iso: String) {
        context.dataStore.edit { it[LAST_IMPORT_AT] = iso }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setAtmosphereEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ATMOSPHERE_ENABLED] = enabled }
    }

    suspend fun setCardsVariantB(enabled: Boolean) {
        context.dataStore.edit { it[CARDS_VARIANT_B] = enabled }
    }

    suspend fun setCatModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CAT_MODE_ENABLED] = enabled }
    }

    suspend fun setUpdateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[UPDATE_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLastNotifiedVersion(version: String) {
        context.dataStore.edit { it[LAST_NOTIFIED_VERSION] = version }
    }
}
