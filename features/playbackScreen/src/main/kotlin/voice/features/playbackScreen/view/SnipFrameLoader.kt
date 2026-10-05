package voice.features.playbackScreen.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.ui.FRAME_ASPECT
import voice.core.ui.crawl.CrawlType
import kotlin.math.PI
import kotlin.math.sin
import voice.core.strings.R as StringsR

private const val REVEAL_MS = 700
private const val PULSE_MS = 1_100

/**
 * A snip's comic frame, in the snip sheet and in the Feed. While there is no image yet it shows a quietly pulsing
 * panel in the screen's own colors with a "footage incoming" label; when the image arrives it fades in with a small
 * dip and spring back. An image that is already there when this first appears (a Feed scrolled back to an old snip)
 * is just shown.
 */
@Composable
internal fun SnipFrameLoader(
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
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(FRAME_ASPECT)
      .graphicsLayer {
        val scale = revealScale(reveal.value)
        scaleX = scale
        scaleY = scale
      }
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .semantics { contentDescription?.let { this.contentDescription = it } },
    contentAlignment = Alignment.Center,
  ) {
    val loaded = bitmap
    if (loaded == null) {
      FramePlaceholder(showLabel = imagePath == null)
    } else {
      Image(
        bitmap = loaded.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = reveal.value,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun FramePlaceholder(showLabel: Boolean) {
  val ink = MaterialTheme.colorScheme.onSurface
  val pulse by rememberInfiniteTransition(label = "snip-frame-pulse").animateFloat(
    initialValue = 0f,
    targetValue = 0.1f,
    animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
    label = "snip-frame-pulse-alpha",
  )
  Column(
    modifier = Modifier
      .fillMaxSize()
      .drawBehind { drawRect(color = ink, alpha = pulse) }
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(24.dp),
      color = MaterialTheme.colorScheme.primary,
      strokeWidth = 2.5.dp,
    )
    if (showLabel) {
      Text(
        text = stringResource(StringsR.string.copilot_feed_frame_drawing).uppercase(),
        style = CrawlType.label,
        color = ink,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** A slight dip and spring back while the frame fades in: 1 at both ends, 0.96 in the middle. */
internal fun revealScale(progress: Float): Float = 1f - 0.04f * sin(PI.toFloat() * progress.coerceIn(0f, 1f))

/** The reveal plays only for an image that arrives while the loader is on screen, not for one already there. */
internal fun shouldPlayReveal(imagePathOnFirstComposition: String?): Boolean = imagePathOnFirstComposition == null
