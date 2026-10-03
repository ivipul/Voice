package voice.features.playbackScreen.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import voice.features.playbackScreen.view.holocard.holoReveal
import voice.core.strings.R as StringsR

private const val REVEAL_MS = 650

/**
 * A snip's comic frame, in the snip sheet and in the Feed. While there is no image yet it shows a pulsing,
 * sweeping holo foil; when the image arrives it bleeds in through the foil like the Crawl explorer's look morph.
 * An image that is already there when this first appears (a Feed scrolled back to an old snip) is just shown.
 */
@Composable
internal fun HoloImageLoader(
  imagePath: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(16.dp),
) {
  val playReveal = remember { shouldPlayReveal(imagePathOnFirstComposition = imagePath) }
  val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
    value = imagePath?.let { path -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } }
  }
  val reveal = remember { Animatable(if (playReveal) 0f else 1f) }
  LaunchedEffect(bitmap) {
    if (bitmap != null && reveal.value < 1f) {
      reveal.animateTo(1f, tween(durationMillis = REVEAL_MS, easing = LinearEasing))
    }
  }
  val animating by remember { derivedStateOf { bitmap == null || reveal.value < 1f } }
  val time by produceState(initialValue = 0f, animating) {
    if (animating) {
      val start = withFrameNanos { it }
      while (true) {
        value = (withFrameNanos { it } - start) / 1_000_000_000f
      }
    }
  }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(FRAME_ASPECT)
      .clip(shape)
      .semantics { contentDescription?.let { this.contentDescription = it } }
      .holoReveal(bitmap = bitmap, reveal = { reveal.value }, time = { time }),
    contentAlignment = Alignment.Center,
  ) {
    if (imagePath == null) {
      Text(
        text = stringResource(StringsR.string.copilot_feed_frame_drawing),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/** The reveal plays only for an image that arrives while the loader is on screen, not for one already there. */
internal fun shouldPlayReveal(imagePathOnFirstComposition: String?): Boolean = imagePathOnFirstComposition == null
