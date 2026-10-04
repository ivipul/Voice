package voice.features.playbackScreen.view

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import voice.core.data.BookId
import voice.core.strips.ActiveStripFrame
import voice.core.ui.VoiceTheme
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
  onSeek: (Duration) -> Unit,
  onStripZoneTap: (Long) -> Unit,
  onStripFrameClick: (ActiveStripFrame) -> Unit,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCloseClick: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onCatchMeUpClick: () -> Unit,
  onAskClick: () -> Unit,
  onSnipClick: () -> Unit,
  onFeedClick: () -> Unit,
  onXRayChipClick: (String) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState)
    },
    topBar = {
      BookPlayAppBar(
        viewState = viewState,
        onSleepTimerClick = onSleepTimerClick,
        onBookmarkClick = onBookmarkClick,
        onBookmarkLongClick = onBookmarkLongClick,
        onSpeedChangeClick = onSpeedChangeClick,
        onSkipSilenceClick = onSkipSilenceClick,
        onVolumeBoostClick = onVolumeBoostClick,
        onCloseClick = onCloseClick,
        useLandscapeLayout = useLandscapeLayout,
      )
    },
    content = {
      BookPlayContent(
        contentPadding = it,
        viewState = viewState,
        bookId = bookId,
        onPlayClick = onPlayClick,
        onRewindClick = onRewindClick,
        onFastForwardClick = onFastForwardClick,
        onSeek = onSeek,
        onStripZoneTap = onStripZoneTap,
        onStripFrameClick = onStripFrameClick,
        onSkipToNext = onSkipToNext,
        onSkipToPrevious = onSkipToPrevious,
        onCurrentChapterClick = onCurrentChapterClick,
        onCatchMeUpClick = onCatchMeUpClick,
        onAskClick = onAskClick,
        onSnipClick = onSnipClick,
        onFeedClick = onFeedClick,
        onXRayChipClick = onXRayChipClick,
        useLandscapeLayout = useLandscapeLayout,
      )
    },
  )
}

@Composable
@Preview
private fun BookPlayPreview(
  @PreviewParameter(BookPlayViewStatePreviewProvider::class)
  viewState: BookPlayViewState,
) {
  VoiceTheme {
    BookPlayView(
      viewState = viewState,
      bookId = BookId("preview"),
      onPlayClick = {},
      onRewindClick = {},
      onFastForwardClick = {},
      onSeek = {},
      onStripZoneTap = {},
      onStripFrameClick = {},
      onSleepTimerClick = {},
      onBookmarkClick = {},
      onBookmarkLongClick = {},
      onSpeedChangeClick = {},
      onSkipSilenceClick = {},
      onVolumeBoostClick = {},
      onSkipToNext = {},
      onSkipToPrevious = {},
      onCloseClick = {},
      onCurrentChapterClick = {},
      onCatchMeUpClick = {},
      onAskClick = {},
      onSnipClick = {},
      onFeedClick = {},
      onXRayChipClick = {},
      useLandscapeLayout = false,
    )
  }
}

private class BookPlayViewStatePreviewProvider : PreviewParameterProvider<BookPlayViewState> {
  override val values = sequence {
    val initial = BookPlayViewState(
      chapterName = "My Chapter",
      showPreviousNextButtons = false,
      cover = null,
      duration = 10.minutes,
      playedTime = 3.minutes,
      playing = true,
      skipSilence = true,
      skipBackSeconds = 15,
      skipForwardSeconds = 30,
      sleepTimerState = BookPlayViewState.SleepTimerViewState.Disabled,
      title = "Das Ende der Welt",
      xrayChips = listOf(
        BookPlayViewState.XRayChipViewState(id = "carl", label = "Carl"),
        BookPlayViewState.XRayChipViewState(id = "donut", label = "Princess Donut"),
      ),
    )
    yield(initial)
    yield(
      initial.copy(
        showPreviousNextButtons = !initial.showPreviousNextButtons,
        playing = !initial.playing,
        skipSilence = !initial.skipSilence,
      ),
    )
    yield(initial.copy(chapterName = null))
    yield(initial.copy(xrayChips = emptyList()))
  }
}
