package com.financeos.hub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One wedge of [Pie3D]. */
data class PieSlice(
    val id      : String,
    val label   : String,
    val kopecks : Long,
    val color   : Color,
)

/**
 * Interactive pseudo-3D pie.
 *
 * The 3D look comes from two cheap tricks rather than a real renderer: the circle is squashed
 * vertically ([PERSPECTIVE]) so it reads as a disc seen at an angle, and the same wedges are drawn
 * repeatedly a few pixels lower in a darkened shade to fake the extruded side. Slices are drawn back
 * to front so the extrusion never covers the top face.
 *
 * Tapping a wedge selects it (the wedge slides outward); tapping the same wedge or the background
 * clears the selection. Hit-testing un-squashes the touch point back into circle space, so it stays
 * accurate despite the perspective.
 */
@Composable
fun Pie3D(
    slices  : List<PieSlice>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.kopecks }.coerceAtLeast(1L)
    // Animate the explode offset so selection feels physical rather than a jump.
    val explode by animateFloatAsState(
        targetValue   = if (selected != null) 1f else 0f,
        animationSpec = tween(220),
        label         = "pieExplode",
    )

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { tap ->
                        val cx = size.width / 2f
                        val cy = size.height * CENTER_Y
                        val rx = (size.width / 2f) * RADIUS
                        val ry = rx * PERSPECTIVE
                        // Un-squash: map the ellipse back to a unit circle before measuring.
                        val nx = (tap.x - cx) / rx
                        val ny = (tap.y - cy) / ry
                        if (nx * nx + ny * ny > 1f) { onSelect(null); return@detectTapGestures }

                        var deg = Math.toDegrees(atan2(ny.toDouble(), nx.toDouble())).toFloat()
                        if (deg < 0f) deg += 360f          // 0° = 3 o'clock, matches startAngle 0
                        var acc = 0f
                        for (s in slices) {
                            val sweep = 360f * (s.kopecks.toFloat() / total)
                            if (deg >= acc && deg < acc + sweep) {
                                onSelect(if (selected == s.id) null else s.id)
                                return@detectTapGestures
                            }
                            acc += sweep
                        }
                        onSelect(null)
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height * CENTER_Y
            val rx = (size.width / 2f) * RADIUS
            val ry = rx * PERSPECTIVE
            val depth = size.height * DEPTH

            fun wedgeOffset(startDeg: Float, sweepDeg: Float): Offset {
                if (explode == 0f) return Offset.Zero
                val mid = Math.toRadians((startDeg + sweepDeg / 2f).toDouble())
                val push = rx * 0.07f * explode
                return Offset((cos(mid) * push).toFloat(), (sin(mid) * push * PERSPECTIVE).toFloat())
            }

            // ── 1. Extruded side: same wedges, drawn from the bottom up in a darker shade.
            var layer = depth
            while (layer > 0f) {
                var angle = 0f
                slices.forEach { s ->
                    val sweep = 360f * (s.kopecks.toFloat() / total)
                    if (sweep > 0f) {
                        val isDim = selected != null && selected != s.id
                        val side  = lerp(s.color, Color.Black, if (isDim) 0.72f else 0.45f)
                        val off   = wedgeOffset(angle, sweep)
                        drawArc(
                            color      = side,
                            startAngle = angle,
                            sweepAngle = sweep,
                            useCenter  = true,
                            topLeft    = Offset(cx - rx + off.x, cy - ry + layer + off.y),
                            size       = Size(rx * 2f, ry * 2f),
                        )
                    }
                    angle += sweep
                }
                layer -= 2f
            }

            // ── 2. Top face.
            var angle = 0f
            slices.forEach { s ->
                val sweep = 360f * (s.kopecks.toFloat() / total)
                if (sweep > 0f) {
                    val isDim = selected != null && selected != s.id
                    val top   = if (isDim) lerp(s.color, Color.Black, 0.45f) else s.color
                    val off   = wedgeOffset(angle, sweep)
                    drawArc(
                        color      = top,
                        startAngle = angle,
                        sweepAngle = sweep,
                        useCenter  = true,
                        topLeft    = Offset(cx - rx + off.x, cy - ry + off.y),
                        size       = Size(rx * 2f, ry * 2f),
                    )
                    // Hairline separator so neighbouring colours never blend together.
                    val a0 = Math.toRadians(angle.toDouble())
                    drawLine(
                        color = Color.Black.copy(alpha = 0.25f),
                        start = Offset(cx + off.x, cy + off.y),
                        end   = Offset(
                            cx + off.x + (cos(a0) * rx).toFloat(),
                            cy + off.y + (sin(a0) * ry).toFloat(),
                        ),
                        strokeWidth = 1.5f,
                    )
                }
                angle += sweep
            }
        }
    }
}

private const val PERSPECTIVE = 0.52f   // vertical squash — how "tilted" the disc looks
private const val RADIUS      = 0.82f   // share of half-width used by the pie
private const val CENTER_Y    = 0.44f   // pie sits slightly above centre to leave room for depth
private const val DEPTH       = 0.09f   // extrusion height as a share of the canvas
