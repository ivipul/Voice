package voice.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Snip frames are drawn 736x1312 (9:16). */
const val FRAME_ASPECT = 736f / 1312f
private const val CARD_SCREEN_FRACTION = 0.9f
private const val SCRIM_ALPHA = 0.85f
private const val FLING_OUT_SCREENS = 1.2f

/**
 * A snip's comic frame as a rounded card filling about 90% of the screen over a black scrim.
 * Drag the card in any direction and let go to swipe it away; a short drag springs back.
 * Back press or a tap on the scrim also dismisses it.
 */
@Composable
fun FrameCardViewer(
  imagePath: String,
  contentDescription: String,
  onDismiss: () -> Unit,
) {
  val currentOnDismiss by rememberUpdatedState(onDismiss)
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val density = LocalDensity.current
      val screenWidth = constraints.maxWidth.toFloat()
      val screenHeight = constraints.maxHeight.toFloat()
      val (cardWidth, cardHeight) = fitCard(screenWidth, screenHeight)
      val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
      val scope = rememberCoroutineScope()
      val dismissDistance = with(density) { 96.dp.toPx() }
      val dragProgress = (hypot(dragOffset.value.x, dragOffset.value.y) / (screenHeight / 2)).coerceIn(0f, 1f)

      fun release() {
        scope.launch {
          val current = dragOffset.value
          if (hypot(current.x, current.y) >= dismissDistance) {
            dragOffset.animateTo(flungOut(current, screenWidth, screenHeight))
            currentOnDismiss()
          } else {
            dragOffset.animateTo(Offset.Zero)
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = SCRIM_ALPHA * (1f - dragProgress)))
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { currentOnDismiss() },
      )
      AsyncImage(
        model = File(imagePath),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .align(Alignment.Center)
          .offset { IntOffset(dragOffset.value.x.roundToInt(), dragOffset.value.y.roundToInt()) }
          .size(with(density) { cardWidth.toDp() }, with(density) { cardHeight.toDp() })
          .clip(RoundedCornerShape(28.dp))
          .background(Color.DarkGray)
          .pointerInput(Unit) {
            detectDragGestures(
              onDragEnd = { release() },
              onDragCancel = { release() },
              onDrag = { change, dragAmount ->
                change.consume()
                scope.launch { dragOffset.snapTo(dragOffset.value + dragAmount) }
              },
            )
          },
      )
    }
  }
}

internal fun fitCard(screenWidth: Float, screenHeight: Float): Pair<Float, Float> {
  val maxWidth = screenWidth * CARD_SCREEN_FRACTION
  val maxHeight = screenHeight * CARD_SCREEN_FRACTION
  return if (maxWidth / maxHeight < FRAME_ASPECT) {
    maxWidth to maxWidth / FRAME_ASPECT
  } else {
    maxHeight * FRAME_ASPECT to maxHeight
  }
}

private fun flungOut(from: Offset, screenWidth: Float, screenHeight: Float): Offset {
  val length = hypot(from.x, from.y).takeIf { it > 0f } ?: return Offset(0f, screenHeight)
  val distance = hypot(screenWidth, screenHeight) * FLING_OUT_SCREENS
  return Offset(from.x / length * distance, from.y / length * distance)
}
