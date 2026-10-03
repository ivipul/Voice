package voice.features.playbackScreen.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.features.playbackScreen.view.holocard.RainbowColors
import java.io.File
import voice.core.strings.R as StringsR

/**
 * The comic frame of a snip, in the sheet and in the Feed. Until the image is there it pulses with a sweeping foil of
 * the holo-card colors; once it has loaded the image fades in over it and the foil fades out.
 */
@Composable
internal fun HoloFrameSlot(
  imagePath: String?,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  var loaded by remember(imagePath) { mutableStateOf(false) }
  val imageAlpha by animateFloatAsState(
    targetValue = if (loaded) 1f else 0f,
    animationSpec = tween(durationMillis = FRAME_FADE_MS),
    label = "snip-frame-image-alpha",
  )
  val transition = rememberInfiniteTransition(label = "snip-frame-holo")
  val sweep by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
    label = "snip-frame-holo-sweep",
  )
  val pulse by transition.animateFloat(
    initialValue = 0.45f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 1100), RepeatMode.Reverse),
    label = "snip-frame-holo-pulse",
  )
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(FRAME_ASPECT)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer { alpha = pulse * (1f - imageAlpha) }
        .drawBehind {
          val width = size.width
          drawRect(
            Brush.linearGradient(
              colors = RainbowColors + RainbowColors.first(),
              start = Offset(sweep * width - width, 0f),
              end = Offset(sweep * width, size.height * 0.6f),
              tileMode = TileMode.Repeated,
            ),
          )
        },
    )
    if (imagePath != null) {
      AsyncImage(
        model = File(imagePath),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { loaded = true },
        modifier = Modifier
          .matchParentSize()
          .graphicsLayer { alpha = imageAlpha },
      )
    }
    Text(
      text = stringResource(StringsR.string.copilot_feed_frame_drawing),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.graphicsLayer { alpha = 1f - imageAlpha },
    )
  }
}

private const val FRAME_FADE_MS = 650
