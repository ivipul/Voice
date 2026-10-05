package voice.features.bookOverview.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import voice.core.data.BookId
import voice.core.ui.BookCardCorner
import voice.core.ui.bookCardBackdropModifier
import voice.core.ui.bookCardContentModifier
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.crawl.hardShadow
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.core.ui.R as UiR

// The top of a card shows above the next card in the stack; the rest of it is tucked under that card.
private val ShowingHeight = 128.dp
private val TuckedHeight = 48.dp
private val CardShape = RoundedCornerShape(BookCardCorner)
private val ShowingShape = RoundedCornerShape(topStart = BookCardCorner, topEnd = BookCardCorner)
private val CoverShape = RoundedCornerShape(16.dp)

// Room under the last card for the play button that floats over the list.
private val PlayButtonClearance = 88.dp

// A card's title is the book's chapter face at half the size the player sets chapter names in.
private const val TITLE_SCALE = 0.5F
private const val TITLE_LINE_HEIGHT = 1.1F
private const val FULL_PERCENT = 100

// Any scroll leans the cards 10 degrees; a faster one adds half a degree per px a frame, up to 20 degrees.
private const val BASE_TILT_DEGREES = 10F
private const val TILT_DEGREES_PER_PX = 0.5F
private const val MAX_TILT_DEGREES = 20F

// Below this speed (px a frame) the list counts as still, so a resting finger doesn't make the cards flicker.
private const val STILL_SPEED_PX = 0.3F

// Each frame's scroll moves the speed only a fifth of the way, so a jumpy finger gives a steady lean.
private const val SPEED_SMOOTHING = 0.2F

// The lean eases in and out slowly, with no bounce.
private val TiltSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 60F)

// A near camera, so the edge that tips away visibly narrows.
private const val TILT_CAMERA_DISTANCE = 7F

// Cards tip about the middle of the part that shows, not of the whole card with its tucked part.
private val TiltPivot = TransformOrigin(pivotFractionX = 0.5F, pivotFractionY = 0.36F)

/**
 * How far the cards lean at a scroll speed of [speedPx] a frame: forward (top edge away) while the list scrolls down,
 * back while it scrolls up. [speedPx] is the content's move, negative when it moves up as the list scrolls down.
 */
internal fun cardTilt(speedPx: Float): Float {
  val speed = abs(speedPx)
  if (speed < STILL_SPEED_PX) return 0F
  val lean = (BASE_TILT_DEGREES + speed * TILT_DEGREES_PER_PX).coerceAtMost(MAX_TILT_DEGREES)
  return if (speedPx < 0F) lean else -lean
}

/** The scroll speed after a frame that moved [scrolledPx], from the [previous] speed. */
internal fun smoothedScrollSpeed(
  previous: Float,
  scrolledPx: Float,
): Float = previous + (scrolledPx - previous) * SPEED_SMOOTHING

/** The library as a stack of cards in each book's own colors, each tucked under the next like cards in a wallet. */
@Composable
internal fun StackBooks(
  books: Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>>,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  showPermissionBugCard: Boolean,
  onPermissionBugCardClick: () -> Unit,
) {
  val listState = rememberLazyListState()
  val scrollSpeed = remember { mutableFloatStateOf(0F) }
  val scrollTilt = remember {
    object : NestedScrollConnection {
      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        scrollSpeed.floatValue = smoothedScrollSpeed(scrollSpeed.floatValue, consumed.y)
        return Offset.Zero
      }
    }
  }
  LaunchedEffect(listState.isScrollInProgress) {
    if (!listState.isScrollInProgress) scrollSpeed.floatValue = 0F
  }
  val tilt = remember { Animatable(0F) }
  LaunchedEffect(tilt) {
    snapshotFlow { cardTilt(scrollSpeed.floatValue) }.collectLatest { target ->
      tilt.animateTo(target, TiltSpring)
    }
  }
  LazyColumn(
    state = listState,
    modifier = Modifier.nestedScroll(scrollTilt),
    contentPadding = PaddingValues(top = 16.dp, start = 12.dp, end = 12.dp),
  ) {
    if (showPermissionBugCard) {
      item {
        Column {
          PermissionBugCard(onPermissionBugCardClick)
          Spacer(Modifier.size(16.dp))
        }
      }
    }
    books.values.forEach { categoryBooks ->
      items(
        items = categoryBooks.toList(),
        key = { (bookId, _) -> bookId.value },
        contentType = { "book" },
      ) { (_, bookState) ->
        StackBookCard(
          book = bookState.value,
          onBookClick = onBookClick,
          onBookLongClick = onBookLongClick,
          modifier = Modifier.graphicsLayer {
            rotationX = tilt.value
            cameraDistance = TILT_CAMERA_DISTANCE * density
            transformOrigin = TiltPivot
          },
        )
      }
    }
    item {
      Column {
        Spacer(Modifier.height(TuckedHeight + PlayButtonClearance))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
      }
    }
  }
}

