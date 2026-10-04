package voice.features.playbackScreen.view

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.strings.R
import voice.core.strips.ActiveStripFrame
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState
import java.io.File

private const val STRIP_FRAME_CROSSFADE_MS = 300

/**
 * The square cover, edge to edge and fading into the screen below. Where the space is shorter than the screen is wide
 * the cover is smaller and its sides fade too. The strip's current frame takes its place while a strip plays.
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
    Crossfade(
      targetState = stripFrame,
      animationSpec = tween(STRIP_FRAME_CROSSFADE_MS),
      label = "cover-strip-frame",
      modifier = Modifier.fillMaxSize(),
    ) { frame ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (frame == null) {
          Cover(
            bookId = bookId,
            cover = cover,
            fadeSides = fadeSides,
            onDoubleClick = onPlayClick,
            modifier = Modifier.aspectRatio(1F, matchHeightConstraintsFirst = true),
          )
        } else {
          AsyncImage(
            model = File(frame.strip.directory, frame.strip.manifest.frames[frame.frameIndex].image),
            contentDescription = frame.strip.manifest.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 20.dp, vertical = 12.dp)
              .clickable { onStripFrameClick(frame) },
          )
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
