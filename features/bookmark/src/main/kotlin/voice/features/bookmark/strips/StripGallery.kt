package voice.features.bookmark.strips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import voice.core.strips.AvailableStrip
import voice.core.ui.crawl.CrawlFonts
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.hardShadow
import voice.core.strings.R as StringsR

/** The two ways to lay out the unlocked strips, side by side until one of them stays. */
internal enum class StripGalleryLayout {
  /** Two columns of tilted comic cards. */
  Grid,

  /** One big card per row, like a stack of comic issues. */
  Issues,
}

/** A strip's place in the gallery: reached, so it shows, or still ahead, so only its slot does. */
internal sealed interface StripSlot {
  val strip: AvailableStrip

  data class Unlocked(override val strip: AvailableStrip) : StripSlot

  data class Locked(override val strip: AvailableStrip) : StripSlot
}

/** Reached and locked strips together, in the order of their chapters. */
internal fun stripSlots(
  unlocked: List<AvailableStrip>,
  locked: List<AvailableStrip>,
): List<StripSlot> = (unlocked.map { StripSlot.Unlocked(it) } + locked.map { StripSlot.Locked(it) })
  .sortedBy { it.strip.manifest.chapter }

private const val DEFAULT_COVER_ASPECT = 9F / 16F

// An issue's picture is cut to a comic cover's 2:3 when it is taller, so one card stays about a screen high.
private const val ISSUE_MIN_ASPECT = 2F / 3F
private const val LOCKED_ISSUE_ASPECT = 2.4F
private val CardShape = RoundedCornerShape(16.dp)
private val GridTilts = listOf(-1.6F, 1.2F, -0.8F, 1.6F, -1.2F, 0.9F)
private val IssueTitle = TextStyle(
  fontFamily = CrawlFonts.BigShouldersDisplay,
  fontWeight = FontWeight.SemiBold,
  fontSize = 26.sp,
  lineHeight = 28.sp,
)

/**
 * Every strip of the book as a collection: a bar with how many are collected, then the reached strips as comic cards
 * with their chapter, and the ones still ahead as locked slots that give nothing away.
 */
@Composable
internal fun StripGallery(
  strips: List<AvailableStrip>,
  lockedStrips: List<AvailableStrip>,
  layout: StripGalleryLayout,
  palette: CrawlPalette,
  onClick: (AvailableStrip) -> Unit,
  modifier: Modifier = Modifier,
) {
  val slots = remember(strips, lockedStrips) { stripSlots(strips, lockedStrips) }
  if (slots.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(id = StringsR.string.bookmark_strips_empty),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
      )
    }
    return
  }
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Fixed(if (layout == StripGalleryLayout.Grid) 2 else 1),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalItemSpacing = if (layout == StripGalleryLayout.Grid) 20.dp else 28.dp,
  ) {
    item(key = "collected", span = StaggeredGridItemSpan.FullLine) {
      CollectionBar(collected = strips.size, total = slots.size, palette = palette)
    }
    items(slots, key = { it.strip.manifest.chapter }) { slot ->
      val tilt = GridTilts[slot.strip.manifest.chapter % GridTilts.size] * if (layout == StripGalleryLayout.Grid) 1F else 0.5F
      when (slot) {
        is StripSlot.Unlocked -> StripCard(
          strip = slot.strip,
          layout = layout,
          palette = palette,
          onClick = { onClick(slot.strip) },
          modifier = Modifier.graphicsLayer { rotationZ = tilt },
        )
        is StripSlot.Locked -> LockedSlot(
          strip = slot.strip,
          layout = layout,
          palette = palette,
          modifier = Modifier.graphicsLayer { rotationZ = tilt },
        )
      }
    }
  }
}

@Composable
private fun CollectionBar(
  collected: Int,
  total: Int,
  palette: CrawlPalette,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(id = StringsR.string.inventory_strips_collected).uppercase(),
        style = CrawlType.label,
        color = palette.content,
        modifier = Modifier.weight(1F),
      )
      Text(
        text = "$collected / $total",
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
          .fillMaxWidth(collected.toFloat() / total)
          .fillMaxHeight()
          .background(palette.highlight),
      )
    }
  }
}

/** A reached strip as a comic card: ink outline, the hard drop shadow of the player, its chapter as a tag. */
@Composable
private fun StripCard(
  strip: AvailableStrip,
  layout: StripGalleryLayout,
  palette: CrawlPalette,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val aspect = strip.coverAspect ?: DEFAULT_COVER_ASPECT
  Column(
    modifier = modifier
      .fillMaxWidth()
      .hardShadow(color = palette.shadow, shape = CardShape, x = 4.dp, y = 4.dp)
      .clip(CardShape)
      .background(palette.background)
      .border(width = 1.5.dp, color = palette.content, shape = CardShape)
      .clickable(onClick = onClick),
  ) {
    Box {
      AsyncImage(
        model = strip.coverFile(),
        contentDescription = strip.manifest.title,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(if (layout == StripGalleryLayout.Issues) maxOf(aspect, ISSUE_MIN_ASPECT) else aspect),
      )
      ChapterTag(
        chapter = strip.manifest.chapter,
        palette = palette,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp),
      )
    }
    when (layout) {
      StripGalleryLayout.Grid -> Text(
        text = strip.manifest.title.uppercase(),
        style = CrawlType.chip,
        color = palette.content,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      )
      StripGalleryLayout.Issues -> Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = strip.manifest.title,
          style = IssueTitle,
          color = palette.content,
        )
        Text(
          text = strip.manifest.summary,
          style = MaterialTheme.typography.bodyMedium,
          color = palette.content.copy(alpha = 0.8F),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** A strip still ahead: an empty dashed slot with a lock and the chapter that unlocks it, nothing from the strip itself. */
@Composable
private fun LockedSlot(
  strip: AvailableStrip,
  layout: StripGalleryLayout,
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
) {
  val aspect = if (layout == StripGalleryLayout.Issues) LOCKED_ISSUE_ASPECT else strip.coverAspect ?: DEFAULT_COVER_ASPECT
  val faded = palette.content.copy(alpha = 0.6F)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(aspect)
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
      text = stringResource(id = StringsR.string.inventory_strip_locked, strip.manifest.chapter).uppercase(),
      style = CrawlType.label,
      color = faded,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun ChapterTag(
  chapter: Int,
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
) {
  Text(
    text = stringResource(id = StringsR.string.bookmark_strips_chapter, chapter).uppercase(),
    style = CrawlType.label,
    color = palette.onHighlight,
    maxLines = 1,
    modifier = modifier
      .background(color = palette.highlight, shape = CircleShape)
      .padding(horizontal = 10.dp, vertical = 5.dp),
  )
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
