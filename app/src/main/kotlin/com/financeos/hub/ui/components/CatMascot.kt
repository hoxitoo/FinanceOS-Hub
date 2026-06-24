package com.financeos.hub.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.financeos.hub.R
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * «Кот-режим» — the mascot's mood follows the user's financial health score, mirroring exactly the
 * tiers [ScoreRing] uses for its colour (≥70 / 40–69 / 20–39 / <20). One enum entry per state keeps
 * the mapping in a single place so the cat and the ring never disagree.
 */
enum class CatMood(val drawableRes: Int, val caption: String) {
    EXCITED (R.drawable.cat_mood_excited,  "Отлично!"),   // score ≥ 70 — milestone / healthy
    NEUTRAL (R.drawable.cat_mood_neutral,  "Спокойно"),   // 40–69 — steady
    SAD     (R.drawable.cat_mood_sad,      "Грустно"),    // 20–39 — overspending
    SLEEPING(R.drawable.cat_mood_sleeping, "Спит…"),      // < 20 — low activity / poor health
}

/** Maps a 0–100 financial score to a [CatMood]. Identical thresholds to [ScoreRing]'s colour tiers. */
fun catMoodFor(score: Int): CatMood = when {
    score >= 70 -> CatMood.EXCITED
    score >= 40 -> CatMood.NEUTRAL
    score >= 20 -> CatMood.SAD
    else        -> CatMood.SLEEPING
}

/**
 * The cat mascot, mood-matched to [score]. Drawn as a static image, with an optional gentle idle
 * "bob" (±2 % vertical scale) when [animated] — the same lightweight motion budget as the hero's
 * breathing, and suppressed under reduce-motion / power-save by the caller passing `animated = false`.
 *
 * `cat_mood_sleeping` is the only vector asset; the other three are WebP. [painterResource] handles
 * both, so the call site does not branch on the underlying format.
 */
@Composable
fun CatMascot(
    score   : Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val mood = catMoodFor(score)

    // Always call the transition (Rules of Hooks) — it idles at 0f when not animated.
    val transition = rememberInfiniteTransition(label = "catBob")
    val bob by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "catBobValue",
    )
    // ±2 % vertical squash-and-stretch reads as a soft breathing idle without moving the layout.
    val scaleY = if (animated) 1f + (bob - 0.5f) * 0.04f else 1f

    Image(
        painter            = painterResource(mood.drawableRes),
        contentDescription = "Кот: ${mood.caption}",
        contentScale       = ContentScale.Fit,
        modifier = modifier.graphicsLayer {
            this.scaleY = scaleY
            // Anchor the squash to the cat's feet so it "settles" rather than floating.
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
    )
}

private data class Paw(
    val x0    : Float,   // base position, fraction of size
    val y0    : Float,
    val ax    : Float,   // drift amplitude, fraction of size
    val ay    : Float,
    val speed : Float,
    val phase : Float,
    val sizePx: Float,
    val baseAlpha: Float,
    val glow  : Boolean, // glow paw vs. faint ghost paw — alternated for a "trail" feel
    val spin  : Float,   // static rotation, radians
)

/**
 * Paw-print particle field — the «Кот-режим» replacement for [ParticleLayer]'s fireflies. Same
 * analytic-drift maths (one accumulated time value, no per-particle Animatable) so it costs the same
 * per frame, and the loop only runs while [animated]. Glow and ghost paws are alternated so the field
 * reads as soft trails rather than a uniform scatter.
 *
 * Drawn BEHIND opaque hero content, exactly like the fireflies, so a paw never crosses a number.
 */
@Composable
fun PawParticleLayer(
    count   : Int = 10,
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density   = LocalDensity.current
    // painterResource returns a VectorPainter for a vector drawable; drawing it in the Canvas via
    // `with(painter) { draw(...) }` works for any Painter, so no format-specific handling is needed.
    val glowPaw   = painterResource(R.drawable.cat_paw_glow)
    val ghostPaw  = painterResource(R.drawable.cat_paw_ghost)

    val paws = remember(count) {
        val rnd = Random(count.toLong() * 2089L + 13L)
        List(count) { i ->
            Paw(
                x0        = rnd.nextFloat(),
                y0        = rnd.nextFloat(),
                ax        = 0.02f + rnd.nextFloat() * 0.05f,
                ay        = 0.02f + rnd.nextFloat() * 0.05f,
                speed     = 0.10f + rnd.nextFloat() * 0.25f,
                phase     = rnd.nextFloat() * 6.2832f,
                sizePx    = with(density) { (18f + rnd.nextFloat() * 12f).dp.toPx() },
                baseAlpha = 0.30f + rnd.nextFloat() * 0.35f,
                glow      = (i % 2 == 0),
                spin      = (rnd.nextFloat() - 0.5f) * 0.9f,
            )
        }
    }

    var time by remember { mutableStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = 0L
        withInfiniteAnimationFrameMillis { last = it }
        while (true) {
            withInfiniteAnimationFrameMillis { ms ->
                time += (ms - last).coerceIn(0L, 64L) / 1000f
                last = ms
            }
        }
    }

    Canvas(modifier = modifier) {
        val t = time
        paws.forEach { p ->
            val x = (p.x0 + p.ax * sin(t * p.speed + p.phase)).coerceIn(0f, 1f) * size.width
            val y = (p.y0 + p.ay * cos(t * p.speed * 0.8f + p.phase)).coerceIn(0f, 1f) * size.height
            val pulse = if (animated) 0.55f + 0.45f * sin(t * p.speed * 1.7f + p.phase) else 1f
            val a = (p.baseAlpha * pulse).coerceIn(0f, 1f)
            val painter = if (p.glow) glowPaw else ghostPaw
            val half = p.sizePx / 2f
            translate(left = x - half, top = y - half) {
                // Static per-paw rotation, applied around the paw's own centre.
                rotate(degrees = p.spin * 57.2958f, pivot = Offset(half, half)) {
                    with(painter) { draw(size = Size(p.sizePx, p.sizePx), alpha = a) }
                }
            }
        }
    }
}
