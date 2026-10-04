package voice.features.playbackScreen.view

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import voice.core.strips.StripZone
import voice.core.strips.stripStartAt
import voice.core.ui.formatTime
import voice.features.playbackScreen.view.holocard.RainbowColors
import kotlin.time.Duration

private val SliderTrackHeight = 16.dp
private val SliderThumbWidth = 4.dp

@Composable
internal fun SliderRow(
  duration: Duration,
  playedTime: Duration,
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
      track = { sliderState -> StripTrack(sliderState, stripZones) },
    )
    Text(
      text = formatTime(
        timeMs = duration.inWholeMilliseconds,
        durationMs = duration.inWholeMilliseconds,
      ),
    )
  }
}

/** The slider's track with the strips' ranges overlaid in rainbow. */
@Composable
private fun StripTrack(
  sliderState: SliderState,
  stripZones: List<StripZone>,
) {
  Box(contentAlignment = Alignment.Center) {
    SliderDefaults.Track(sliderState = sliderState)
    if (stripZones.isNotEmpty()) {
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(SliderTrackHeight)
          .clip(RoundedCornerShape(percent = 50)),
      ) {
        stripZones.forEach { zone ->
          val left = size.width * zone.startFraction
          val right = size.width * zone.endFraction
          drawRect(
            brush = Brush.horizontalGradient(colors = RainbowColors, startX = left, endX = right),
            topLeft = Offset(left, 0f),
            size = Size(right - left, size.height),
          )
        }
      }
    }
  }
}

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
