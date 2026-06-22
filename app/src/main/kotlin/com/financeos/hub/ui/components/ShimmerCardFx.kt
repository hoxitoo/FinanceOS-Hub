package com.financeos.hub.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.financeos.hub.ui.theme.FosColors

/**
 * Device-tilt signal (accelerometer), normalised to roughly ±1 and low-pass filtered.
 * The resting orientation captured on the first event becomes the neutral (0,0), so the effect is
 * "tilt relative to however you're holding the phone" rather than absolute gravity. No-op (returns
 * [Offset.Zero]) when [active] is false, so the sensor is only registered while a card needs it.
 */
@Composable
private fun rememberDeviceTilt(active: Boolean): Offset {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(active) {
        if (!active) {
            tilt = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseX = Float.NaN
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (baseX.isNaN()) { baseX = e.values[0]; baseY = e.values[1] }
                // Deviation from rest in m/s²; full tilt at ~4 m/s² (~24°).
                val nx = ((e.values[0] - baseX) / 4f).coerceIn(-1f, 1f)
                val ny = ((e.values[1] - baseY) / 4f).coerceIn(-1f, 1f)
                // Exponential smoothing to kill accelerometer jitter.
                tilt = Offset(tilt.x + (nx - tilt.x) * 0.25f, tilt.y + (ny - tilt.y) * 0.25f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}

/** Subtle 3-D parallax driven by device tilt, capped at ±8°. No-op when [active] is false. */
@Composable
fun Modifier.shimmerTilt(active: Boolean): Modifier {
    if (!active) return this
    val tilt = rememberDeviceTilt(true)
    val density = LocalDensity.current.density
    val rotY by animateFloatAsState((tilt.x * 8f).coerceIn(-8f, 8f), spring(), label = "tiltY")
    val rotX by animateFloatAsState((-tilt.y * 8f).coerceIn(-8f, 8f), spring(), label = "tiltX")
    return this.graphicsLayer {
        rotationY = rotY
        rotationX = rotX
        cameraDistance = 16f * density
    }
}

/**
 * Light sweep drawn over a bank card. Call inside the card's [Box].
 *  - [holographic] (variant A): a faster indigo band sweeping diagonally — max character.
 *  - [glass] (variant B): a slower, calmer white gloss over a matte frost — cheaper for battery.
 * Both are no-ops when their flag is false, so the Standard card is untouched.
 */
@Composable
fun BoxScope.ShimmerCardSheen(holographic: Boolean, glass: Boolean) {
    if (!holographic && !glass) return

    // Variant B mutes the brand a touch for a "deep glass" feel.
    if (glass) {
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.12f)))
    }

    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val density = LocalDensity.current
        val wPx  = with(density) { maxWidth.toPx() }
        val hPx  = with(density) { maxHeight.toPx() }
        val band = wPx * 0.45f

        val infinite = rememberInfiniteTransition(label = "sheen")
        val pos by infinite.animateFloat(
            initialValue  = -band,
            targetValue   = wPx + band,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = if (glass) 5200 else 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sheenPos",
        )
        val sheen = if (glass) Color.White.copy(alpha = 0.10f)
                    else       FosColors.Shimmer.GlowIndigo.copy(alpha = 0.18f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, sheen, Color.Transparent),
                        start  = Offset(pos, 0f),
                        end    = Offset(pos + band, hPx),
                    )
                )
        )
    }
}
