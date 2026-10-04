package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.core.strips.ActiveStripFrame
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState
import kotlin.math.roundToInt
import kotlin.time.Duration

/** How much of the cover's height sits above the chapter name; the rest runs under it as it fades. */
private const val STAGE_VISIBLE_FRACTION = 0.97F

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
  onInventoryLongClick: () -> Unit,
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
    SliderRow(
      duration = viewState.duration,
      playedTime = viewState.playedTime,
      stripZones = viewState.stripZones,
      palette = palette,
      onScrub = { scrubFraction = it },
      onStripZoneTap = onStripZoneTap,
      onSeek = onSeek,
    )
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
    Spacer(modifier = Modifier.size(12.dp))
    XRayChipRow(chips = viewState.xrayChips, palette = palette, onChipClick = onXRayChipClick)
    Spacer(modifier = Modifier.size(12.dp))
    CoPilotActions(
      palette = palette,
      onAskClick = onAskClick,
      onSnipClick = onSnipClick,
      onInventoryClick = onInventoryClick,
      onInventoryLongClick = onInventoryLongClick,
    )
  }
  if (useLandscapeLayout) {
    Row(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
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
          .fillMaxHeight()
          .aspectRatio(1F, matchHeightConstraintsFirst = true),
      )
      Column(
        modifier = Modifier.weight(1F),
        verticalArrangement = Arrangement.Center,
      ) {
        controls()
      }
    }
  } else {
    Column(
      modifier = Modifier
        .padding(contentPadding)
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
          .weight(1F, fill = false)
          .posterStage(),
      )
      controls()
      Spacer(modifier = Modifier.size(20.dp))
    }
  }
}

/**
 * As wide as the screen, and square unless the space left is shorter. It reports a little less height than it draws,
 * so the bottom of the fading cover runs under the chapter name.
 */
private fun Modifier.posterStage(): Modifier = layout { measurable, constraints ->
  val width = constraints.maxWidth
  val height = minOf(width, constraints.maxHeight)
  val placeable = measurable.measure(Constraints.fixed(width, height))
  layout(width, (height * STAGE_VISIBLE_FRACTION).roundToInt()) {
    placeable.place(0, 0)
  }
}
