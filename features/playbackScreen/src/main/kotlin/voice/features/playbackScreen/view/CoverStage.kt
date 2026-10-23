package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.core.strings.R
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState

/**
 * The square cover, centered in the space it gets and fading into the screen below. It is as wide as the screen unless
 * the space is shorter, where it is smaller and its sides fade too.
 */
@Composable
internal fun CoverStage(
  bookId: BookId,
  cover: Any?,
  sleepTimerState: BookPlayViewState.SleepTimerViewState,
  palette: CrawlPalette,
  onPlayClick: () -> Unit,
  modifier: Modifier = Modifier,
  alwaysFadeSides: Boolean = false,
) {
  BoxWithConstraints(modifier) {
    val fadeSides = alwaysFadeSides || maxHeight < maxWidth
    Cover(
      bookId = bookId,
      cover = cover,
      fadeSides = fadeSides,
      onDoubleClick = onPlayClick,
      modifier = Modifier
        .align(Alignment.Center)
        .aspectRatio(1F, matchHeightConstraintsFirst = true),
    )
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
