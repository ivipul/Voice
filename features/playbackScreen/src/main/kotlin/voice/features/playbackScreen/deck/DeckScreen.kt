package voice.features.playbackScreen.deck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.ui.crawl.CrawlLibraryTheme
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.crawl.hardShadow
import voice.core.ui.icons.VoiceIcons
import voice.core.xray.card.composeAt
import voice.core.xray.card.hasGameStats
import voice.features.playbackScreen.BookPlayDialogViewState
import voice.features.playbackScreen.view.holocard.CardUnit
import voice.features.playbackScreen.view.holocard.HoloCard
import voice.features.playbackScreen.view.holocard.HoloCardFront
import voice.features.playbackScreen.view.holocard.HoloTiltState
import voice.features.playbackScreen.view.holocard.animationsDisabled
import voice.features.playbackScreen.view.holocard.toImageModel
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface DeckGraph {
  val deckViewModel: DeckViewModel
}

@ContributesTo(AppScope::class)
interface DeckProvider {

  @Provides
  @IntoSet
  fun deckNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Deck> { key ->
    NavEntry(key) {
      CrawlLibraryTheme {
        DeckScreen()
      }
    }
  }
}

@Composable
fun DeckScreen() {
  val viewModel = retain<DeckViewModel> { rootGraphAs<DeckGraph>().deckViewModel }
  LaunchedEffect(viewModel) {
    viewModel.load()
  }
  DeckScreen(viewState = viewModel.viewState, onClose = viewModel::onClose)
}

private const val CARD_ASPECT = 5F / 7F
private const val MAX_CARD_WIDTH_DP = 380
private const val SCRIM_ALPHA = 0.92F
// The card rounds its own corners at 5% of its width; the tile follows so its clip and shadow hug the card.
private val TileShape = RoundedCornerShape(percent = 5)
private val GridTilts = listOf(-1.6F, 1.2F, -0.8F, 1.6F, -1.2F, 0.9F)

/** A tapped card on its way to the middle of the screen: where its tile sits in the window and how it is tilted there. */
private class ZoomTarget(
  val slot: DeckSlot.Unlocked,
  val from: Rect,
  val tilt: Float,
)

/**
 * The Deck: every crawler's holo card in two columns, like the Inventory's Strips. A card the listener has met shows
 * as of where they are in the books; one still ahead is a locked slot. Tapping a card zooms it into the middle of the
 * screen, where it flips and tilts like the card on the player.
 */
@Composable
internal fun DeckScreen(
  viewState: DeckViewState,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = crawlPaletteOf(null)
  var zoomed by remember { mutableStateOf<ZoomTarget?>(null) }
  Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = stringResource(id = StringsR.string.deck_title)) },
          navigationIcon = {
            IconButton(onClick = onClose) {
              Icon(
                imageVector = VoiceIcons.Close,
                contentDescription = stringResource(id = StringsR.string.common_action_close),
              )
            }
          },
        )
      },
    ) { paddingValues ->
      Box(modifier = Modifier.padding(paddingValues)) {
        when {
          viewState.slots.isNotEmpty() -> DeckGrid(
            viewState = viewState,
            palette = palette,
            onCardClick = { slot, bounds, tilt -> zoomed = ZoomTarget(slot, bounds, tilt) },
          )
          !viewState.loading -> Text(
            text = stringResource(id = StringsR.string.deck_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .align(Alignment.Center)
              .padding(24.dp),
          )
        }
      }
    }
    zoomed?.let { target ->
      ZoomedCard(target = target, onClosed = { zoomed = null })
    }
  }
}

