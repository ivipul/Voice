package voice.features.playbackScreen.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.strings.R
import voice.core.strips.ActiveStripFrame
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.hardShadow
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState
import java.io.File

private const val FRAME_RISE_MS = 550
private const val FRAME_FADE_MS = 450
private const val FRAME_LEAVE_MS = 300
private const val FRAME_CHANGE_MS = 300
private const val FRAME_TILT_DEGREES = -2F
private const val DIMMED_COVER_ALPHA = 0.2F
private val FrameShape = RoundedCornerShape(18.dp)
private val FrameEasing = CubicBezierEasing(0.2F, 0.8F, 0.2F, 1F)

/**
 * The square cover, centered in the space it gets and fading into the screen below. It is as wide as the screen unless
 * the space is shorter, where it is smaller and its sides fade too. While a strip plays, the cover dims and the strip's
 * current frame rises in front of it as a tilted card.
 */
@Composable
internal fun CoverStage(
  bookId: BookId,
  cover: Any?,
  stripFrame: ActiveStripFrame?,
  sleepTimerState: BookPlayViewState.SleepTimerViewState,
  palette: CrawlPalette,
  onPlayClick: () -> Unit,
  onStripFrameClick: (ActiveStripFrame) -> Unit,
  modifier: Modifier = Modifier,
  alwaysFadeSides: Boolean = false,
) {
  BoxWithConstraints(modifier) {
    val fadeSides = alwaysFadeSides || maxHeight < maxWidth
    val coverAlpha by animateFloatAsState(
      targetValue = if (stripFrame == null) 1F else DIMMED_COVER_ALPHA,
      animationSpec = tween(FRAME_FADE_MS),
      label = "cover-alpha",
    )
    Cover(
      bookId = bookId,
      cover = cover,
      fadeSides = fadeSides,
      onDoubleClick = onPlayClick,
      modifier = Modifier
        .align(Alignment.Center)
        .aspectRatio(1F, matchHeightConstraintsFirst = true)
        .graphicsLayer { alpha = coverAlpha },
    )
    AnimatedContent(
      targetState = stripFrame,
      contentKey = { it != null },
      transitionSpec = { stripFrameTransition() },
      label = "strip-frame",
      modifier = Modifier.fillMaxSize(),
    ) { frame ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (frame != null) {
          StripFrameCard(frame = frame, palette = palette, onClick = { onStripFrameClick(frame) })
        }
      }
    }
    SleepTimerBadge(
      sleepTimerState = sleepTimerState,
      palette = palette,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(12.dp),
    )
  }
}

/** The card fades in as it rises from below and sinks away when the strip ends; moving between frames swaps only the image. */
private fun AnimatedContentTransitionScope<ActiveStripFrame?>.stripFrameTransition(): ContentTransform {
  val transform = if (targetState != null) {
    (fadeIn(tween(FRAME_FADE_MS)) + slideInVertically(tween(FRAME_RISE_MS, easing = FrameEasing)) { it / 12 })
      .togetherWith(ExitTransition.None)
  } else {
    EnterTransition.None
      .togetherWith(fadeOut(tween(FRAME_LEAVE_MS)) + slideOutVertically(tween(FRAME_LEAVE_MS)) { it / 12 })
  }
  return transform.using(SizeTransform(clip = false))
}

/** The strip's current frame as a tilted card with rounded corners, sized to the image. A tap opens the strip. */
@Composable
private fun StripFrameCard(
  frame: ActiveStripFrame,
  palette: CrawlPalette,
  onClick: () -> Unit,
) {
  Crossfade(
    targetState = frame,
    animationSpec = tween(FRAME_CHANGE_MS),
    label = "strip-frame-image",
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 28.dp, vertical = 20.dp),
  ) { shown ->
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      // Until the image is in, the card has no size of its own, so its shadow waits for it.
      var loaded by remember { mutableStateOf(false) }
      AsyncImage(
        model = File(shown.strip.directory, shown.strip.manifest.frames[shown.frameIndex].image),
        contentDescription = shown.strip.manifest.title,
        contentScale = ContentScale.Fit,
        onSuccess = { loaded = true },
        modifier = Modifier
          .graphicsLayer { rotationZ = FRAME_TILT_DEGREES }
          .then(if (loaded) Modifier.hardShadow(color = palette.shadow, shape = FrameShape, x = 4.dp, y = 4.dp) else Modifier)
          .clip(FrameShape)
          .clickable(onClick = onClick),
      )
    }
  }
}

@Composable
private fun SleepTimerBadge(
  sleepTimerState: BookPlayViewState.SleepTimerViewState,
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
) {
  val text = when (sleepTimerState) {
    BookPlayViewState.SleepTimerViewState.Disabled -> return
    is BookPlayViewState.SleepTimerViewState.Enabled.WithDuration -> formatTime(
      timeMs = sleepTimerState.leftDuration.inWholeMilliseconds,
    )
    BookPlayViewState.SleepTimerViewState.Enabled.WithEndOfChapter -> stringResource(R.string.sleep_timer_end_of_chapter)
  }
  Text(
    text = text,
    style = CrawlType.badge,
    color = palette.background,
    modifier = modifier
      .background(color = palette.content, shape = CircleShape)
      .padding(horizontal = 12.dp, vertical = 6.dp),
  )
}
