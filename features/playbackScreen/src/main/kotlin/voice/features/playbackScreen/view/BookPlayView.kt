package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import voice.core.data.BookId
import voice.core.strips.ActiveStripFrame
import voice.core.ui.VoiceTheme
import voice.core.ui.bookCardBackdropModifier
import voice.core.ui.bookCardContentModifier
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.crawl.crawlPaletteOf
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun BookPlayView(
  viewState: BookPlayViewState,
  bookId: BookId,
  useLandscapeLayout: Boolean,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onSeek: (Duration) -> Unit,
  onStripZoneTap: (Long) -> Unit,
  onStripFrameClick: (ActiveStripFrame) -> Unit,
  onCloseClick: () -> Unit,
  onFeedClick: () -> Unit,
  onSleepTimerClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onSmartRecapClick: () -> Unit,
  onSpeedClick: () -> Unit,
  onAskClick: () -> Unit,
  onSnipClick: () -> Unit,
  onInventoryClick: () -> Unit,
  onXRayChipClick: (String) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val palette = crawlPaletteOf(viewState.edition)
  // The player is what the book's library card grows into: its color first, then the screen on top of it.
  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .bookCardBackdropModifier(bookId, onCard = false)
        .background(palette.background),
    )
    Scaffold(
      modifier = Modifier.bookCardContentModifier(bookId, onCard = false),
      containerColor = palette.background,
      contentColor = palette.content,
      snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
      },
      topBar = {
        BookPlayAppBar(
          skipSilence = viewState.skipSilence,
          onCloseClick = onCloseClick,
          onFeedClick = onFeedClick,
          onSleepTimerClick = onSleepTimerClick,
          onSkipSilenceClick = onSkipSilenceClick,
          onVolumeBoostClick = onVolumeBoostClick,
        )
      },
      content = {
        BookPlayContent(
          contentPadding = it,
          viewState = viewState,
          bookId = bookId,
          useLandscapeLayout = useLandscapeLayout,
          onPlayClick = onPlayClick,
          onRewindClick = onRewindClick,
          onFastForwardClick = onFastForwardClick,
          onSkipToNext = onSkipToNext,
          onSkipToPrevious = onSkipToPrevious,
          onSeek = onSeek,
          onStripZoneTap = onStripZoneTap,
          onStripFrameClick = onStripFrameClick,
          onCurrentChapterClick = onCurrentChapterClick,
          onSmartRecapClick = onSmartRecapClick,
          onSpeedClick = onSpeedClick,
          onAskClick = onAskClick,
          onSnipClick = onSnipClick,
          onInventoryClick = onInventoryClick,
          onXRayChipClick = onXRayChipClick,
        )
      },
    )
  }
}

@Composable
@Preview
private fun BookPlayPreview(
  @PreviewParameter(BookPlayViewStatePreviewProvider::class)
  viewState: BookPlayViewState,
) {
  VoiceTheme {
    CrawlTheme(viewState.edition) {
      BookPlayView(
        viewState = viewState,
        bookId = BookId("preview"),
        useLandscapeLayout = false,
        onPlayClick = {},
        onRewindClick = {},
        onFastForwardClick = {},
        onSkipToNext = {},
        onSkipToPrevious = {},
        onSeek = {},
        onStripZoneTap = {},
        onStripFrameClick = {},
        onCloseClick = {},
        onFeedClick = {},
        onSleepTimerClick = {},
        onSkipSilenceClick = {},
        onVolumeBoostClick = {},
        onCurrentChapterClick = {},
        onSmartRecapClick = {},
        onSpeedClick = {},
        onAskClick = {},
        onSnipClick = {},
        onInventoryClick = {},
        onXRayChipClick = {},
      )
    }
  }
}

private class BookPlayViewStatePreviewProvider : PreviewParameterProvider<BookPlayViewState> {
  override val values = sequence {
    val initial = BookPlayViewState(
      chapterName = "Chapter 11",
      showPreviousNextButtons = true,
      cover = null,
      duration = 10.minutes,
      playedTime = 3.minutes,
      playing = true,
      skipSilence = true,
      skipBackSeconds = 15,
      skipForwardSeconds = 30,
      sleepTimerState = BookPlayViewState.SleepTimerViewState.Disabled,
      title = "1. Dungeon Crawler Carl",
      xrayChips = listOf(
        BookPlayViewState.XRayChipViewState(id = "carl", label = "Carl"),
        BookPlayViewState.XRayChipViewState(id = "donut", label = "Princess Donut"),
        BookPlayViewState.XRayChipViewState(id = "mongo", label = "Mongo"),
        BookPlayViewState.XRayChipViewState(id = "mordecai", label = "Mordecai"),
      ),
      edition = CrawlEdition.DungeonCrawlerCarl,
      playbackSpeed = 1.1F,
      inventoryCount = 7,
    )
    yield(initial)
    yield(
      initial.copy(
        title = "4. The Gate of the Feral Gods",
        chapterName = "Chapter 21",
        playing = false,
        edition = CrawlEdition.GateOfTheFeralGods,
        sleepTimerState = BookPlayViewState.SleepTimerViewState.Enabled.WithDuration(12.minutes),
      ),
    )
    yield(initial.copy(title = "Das Ende der Welt", edition = null, xrayChips = emptyList()))
  }
}
