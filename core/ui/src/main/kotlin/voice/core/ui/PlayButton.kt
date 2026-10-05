package voice.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.hardShadow
import voice.core.strings.R as StringsR

@Composable
fun PlayButton(
  playing: Boolean,
  fabSize: Dp,
  iconSize: Dp,
  onPlayClick: () -> Unit,
  modifier: Modifier = Modifier,
  sharedElementModifier: Modifier = Modifier,
  palette: CrawlPalette? = null,
) {
  val cornerSize by animateDpAsState(
    targetValue = if (playing) 16.dp else fabSize / 2,
    label = "cornerSize",
  )
  val shape = RoundedCornerShape(cornerSize)
  val containerColor = palette?.background ?: FloatingActionButtonDefaults.containerColor
  FloatingActionButton(
    modifier = modifier
      .then(if (palette != null) Modifier.hardShadow(color = Color.White, shape = shape, x = 3.dp, y = 3.dp) else Modifier)
      .size(fabSize)
      .then(sharedElementModifier),
    onClick = onPlayClick,
    shape = shape,
    containerColor = containerColor,
    contentColor = palette?.highlight ?: contentColorFor(containerColor),
    elevation = if (palette != null) flatElevation() else FloatingActionButtonDefaults.elevation(),
  ) {
    Icon(
      modifier = Modifier.size(iconSize),
      painter = rememberPlayIconPainter(playing = playing),
      contentDescription = stringResource(
        id = if (playing) {
          StringsR.string.playback_action_pause
        } else {
          StringsR.string.playback_action_play
        },
      ),
    )
  }
}

@Composable
fun rememberPlayIconPainter(playing: Boolean): Painter {
  return rememberAnimatedVectorPainter(
    animatedImageVector = AnimatedImageVector.animatedVectorResource(
      id = R.drawable.avd_pause_to_play,
    ),
    atEnd = !playing,
  )
}

/** With a palette the button casts a white hard shadow instead of a soft one, so it stands off a card of its own color. */
@Composable
private fun flatElevation() = FloatingActionButtonDefaults.elevation(
  defaultElevation = 0.dp,
  pressedElevation = 0.dp,
  focusedElevation = 0.dp,
  hoveredElevation = 0.dp,
)
