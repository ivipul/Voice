package voice.features.playbackScreen.view

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.CrawlPalette
import kotlin.time.Duration

private val TrackHeight = 4.dp
private val TouchHeight = 10.dp
private val ThumbSize = DpSize(4.dp, 18.dp)
private const val TRACK_ALPHA = 0.2F

/** The chapter's seek bar: a thin track filled in the highlight color. [onScrub] reports where a drag is, then null. */
@Composable
internal fun SliderRow(
  duration: Duration,
  playedTime: Duration,
  palette: CrawlPalette,
  onScrub: (Float?) -> Unit,
  onSeek: (Duration) -> Unit,
  modifier: Modifier = Modifier,
) {
  var localValue by remember { mutableFloatStateOf(0F) }
  val interactionSource = remember { MutableInteractionSource() }
  val dragging by interactionSource.collectIsDraggedAsState()
  Slider(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp),
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
      onSeek(duration * localValue.toDouble())
    },
    thumb = {
      SliderDefaults.Thumb(
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(thumbColor = palette.highlight),
        thumbSize = ThumbSize,
      )
    },
    track = { sliderState ->
      PosterTrack(sliderState = sliderState, palette = palette)
    },
  )
}

@Composable
private fun PosterTrack(
  sliderState: SliderState,
  palette: CrawlPalette,
) {
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(TouchHeight),
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
  }
}
