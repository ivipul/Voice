package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.features.playbackScreen.BookPlayViewState

private val ChipHeight = 40.dp

/**
 * The characters in the scene, in one scrolling row. It keeps its height while nobody is in the scene, so the buttons
 * below don't jump as chips come and go.
 */
@Composable
internal fun XRayChipRow(
  chips: List<BookPlayViewState.XRayChipViewState>,
  palette: CrawlPalette,
  onChipClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  LazyRow(
    state = listState,
    modifier = modifier
      .fillMaxWidth()
      .height(ChipHeight)
      .fadeTrailingEdge(enabled = listState.canScrollForward),
    contentPadding = PaddingValues(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(chips, key = { it.id }) { chip ->
      XRayChip(label = chip.label, palette = palette, onClick = { onChipClick(chip.id) })
    }
  }
}

@Composable
private fun XRayChip(
  label: String,
  palette: CrawlPalette,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .height(ChipHeight)
      .clip(CircleShape)
      .background(palette.highlight)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label.uppercase(),
      style = CrawlType.chip,
      color = palette.onHighlight,
      maxLines = 1,
    )
  }
}

/** Fades the row out at its end while there are more chips to scroll to. */
private fun Modifier.fadeTrailingEdge(enabled: Boolean): Modifier {
  if (!enabled) return this
  return this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()
      drawRect(
        brush = Brush.horizontalGradient(0.82F to Color.Black, 1F to Color.Transparent),
        blendMode = BlendMode.DstIn,
      )
    }
}