/**
 * A book as a card in its own colors: the cover on the left; the author, the title in the book's display face, the time
 * left and how far in on the right. Only the top of the card takes up room in the list, so the next card covers the
 * rest. A tap grows the card into the book's player.
 */
@Composable
internal fun StackBookCard(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = cardPaletteOf(book.edition)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .tuckedUnderNext(TuckedHeight)
      .height(ShowingHeight + TuckedHeight)
      .shadow(elevation = 6.dp, shape = CardShape)
      .background(palette.background),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(ShowingHeight)
        .bookCardBackdropModifier(book.id, onCard = true)
        .background(color = palette.background, shape = ShowingShape),
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(ShowingHeight)
        .bookCardContentModifier(book.id, onCard = true)
        .clip(ShowingShape)
        .combinedClickable(
          onClick = { onBookClick(book.id) },
          onLongClick = { onBookLongClick(book.id) },
        )
        .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      AsyncImage(
        model = book.artwork ?: book.cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(id = UiR.drawable.album_art),
        error = painterResource(id = UiR.drawable.album_art),
        modifier = Modifier
          .size(ShowingHeight - 32.dp)
          .hardShadow(color = palette.shadow, shape = CoverShape, x = 3.dp, y = 3.dp)
          .clip(CoverShape)
          .border(width = 1.5.dp, color = palette.content, shape = CoverShape),
      )
      CardText(
        book = book,
        palette = palette,
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight(),
      )
    }
  }
}

@Composable
private fun CardText(
  book: BookOverviewItemViewState,
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
) {
  val titleType = book.edition?.chapterType ?: CrawlDisplayType.Default
  Column(modifier = modifier) {
    if (book.author != null) {
      Text(
        text = book.author.uppercase(),
        style = CrawlType.label,
        color = palette.content.copy(alpha = 0.75F),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = if (titleType.uppercase) book.name.uppercase() else book.name,
      style = TextStyle(
        fontFamily = titleType.fontFamily,
        fontWeight = titleType.fontWeight,
        fontSize = titleType.fontSize * TITLE_SCALE,
        lineHeight = titleType.fontSize * TITLE_SCALE * TITLE_LINE_HEIGHT,
      ),
      color = palette.content,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.weight(1F))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = book.remainingTime,
        style = CrawlType.time,
        color = palette.content,
        modifier = Modifier.weight(1F),
      )
      if (book.progress > 0F) {
        Text(
          text = "${(book.progress * FULL_PERCENT).toInt()}%",
          style = CrawlType.badge,
          color = palette.content,
        )
      }
    }
    if (book.progress > 0.05F) {
      Spacer(modifier = Modifier.size(6.dp))
      ProgressTrack(progress = book.progress, palette = palette)
    }
  }
}

@Composable
private fun ProgressTrack(
  progress: Float,
  palette: CrawlPalette,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(4.dp)
      .clip(CircleShape)
      .background(palette.content.copy(alpha = 0.15F)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(progress)
        .fillMaxHeight()
        .clip(CircleShape)
        .background(palette.highlight),
    )
  }
}

/** A series book's palette; any other book gets a card a shade off the screen so it still stands out from it. */
@Composable
private fun cardPaletteOf(edition: CrawlEdition?): CrawlPalette {
  val palette = crawlPaletteOf(edition)
  return if (edition == null) palette.copy(background = MaterialTheme.colorScheme.surfaceContainerHigh) else palette
}

/** Takes up only the card above [tucked], so the next item in the list is laid over the rest of it. */
private fun Modifier.tuckedUnderNext(tucked: Dp): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints)
  layout(placeable.width, (placeable.height - tucked.roundToPx()).coerceAtLeast(0)) {
    placeable.place(0, 0)
  }
}

@Composable
@Preview
private fun StackBookCardPreview() {
  StackBookCard(
    book = BookOverviewPreviewParameterProvider().book().copy(
      name = "1. Dungeon Crawler Carl",
      progress = 0.6F,
      edition = CrawlEdition.DungeonCrawlerCarl,
    ),
    onBookClick = {},
    onBookLongClick = {},
  )
}
