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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import voice.core.data.BookId
import voice.core.ui.VoiceTheme
import voice.core.ui.bookCardBackdropModifier
import voice.core.ui.bookCardContentModifier
import voice.core.ui.crawl.BookStyle
import voice.core.ui.crawl.ChapterTypes
import voice.core.ui.crawl.DefaultPalette
import voice.core.ui.InventoryContent
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
  onXRayChipClick: (String) -> Unit,
  inventoryContent: InventoryContent,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val palette = crawlPaletteOf(viewState.bookStyle)
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
          onCurrentChapterClick = onCurrentChapterClick,
          onSmartRecapClick = onSmartRecapClick,
          onSpeedClick = onSpeedClick,
          onAskClick = onAskClick,
          onSnipClick = onSnipClick,
          onXRayChipClick = onXRayChipClick,
        )
      },
    )
    InventorySheet(
      bookId = bookId,
      count = viewState.inventoryCount,
      palette = palette,
      inventoryContent = inventoryContent,
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
    CrawlTheme(viewState.bookStyle) {
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
        onXRayChipClick = {},
        inventoryContent = PreviewInventoryContent,
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
      title = "The Psychology of Money",
      xrayChips = listOf(
        BookPlayViewState.XRayChipViewState(id = "compounding", label = "Compounding"),
        BookPlayViewState.XRayChipViewState(id = "tail-events", label = "Tail events"),
        BookPlayViewState.XRayChipViewState(id = "room-for-error", label = "Room for error"),
      ),
      bookStyle = BookStyle(),
      playbackSpeed = 1.1F,
      inventoryCount = 7,
    )
    yield(initial)
    yield(
      initial.copy(
        title = "Pride and Prejudice",
        chapterName = "Chapter 21",
        playing = false,
        xrayChips = listOf(
          BookPlayViewState.XRayChipViewState(id = "elizabeth", label = "Elizabeth Bennet"),
          BookPlayViewState.XRayChipViewState(id = "darcy", label = "Mr. Darcy"),
        ),
        bookStyle = BookStyle(
          palette = DefaultPalette.copy(background = Color(0xFFE7EEF2), highlight = Color(0xFF2F5D8A), shadow = Color(0xFFB4C4CF)),
          chapterType = ChapterTypes.getValue("classic"),
        ),
        sleepTimerState = BookPlayViewState.SleepTimerViewState.Enabled.WithDuration(12.minutes),
      ),
    )
    yield(initial.copy(title = "Das Ende der Welt", xrayChips = emptyList()))
  }
}

private object PreviewInventoryContent : InventoryContent {
  @Composable
  override fun Content(
    bookId: BookId,
    onClose: () -> Unit,
    modifier: Modifier,
  ) = Unit
}
