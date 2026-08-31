package com.financeos.hub.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("fos_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Единственная точка чтения настроек — и она не имеет права падать.
     *
     * `DataStore.data` бросает `IOException`, если файл повреждён или недоступен (недописанная
     * запись при внезапном выключении — самый частый случай). Раньше это исключение уходило вверх
     * по каждому потоку настроек, а читают их в том числе `PushNotificationListener` и
     * `SmsReceiver` — то есть одна битая запись на диске означала, что КАЖДЫЙ пуш и КАЖДАЯ SMS
     * молча перестают обрабатываться, и увидеть это нельзя ничем, кроме отсутствия операций.
     *
     * Теперь такой случай откатывается к пустым настройкам: приложение ведёт себя как свежая
     * установка (все опасные тумблеры выключены по умолчанию), но продолжает работать. Всё, что не
     * `IOException`, пробрасывается дальше — это настоящая ошибка, и прятать её нельзя. Отмену
     * корутины `catch` не перехватывает по устройству самого Flow.
     */
    private val prefs: Flow<Preferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    companion object Keys {
        val ONBOARDING_COMPLETE       = booleanPreferencesKey("onboarding_complete")
        val HERO_VARIANT              = stringPreferencesKey("hero_variant")  // CALM | CONTRAST | MINIMAL
        val BIOMETRIC_ENABLED         = booleanPreferencesKey("biometric_enabled")
        val DEFAULT_CURRENCY          = stringPreferencesKey("default_currency")
        val LAST_IMPORT_AT            = stringPreferencesKey("last_import_at")
        val NOTIFICATIONS_ENABLED     = booleanPreferencesKey("notifications_enabled")
        val BUDGET_ALERT_THRESHOLD    = stringPreferencesKey("budget_alert_threshold") // "80" default
        // Budget-alert throttling. These MUST be persisted: the tracker used to live in the
        // BudgetViewModel, which is recreated on every navigation to the Budget screen — so each
        // visit re-fired the same alert (the reported "приходит 10 раз").
        /** Epoch-day the counter below belongs to; a new day resets it. */
        val BUDGET_ALERT_DAY          = stringPreferencesKey("budget_alert_day")
        /** How many budget alerts were already shown on [BUDGET_ALERT_DAY]. Hard cap: 2/day. */
        val BUDGET_ALERT_COUNT        = stringPreferencesKey("budget_alert_count")
        /** CSV of "<budgetId>:<YYYY-MM>" already alerted, so one budget alerts once per month. */
        val BUDGET_ALERTED_KEYS       = stringPreferencesKey("budget_alerted_keys")
        /**
         * Неприкосновенный остаток: ниже него «Свободно» не должно опускаться.
         *
         * Это НЕ цель. Цель — то, что вы копите и однажды потратите; резерв — пол, который не
         * пробивают. Строкой, как и остальные числа в настройках: копейки в Long тут не нужны, а
         * тип ключа один на все «числа как текст».
         */
        val FREE_MONEY_RESERVE         = stringPreferencesKey("free_money_reserve_kopecks")
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

    val onboardingComplete: Flow<Boolean> = prefs
        .map { it[ONBOARDING_COMPLETE] ?: false }

    val heroVariant: Flow<String> = prefs
        .map { it[HERO_VARIANT] ?: "CALM" }

    val biometricEnabled: Flow<Boolean> = prefs
        .map { it[BIOMETRIC_ENABLED] ?: false }

    val notificationsEnabled: Flow<Boolean> = prefs
        .map { it[NOTIFICATIONS_ENABLED] ?: true }

    val budgetAlertThreshold: Flow<Int> = prefs
        .map { it[BUDGET_ALERT_THRESHOLD]?.toIntOrNull() ?: 80 }

    /** Резерв в копейках. По умолчанию 0 — приложение не выдумывает за человека, сколько ему нужно. */
    val freeMoneyReserve: Flow<Long> = prefs
        .map { it[FREE_MONEY_RESERVE]?.toLongOrNull() ?: 0L }

    val mlClassificationEnabled: Flow<Boolean> = prefs
        .map { it[ML_CLASSIFICATION_ENABLED] ?: false }

    val pushListenerEnabled: Flow<Boolean> = prefs
        .map { it[PUSH_LISTENER_ENABLED] ?: false }

    /** Real-time capture of incoming bank SMS. Off by default so a fresh install never
     *  silently fills up with operations before the user has set anything up. */
    val smsRealtimeEnabled: Flow<Boolean> = prefs
        .map { it[SMS_REALTIME_ENABLED] ?: false }

    val lastImportAt: Flow<String?> = prefs
        .map { it[LAST_IMPORT_AT] }

    /** «Анимации» — плавные переходы, счётчики чисел, объёмные карты, отклик касания. Off by default. */
    val animationsEnabled: Flow<Boolean> = prefs
        .map { it[ANIMATIONS_ENABLED] ?: false }

    /** «Атмосфера Мерцание» — светлячки, свечение, глубина. Off by default. */
    val atmosphereEnabled: Flow<Boolean> = prefs
        .map { it[ATMOSPHERE_ENABLED] ?: false }

    /** Bank cards: variant B (deep glass) when true, variant A (holographic) when false. */
    val cardsVariantB: Flow<Boolean> = prefs
        .map { it[CARDS_VARIANT_B] ?: false }

    /** «Кот-режим» — мяу-маскот в герое + следы лапок вместо светлячков. Off by default. */
    val catModeEnabled: Flow<Boolean> = prefs
        .map { it[CAT_MODE_ENABLED] ?: false }

    /** Уведомлять о новой версии приложения. On by default. */
    val updateNotificationsEnabled: Flow<Boolean> = prefs
        .map { it[UPDATE_NOTIFICATIONS_ENABLED] ?: true }

    /** Last release version the user was already notified about (null if never). */
    val lastNotifiedVersion: Flow<String?> = prefs
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

    /** Persisted budget-alert throttle state: (epochDay, alertsSentToday, alertedKeys). */
    data class BudgetAlertState(
        val epochDay   : Long,
        val countToday : Int,
        val alertedKeys: Set<String>,
    )

    suspend fun budgetAlertState(): BudgetAlertState {
        // Через тот же защищённый поток: битый файл здесь означал бы падение при каждом пересчёте
        // бюджета, а не потерянный счётчик оповещений.
        val snapshot = prefs.first()
        return BudgetAlertState(
            epochDay    = snapshot[BUDGET_ALERT_DAY]?.toLongOrNull() ?: 0L,
            countToday  = snapshot[BUDGET_ALERT_COUNT]?.toIntOrNull() ?: 0,
            alertedKeys = snapshot[BUDGET_ALERTED_KEYS]
                ?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
        )
    }

    suspend fun saveBudgetAlertState(state: BudgetAlertState) {
        context.dataStore.edit {
            it[BUDGET_ALERT_DAY]    = state.epochDay.toString()
            it[BUDGET_ALERT_COUNT]  = state.countToday.toString()
            // Keep the list bounded — only the current month's keys matter.
            it[BUDGET_ALERTED_KEYS] = state.alertedKeys.take(50).joinToString("|")
        }
    }

    suspend fun setFreeMoneyReserve(kopecks: Long) {
        context.dataStore.edit { it[FREE_MONEY_RESERVE] = kopecks.coerceAtLeast(0L).toString() }
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
