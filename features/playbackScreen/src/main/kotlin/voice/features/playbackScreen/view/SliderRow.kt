package voice.features.playbackScreen.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import voice.core.strips.StripZone
import voice.core.strips.stripStartAt
import voice.core.ui.formatTime
import kotlin.time.Duration

private val SliderTrackHeight = 16.dp
private val SliderThumbWidth = 4.dp
private const val FlowLoopMs = 5_000

@Composable
internal fun SliderRow(
  duration: Duration,
  playedTime: Duration,
  playing: Boolean,
  stripZones: List<StripZone>,
  onStripZoneTap: (Long) -> Unit,
  onSeek: (Duration) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    var localValue by remember { mutableFloatStateOf(0F) }
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val gesture = remember { GestureTracker() }
    Text(
      text = formatTime(
        timeMs = if (dragging) {
          (duration * localValue.toDouble()).inWholeMilliseconds
        } else {
          playedTime.inWholeMilliseconds
        },
        durationMs = duration.inWholeMilliseconds,
      ),
    )
    Slider(
      modifier = Modifier
        .weight(1F)
        .padding(horizontal = 8.dp)
        .trackTapVersusDrag(gesture),
      interactionSource = interactionSource,
      value = if (dragging) {
        localValue
      } else {
        (playedTime / duration).toFloat()
          .coerceIn(0F, 1F)
      },
      onValueChange = {
        localValue = it
      },
      onValueChangeFinished = {
        val stripStart = if (gesture.moved) null else stripZones.stripStartAt(localValue)
        if (stripStart != null) {
          onStripZoneTap(stripStart)
        } else {
          onSeek(duration * localValue.toDouble())
        }
      },
      thumb = {
        // As tall as the track, instead of the default 44 dp bar.
        SliderDefaults.Thumb(
          interactionSource = interactionSource,
          thumbSize = DpSize(SliderThumbWidth, SliderTrackHeight),
        )
      },
      track = { sliderState ->
        StripTrack(
          sliderState = sliderState,
          stripZones = stripZones,
          flowingZone = if (playing && !dragging) stripZones.zoneAt(sliderState.value) else null,
        )
      },
    )
    Text(
      text = formatTime(
        timeMs = duration.inWholeMilliseconds,
        durationMs = duration.inWholeMilliseconds,
      ),
    )
  }
}

/** The slider's track with each strip's range overlaid as a soft gradient of its own colour. */
@Composable
private fun StripTrack(
  sliderState: SliderState,
  stripZones: List<StripZone>,
  flowingZone: StripZone?,
) {
  Box(contentAlignment = Alignment.Center) {
    SliderDefaults.Track(sliderState = sliderState)
    if (stripZones.isNotEmpty()) {
      // Read only while drawing, so the flow redraws this canvas and recomposes nothing.
      val flow = rememberFlowPhase(flowing = flowingZone != null)
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(SliderTrackHeight)
          .clip(RoundedCornerShape(percent = 50)),
      ) {
        stripZones.forEach { zone ->
          val left = size.width * zone.startFraction
          val right = size.width * zone.endFraction
          val colors = stripZoneColors(zone.chapter)
          val width = right - left
          // Mirrored tiles repeat every two zone widths, so a drift of exactly that loops without a seam.
          val drift = if (zone == flowingZone) flow.value * 2f * width else 0f
          drawRoundRect(
            brush = Brush.horizontalGradient(
              colors = listOf(colors.light, colors.deep),
              startX = left + drift,
              endX = right + drift,
              tileMode = TileMode.Mirror,
            ),
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(minOf(size.height, width) / 2f),
          )
        }
      }
    }
  }
}

/** 0..1, looping slowly while [flowing]; a still 0 otherwise, without keeping an animation running. */
@Composable
private fun rememberFlowPhase(flowing: Boolean): State<Float> {
  if (!flowing) return remember { mutableFloatStateOf(0f) }
  return rememberInfiniteTransition(label = "strip zone flow").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(durationMillis = FlowLoopMs, easing = LinearEasing), RepeatMode.Restart),
    label = "strip zone flow phase",
  )
}

private fun List<StripZone>.zoneAt(fraction: Float): StripZone? =
  lastOrNull { fraction >= it.startFraction && fraction < it.endFraction }

/** Whether the gesture that just ended moved far enough to be a drag rather than a tap. */
internal class GestureTracker {
  var moved: Boolean = false
}

/** Watches every gesture on the slider without consuming it, so a tap can be told from a drag. */
private fun Modifier.trackTapVersusDrag(tracker: GestureTracker): Modifier = pointerInput(tracker) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    tracker.moved = false
    do {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      if (event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) {
        tracker.moved = true
      }
    } while (event.changes.any { it.pressed })
  }
}