@Composable
private fun DeckGrid(
  viewState: DeckViewState,
  palette: CrawlPalette,
  onCardClick: (DeckSlot.Unlocked, Rect, Float) -> Unit,
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item(key = "collected", span = { GridItemSpan(maxLineSpan) }) {
      CollectionBar(collected = viewState.collected, total = viewState.slots.size, palette = palette)
    }
    items(viewState.slots, key = { it.id }) { slot ->
      val tilt = GridTilts[(slot.id.hashCode() and Int.MAX_VALUE) % GridTilts.size]
      when (slot) {
        is DeckSlot.Unlocked -> CardTile(
          slot = slot,
          palette = palette,
          tilt = tilt,
          onClick = { bounds -> onCardClick(slot, bounds, tilt) },
        )
        is DeckSlot.Locked -> LockedSlot(
          slot = slot,
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
        text = stringResource(id = StringsR.string.deck_collected).uppercase(),
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

/** A met crawler: the front of their holo card at rest, tilted a little like the gallery cards of the Inventory. */
@Composable
private fun CardTile(
  slot: DeckSlot.Unlocked,
  palette: CrawlPalette,
  tilt: Float,
  onClick: (Rect) -> Unit,
) {
  var bounds by remember { mutableStateOf(Rect.Zero) }
  val card = remember(slot) { slot.data.composeAt(slot.positionMs) }
  val tiltState = remember { HoloTiltState(reducedMotion = true) }
  val description = stringResource(StringsR.string.holo_card_description, slot.name)
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(CARD_ASPECT)
      .onGloballyPositioned { bounds = it.boundsInWindow() }
      .graphicsLayer { rotationZ = tilt }
      .hardShadow(color = palette.shadow, shape = TileShape, x = 4.dp, y = 4.dp)
      .clip(TileShape)
      .semantics { contentDescription = description }
      .clickable { onClick(bounds) },
  ) {
    val density = LocalDensity.current
    val u = remember(maxWidth, density) { CardUnit(with(density) { maxWidth.toPx() } / 100F, density) }
    HoloCardFront(
      name = slot.name,
      description = slot.description,
      plain = !slot.data.hasGameStats,
      card = card,
      lookImage = card.look?.image?.let(slot.imageUris::get),
      portrait = slot.portrait?.toImageModel(),
      animate = false,
      tilt = tiltState,
      u = u,
    )
  }
}

/** A crawler still ahead: an empty dashed slot with a lock and the book to meet them in, nothing from the card itself. */
@Composable
private fun LockedSlot(
  slot: DeckSlot.Locked,
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
) {
  val faded = palette.content.copy(alpha = 0.6F)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(CARD_ASPECT)
      .clip(TileShape)
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
    if (slot.firstBook != Int.MAX_VALUE) {
      Text(
        text = stringResource(id = StringsR.string.deck_locked, slot.firstBook).uppercase(),
        style = CrawlType.label,
        color = faded,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * The tapped card grows from its tile to the middle of the screen over a white scrim and shrinks back to the tile when
 * dismissed with a tap outside it, back or a flick down. [onClosed] runs once it is back.
 */
@Composable
private fun ZoomedCard(
  target: ZoomTarget,
  onClosed: () -> Unit,
) {
  val context = LocalContext.current
  val reducedMotion = remember { animationsDisabled(context) }
  val progress = remember { Animatable(if (reducedMotion) 1F else 0F) }
  val scope = rememberCoroutineScope()
  var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
  var closing by remember { mutableStateOf(false) }

  fun dismiss() {
    if (closing) return
    closing = true
    scope.launch {
      progress.animateTo(0F, if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMedium))
      onClosed()
    }
  }

  LaunchedEffect(Unit) {
    progress.animateTo(1F, if (reducedMotion) snap() else spring(dampingRatio = 0.8F, stiffness = Spring.StiffnessMediumLow))
  }
  BackHandler(onBack = ::dismiss)

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
      .drawBehind { drawRect(Color.White.copy(alpha = SCRIM_ALPHA * progress.value.coerceIn(0F, 1F))) }
      .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
    contentAlignment = Alignment.Center,
  ) {
    val cardWidth = minOf(maxWidth * 0.86F, maxHeight * 0.74F * CARD_ASPECT, MAX_CARD_WIDTH_DP.dp)
    val density = LocalDensity.current
    val overlayCenter = Offset(with(density) { maxWidth.toPx() } / 2F, with(density) { maxHeight.toPx() } / 2F)
    val tileCenter = target.from.center - overlayOrigin
    val startScale = target.from.width / with(density) { cardWidth.toPx() }
    HoloCard(
      dialogState = BookPlayDialogViewState.XRayCardDialog(
        name = target.slot.name,
        description = target.slot.description,
        data = target.slot.data,
        imageUris = target.slot.imageUris,
        portrait = target.slot.portrait,
        openedAtMs = target.slot.positionMs,
      ),
      positionMs = target.slot.positionMs,
      width = cardWidth,
      reducedMotion = reducedMotion,
      onDismiss = ::dismiss,
      hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6F),
      popIn = false,
      modifier = Modifier.graphicsLayer {
        val p = progress.value
        val scale = startScale + (1F - startScale) * p
        scaleX = scale
        scaleY = scale
        translationX = (tileCenter.x - overlayCenter.x) * (1F - p)
        translationY = (tileCenter.y - overlayCenter.y) * (1F - p)
        rotationZ = target.tilt * (1F - p)
      },
    )
  }
}

private fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
  val width = 1.5.dp.toPx()
  drawRoundRect(
    color = color,
    topLeft = Offset(width / 2, width / 2),
    size = Size(size.width - width, size.height - width),
    cornerRadius = CornerRadius(size.width * 0.05F),
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
