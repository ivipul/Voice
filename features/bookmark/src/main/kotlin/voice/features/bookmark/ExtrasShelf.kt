package voice.features.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.hardShadow
import voice.core.strings.R as StringsR

private val CardShape = RoundedCornerShape(16.dp)
private val GridTilts = listOf(-1.4F, 1.1F, -0.7F, 1.4F, -1.1F, 0.8F)

/**
 * The book's Extras as a shelf: how many are unlocked, two columns of tilted cards, locked slots for the ones still
 * ahead, and a button to get more. There is no limit to how many a book can have.
 */
@Composable
internal fun ExtrasShelf(
  extras: List<ExtraCardViewState>,
  palette: CrawlPalette,
  onClick: (ExtraCardViewState) -> Unit,
  onMoreClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Fixed(2),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalItemSpacing = 20.dp,
  ) {
    if (extras.isEmpty()) {
      item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
        Text(
          text = stringResource(id = StringsR.string.inventory_extras_empty),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(vertical = 24.dp),
        )
      }
    } else {
      item(key = "collected", span = StaggeredGridItemSpan.FullLine) {
        CollectionBar(unlocked = extras.count { !it.locked }, total = extras.size, palette = palette)
      }
    }
    items(extras, key = { it.id }) { extra ->
      val tilt = GridTilts[(extra.id.hashCode() and Int.MAX_VALUE) % GridTilts.size]
      if (extra.locked) {
        LockedSlot(extra = extra, palette = palette, modifier = Modifier.graphicsLayer { rotationZ = tilt })
      } else {
        ExtraCard(
          extra = extra,
          palette = palette,
          onClick = { onClick(extra) },
          modifier = Modifier.graphicsLayer { rotationZ = tilt },
        )
      }
    }
    item(key = "more", span = StaggeredGridItemSpan.FullLine) {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
          onClick = onMoreClick,
          colors = ButtonDefaults.buttonColors(containerColor = palette.highlight, contentColor = palette.onHighlight),
        ) {
          Text(text = stringResource(id = StringsR.string.inventory_extras_more))
        }
      }
    }
  }
}

@Composable
private fun CollectionBar(
  unlocked: Int,
  total: Int,
  palette: CrawlPalette,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(id = StringsR.string.inventory_extras_collected).uppercase(),
        style = CrawlType.label,
        color = palette.content,
        modifier = Modifier.weight(1F),
      )
      Text(
        text = "$unlocked / $total",
        style = CrawlType.speed,
        color = palette.content,
      )
    }
    Spacer(modifier = Modifier.size(8.dp))
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(10.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(width = 1.5.dp, color = palette.content, shape = CircleShape),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(unlocked.toFloat() / total)
          .fillMaxHeight()
          .background(palette.highlight),
      )
    }
  }
}

/** An unlocked Extra: ink outline, the player's hard drop shadow, its kind as a tag, title and a line about it. */
@Composable
private fun ExtraCard(
  extra: ExtraCardViewState,
  palette: CrawlPalette,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 150.dp)
      .hardShadow(color = palette.shadow, shape = CardShape, x = 4.dp, y = 4.dp)
      .clip(CardShape)
      .background(palette.background)
      .border(width = 1.5.dp, color = palette.content, shape = CardShape)
      .clickable(onClick = onClick)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (extra.kind.isNotBlank()) {
      Text(
        text = extra.kind.uppercase(),
        style = CrawlType.label,
        color = palette.onHighlight,
        maxLines = 1,
        modifier = Modifier
          .background(color = palette.highlight, shape = CircleShape)
          .padding(horizontal = 10.dp, vertical = 5.dp),
      )
    }
    Text(
      text = extra.title.uppercase(),
      style = CrawlType.chip,
      color = palette.content,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = extra.description,
      style = MaterialTheme.typography.bodySmall,
      color = palette.content,
      maxLines = 4,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** An Extra still ahead: an empty dashed slot with a lock and where it unlocks, nothing from the Extra itself. */
@Composable
private fun LockedSlot(
  extra: ExtraCardViewState,
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
) {
  val faded = palette.content.copy(alpha = 0.6F)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 150.dp)
      .clip(CardShape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .dashedOutline(color = faded)
      .padding(16.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = LockIcon,
      contentDescription = null,
      tint = faded,
      modifier = Modifier.size(28.dp),
    )
    Spacer(modifier = Modifier.size(10.dp))
    Text(
      text = stringResource(id = StringsR.string.inventory_extra_locked, extra.unlocksAt.orEmpty()).uppercase(),
      style = CrawlType.label,
      color = faded,
      textAlign = TextAlign.Center,
    )
  }
}

private fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
  val width = 1.5.dp.toPx()
  drawRoundRect(
    color = color,
    topLeft = Offset(width / 2, width / 2),
    size = Size(size.width - width, size.height - width),
    cornerRadius = CornerRadius(16.dp.toPx()),
    style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
  )
}

private val LockIcon: ImageVector = ImageVector.Builder(
  name = "Lock",
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24F,
  viewportHeight = 24F,
)
  .apply {
    addPath(
      pathData = addPathNodes("M7.5 11V8a4.5 4.5 0 0 1 9 0v3"),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2F,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
      pathData = addPathNodes("M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z"),
      fill = SolidColor(Color.Black),
    )
  }
  .build()
