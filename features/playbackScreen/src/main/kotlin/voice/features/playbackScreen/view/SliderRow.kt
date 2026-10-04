package voice.features.playbackScreen.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import voice.core.strips.StripZone
import voice.core.strips.stripStartAt
import voice.core.ui.crawl.CrawlPalette
import kotlin.time.Duration

private val TrackHeight = 4.dp
private val StripHeight = 10.dp
private val ThumbSize = DpSize(4.dp, 18.dp)
private const val TRACK_ALPHA = 0.2F

/**
 * The chapter's seek bar: a thin track filled in the title color, with each comic strip as one stretch in the Ask AI
 * color. A tap inside a stretch starts its strip, anything else seeks. [onScrub] reports where a drag is, then null.
 */
@Composable
internal fun SliderRow(
  duration: Duration,
  playedTime: Duration,
  stripZones: List<StripZone>,
  palette: CrawlPalette,
  onScrub: (Float?) -> Unit,
  onStripZoneTap: (Long) -> Unit,
  onSeek: (Duration) -> Unit,
  modifier: Modifier = Modifier,
) {
  var localValue by remember { mutableFloatStateOf(0F) }
  val interactionSource = remember { MutableInteractionSource() }
  val dragging by interactionSource.collectIsDraggedAsState()
  val gesture = remember { GestureTracker() }
  Slider(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
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
      onScrub(it)
    },
    onValueChangeFinished = {
      onScrub(null)
      val stripStart = if (gesture.moved) null else stripZones.stripStartAt(localValue)
      if (stripStart != null) {
        onStripZoneTap(stripStart)
      } else {
        onSeek(duration * localValue.toDouble())
      }
    },
    thumb = {
      SliderDefaults.Thumb(
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(thumbColor = palette.highlight),
        thumbSize = ThumbSize,
      )
    },
    track = { sliderState ->
      PosterTrack(sliderState = sliderState, stripZones = stripZones, palette = palette)
    },
  )
}

@Composable
private fun PosterTrack(
  sliderState: SliderState,
  stripZones: List<StripZone>,
  palette: CrawlPalette,
) {
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(StripHeight),
  ) {
    val trackHeight = TrackHeight.toPx()
    val trackTop = (size.height - trackHeight) / 2F
    val trackCorner = CornerRadius(trackHeight / 2F)
    drawRoundRect(
      color = palette.content.copy(alpha = TRACK_ALPHA),
      topLeft = Offset(0F, trackTop),
      size = Size(size.width, trackHeight),
      cornerRadius = trackCorner,
    )
    drawRoundRect(
      color = palette.highlight,
      topLeft = Offset(0F, trackTop),
      size = Size(size.width * sliderState.value.coerceIn(0F, 1F), trackHeight),
      cornerRadius = trackCorner,
    )
    stripZones.forEach { zone ->
      val left = size.width * zone.startFraction
      val width = size.width * (zone.endFraction - zone.startFraction)
      drawRoundRect(
        color = palette.accent,
        topLeft = Offset(left, 0F),
        size = Size(width, size.height),
        cornerRadius = CornerRadius(minOf(size.height, width) / 2F),
      )
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
