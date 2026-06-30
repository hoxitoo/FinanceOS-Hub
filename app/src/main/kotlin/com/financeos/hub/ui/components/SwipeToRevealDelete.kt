package com.financeos.hub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.financeos.hub.ui.theme.FosColors
import com.financeos.hub.ui.theme.FosDimens
import com.financeos.hub.ui.theme.FosType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Wraps [content] in a swipe-LEFT-to-reveal-delete affordance:
 *  - dragging the row left exposes a red trash button pinned to the right edge;
 *  - the swipe itself NEVER deletes (unlike SwipeToDismissBox, which auto-deleted on a flick in
 *    either direction — too easy to trigger by accident);
 *  - TAP the revealed trash to confirm deletion;
 *  - releasing below half-reveal, or dragging back right, closes it.
 *
 * The row content is opaque, so the trash stays hidden until the row is dragged off it.
 */
@Composable
fun SwipeToRevealDelete(
    onDelete : () -> Unit,
    modifier : Modifier = Modifier,
    content  : @Composable () -> Unit,
) {
    val scope    = rememberCoroutineScope()
    val revealDp = 76.dp
    val revealPx = with(LocalDensity.current) { revealDp.toPx() }
    val offsetX  = remember { Animatable(0f) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FosDimens.RadiusCardSmall)),
    ) {
        // Red trash, pinned to the right; revealed as the row slides left. Tap = confirm delete.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(revealDp)
                    .background(FosColors.Negative)
                    .clickable {
                        onDelete()
                        scope.launch { offsetX.animateTo(0f) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("🗑", style = FosType.Body)
            }
        }

        // Foreground row: slides horizontally over the trash. Horizontal drags are handled here;
        // vertical scrolling and taps still pass through to the list / the row's own onClick.
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            offsetX.animateTo(if (offsetX.value < -revealPx / 2f) -revealPx else 0f)
                        }
                    },
                ),
        ) { content() }
    }
}
