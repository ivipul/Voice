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
  containerColor: Color = FloatingActionButtonDefaults.containerColor,
  contentColor: Color = contentColorFor(containerColor),
  hardShadowColor: Color? = null,
) {
  val cornerSize by animateDpAsState(
    targetValue = if (playing) 16.dp else fabSize / 2,
    label = "cornerSize",
  )
  FloatingActionButton(
    modifier = modifier
      .size(fabSize)
      .then(sharedElementModifier)
      .then(
        if (hardShadowColor != null) {
          Modifier.hardShadow(color = hardShadowColor, shape = RoundedCornerShape(cornerSize), x = 3.dp, y = 3.dp)
        } else {
          Modifier
        },
      ),
    onClick = onPlayClick,
    shape = RoundedCornerShape(cornerSize),
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = if (hardShadowColor != null) {
      FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    } else {
      FloatingActionButtonDefaults.elevation()
    },
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
