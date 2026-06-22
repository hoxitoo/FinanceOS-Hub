package com.financeos.hub.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.financeos.hub.data.preferences.UserPreferences

/**
 * Resolved state of the «Мерцание» (Shimmer) customization layer.
 *
 * This is the single source of truth every composable reads (via [LocalShimmer]) to decide
 * whether to draw a given effect. It folds three inputs together:
 *  - user preferences — the two toggles in Settings → «Кастомизация» (+ the conditional cards one)
 *  - system reduce-motion — vechnye/infinite animations are suppressed
 *  - power-save mode — particles off, bank cards forced to the cheaper glass variant
 *
 * Defaults are all-off, so a fresh install renders exactly the current (Standard) theme.
 */
@Immutable
data class ShimmerConfig(
    val animations   : Boolean = false,   // Tumbler 1
    val atmosphere   : Boolean = false,   // Tumbler 2
    val cardsVariantB: Boolean = false,   // conditional sub-toggle under #1
    val reduceMotion : Boolean = false,
    val powerSave    : Boolean = false,
) {
    // ── Tumbler 1 · Анимации (event-driven motion) ────────────────────────────
    val countUp           : Boolean get() = animations
    val screenTransitions : Boolean get() = animations
    val touchRipple       : Boolean get() = animations
    /** Holographic unless the user picked B, or power-save forces the cheaper glass variant. */
    val holographicCards  : Boolean get() = animations && !cardsVariantB && !powerSave
    val glassCards        : Boolean get() = animations && (cardsVariantB || powerSave)

    // ── Tumbler 2 · Атмосфера «Мерцание» (ambient) ────────────────────────────
    val particles         : Boolean get() = atmosphere && !powerSave
    /** Infinite opacity pulse of particles — gated behind reduce-motion. */
    val particlePulse     : Boolean get() = atmosphere && !reduceMotion && !powerSave
    val surfaceGlow       : Boolean get() = atmosphere
    val heroBreathing     : Boolean get() = atmosphere && !reduceMotion
    /** Depth-of-field timeline is static per-position, so it is safe even under reduce-motion. */
    val depthTimeline     : Boolean get() = atmosphere
    val insightBorderGlow : Boolean get() = atmosphere
    val semanticGlow      : Boolean get() = atmosphere   // phase 3
    val currencyReef      : Boolean get() = atmosphere   // phase 3

    companion object { val Off = ShimmerConfig() }
}

/** Read everywhere via `LocalShimmer.current`. Defaults to [ShimmerConfig.Off] = Standard theme. */
val LocalShimmer = staticCompositionLocalOf { ShimmerConfig.Off }

/**
 * Reactive "remove animations" state — mirrors [rememberPowerSave] pattern.
 * A [ContentObserver] on ANIMATOR_DURATION_SCALE fires on every accessibility-settings change
 * so the shimmer layer reacts live (no app restart needed), consistent with power-save behaviour.
 */
@Composable
private fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    val initial = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f
    var reduceMotion by remember { mutableStateOf(initial) }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val scale = Settings.Global.getFloat(
                    context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
                )
                reduceMotion = (scale == 0f)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

/** Reactive battery-saver state — flips live when the user toggles power-save. */
@Composable
private fun rememberPowerSave(): Boolean {
    val context = LocalContext.current
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    var saving by remember { mutableStateOf(pm?.isPowerSaveMode ?: false) }
    DisposableEffect(pm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { saving = pm?.isPowerSaveMode ?: false }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}

/**
 * Reads the customization prefs + live system state and provides a resolved [ShimmerConfig]
 * down the tree. Wrap the app content with this (inside [FosTheme]).
 */
@Composable
fun ProvideShimmer(prefs: UserPreferences, content: @Composable () -> Unit) {
    val animations   by prefs.animationsEnabled.collectAsState(initial = false)
    val atmosphere   by prefs.atmosphereEnabled.collectAsState(initial = false)
    val cardsVariantB by prefs.cardsVariantB.collectAsState(initial = false)
    val reduceMotion = rememberSystemReduceMotion()
    val powerSave    = rememberPowerSave()

    val config = remember(animations, atmosphere, cardsVariantB, reduceMotion, powerSave) {
        ShimmerConfig(
            animations    = animations,
            atmosphere    = atmosphere,
            cardsVariantB = cardsVariantB,
            reduceMotion  = reduceMotion,
            powerSave     = powerSave,
        )
    }
    CompositionLocalProvider(LocalShimmer provides config, content = content)
}
