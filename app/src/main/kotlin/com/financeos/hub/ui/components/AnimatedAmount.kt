package com.financeos.hub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.financeos.hub.ui.theme.FosFormatter
import com.financeos.hub.ui.theme.LocalShimmer

/**
 * Money [Text] that counts up to its value when the «Анимации» layer is on (otherwise renders
 * instantly — identical to a plain [Text], so the Standard theme is unchanged).
 *
 * Two correctness details:
 *  - The value is interpolated in **Long** space (via a 0..1 Float progress), never as a Float of
 *    the kopecks themselves — Compose animation vectors are Float-based and would lose precision
 *    for large balances. The final frame is therefore always the exact target.
 *  - Re-targeting mid-flight re-anchors at the currently-shown value, so a balance that changes
 *    while still animating rolls smoothly instead of jumping. The data layer already debounces
 *    (DashboardViewModel `debounce(500)`), so this never "slot-machines" during a batch import.
 */
@Composable
fun AnimatedAmount(
    kopecks  : Long,
    style    : TextStyle,
    color    : Color,
    modifier : Modifier = Modifier,
    currency : String = "₽",
    formatter: (Long) -> String = { FosFormatter.amount(it, currency) },
) {
    val enabled = LocalShimmer.current.countUp
    if (!enabled) {
        Text(formatter(kopecks), style = style, color = color, modifier = modifier)
        return
    }

    val progress = remember { Animatable(0f) }
    var fromVal by remember { mutableStateOf(0L) }
    var toVal   by remember { mutableStateOf(0L) }
    LaunchedEffect(kopecks) {
        fromVal  = fromVal + ((toVal - fromVal) * progress.value.toDouble()).toLong()
        toVal    = kopecks
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val shown = fromVal + ((toVal - fromVal) * progress.value.toDouble()).toLong()
    Text(formatter(shown), style = style, color = color, modifier = modifier)
}
