package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.core.strips.ActiveStripFrame
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration

@Composable
internal fun BookPlayContent(
  contentPadding: PaddingValues,
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
  onCurrentChapterClick: () -> Unit,
  onSmartRecapClick: () -> Unit,
  onSpeedClick: () -> Unit,
  onAskClick: () -> Unit,
  onSnipClick: () -> Unit,
  onInventoryClick: () -> Unit,
  onXRayChipClick: (String) -> Unit,
) {
  val palette = crawlPaletteOf(viewState.edition)
  val cover: Any? = viewState.edition?.cover ?: viewState.cover
  var scrubFraction by remember { mutableStateOf<Float?>(null) }
  val shownTime = scrubFraction?.let { viewState.duration * it.toDouble() } ?: viewState.playedTime
  val controls: @Composable ColumnScope.() -> Unit = {
    ChapterLine(
      chapterName = viewState.chapterName,
      time = formatTime(
        timeMs = shownTime.inWholeMilliseconds,
        durationMs = viewState.duration.inWholeMilliseconds,
      ),
      palette = palette,
      type = viewState.edition?.chapterType ?: CrawlDisplayType.Default,
      onChapterClick = onCurrentChapterClick,
    )
    Spacer(modifier = Modifier.size(12.dp))
    XRayChipRow(chips = viewState.xrayChips, palette = palette, onChipClick = onXRayChipClick)
    Spacer(modifier = Modifier.size(12.dp))
    SliderRow(
      duration = viewState.duration,
      playedTime = viewState.playedTime,
      stripZones = viewState.stripZones,
      palette = palette,
      onScrub = { scrubFraction = it },
      onStripZoneTap = onStripZoneTap,
      onSeek = onSeek,
    )
    Spacer(modifier = Modifier.size(8.dp))
    TransportRow(
      playing = viewState.playing,
      playbackSpeed = viewState.playbackSpeed,
      skipBackSeconds = viewState.skipBackSeconds,
      skipForwardSeconds = viewState.skipForwardSeconds,
      palette = palette,
      onSmartRecapClick = onSmartRecapClick,
      onRewindClick = onRewindClick,
      onSkipToPrevious = onSkipToPrevious,
      onPlayClick = onPlayClick,
      onFastForwardClick = onFastForwardClick,
      onSkipToNext = onSkipToNext,
      onSpeedClick = onSpeedClick,
    )
    Spacer(modifier = Modifier.size(24.dp))
    CoPilotActions(
      palette = palette,
      onAskClick = onAskClick,
      onSnipClick = onSnipClick,
    )
  }
  // The Inventory card runs under the navigation bar, so only the card keeps clear of it.
  val bottomInset = contentPadding.calculateBottomPadding()
  val inventoryPeek: @Composable () -> Unit = {
    InventoryPeek(
      count = viewState.inventoryCount,
      palette = palette,
      bottomInset = bottomInset,
      onClick = onInventoryClick,
    )
  }
  val layoutDirection = LocalLayoutDirection.current
  if (useLandscapeLayout) {
    Row(
      modifier = Modifier
        .padding(contentPadding.withoutBottom(layoutDirection))
        .fillMaxSize(),
    ) {
      CoverStage(
        bookId = bookId,
        cover = cover,
        stripFrame = viewState.stripFrame,
        sleepTimerState = viewState.sleepTimerState,
        palette = palette,
        onPlayClick = onPlayClick,
        onStripFrameClick = onStripFrameClick,
        alwaysFadeSides = true,
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight()
          .padding(bottom = bottomInset),
      )
      Column(
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight(),
      ) {
        Box(
          modifier = Modifier
            .weight(1F)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          ) {
            controls()
          }
        }
        Spacer(modifier = Modifier.size(12.dp))
        inventoryPeek()
      }
    }
  } else {
    Column(
      modifier = Modifier
        .padding(contentPadding.withoutBottom(layoutDirection))
        .fillMaxSize(),
    ) {
      CoverStage(
        bookId = bookId,
        cover = cover,
        stripFrame = viewState.stripFrame,
        sleepTimerState = viewState.sleepTimerState,
        palette = palette,
        onPlayClick = onPlayClick,
        onStripFrameClick = onStripFrameClick,
        modifier = Modifier
          .weight(1F)
          .fillMaxWidth(),
      )
      controls()
      Spacer(modifier = Modifier.size(24.dp))
      inventoryPeek()
    }
  }
}

private fun PaddingValues.withoutBottom(layoutDirection: LayoutDirection): PaddingValues = PaddingValues(
  start = calculateStartPadding(layoutDirection),
  top = calculateTopPadding(),
  end = calculateEndPadding(layoutDirection),
)
