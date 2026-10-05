package voice.features.bookOverview.views

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.hardShadow
import voice.core.strings.R as StringsR

private val FabSize = 56.dp
private val IconSize = 24.dp

/** Opens The Deck. Sized like the play button on the other side of the library, and in the same colors. */
@Composable
internal fun DeckButton(
  onClick: () -> Unit,
  containerColor: Color,
  contentColor: Color,
  shadowColor: Color,
  modifier: Modifier = Modifier,
  palette: CrawlPalette? = null,
) {
  val containerColor = palette?.background ?: FloatingActionButtonDefaults.containerColor
  FloatingActionButton(
    modifier = modifier
      .size(FabSize)
      .hardShadow(color = shadowColor, shape = CircleShape, x = 3.dp, y = 3.dp),
    onClick = onClick,
    shape = CircleShape,
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
  ) {
    Icon(
      modifier = Modifier.size(IconSize),
      imageVector = DeckIcon,
      contentDescription = stringResource(id = StringsR.string.deck_open),
    )
  }
}

@Composable
private fun flatElevation() = FloatingActionButtonDefaults.elevation(
  defaultElevation = 0.dp,
  pressedElevation = 0.dp,
  focusedElevation = 0.dp,
  hoveredElevation = 0.dp,
)

/** Two cards, the second peeking out behind the first. */
private val DeckIcon: ImageVector = ImageVector.Builder(
  name = "Deck",
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24F,
  viewportHeight = 24F,
)
  .apply {
    addPath(
      pathData = addPathNodes("M7.5 3h9A2.5 2.5 0 0 1 19 5.5v11a2.5 2.5 0 0 1-2.5 2.5H16.5V5.5H7.5z"),
      fill = SolidColor(Color.Black),
    )
    addPath(
      pathData = addPathNodes("M5.5 7h9A1.5 1.5 0 0 1 16 8.5v11a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 4 19.5v-11A1.5 1.5 0 0 1 5.5 7z"),
      fill = SolidColor(Color.Black),
    )
  }
  .build()
