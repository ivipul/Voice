package voice.features.playbackScreen.view

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.strings.R
import voice.core.ui.InventoryContent
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.icons.VoiceIcons
import kotlin.math.abs

internal val InventoryPeekHeight = 60.dp
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SheetTopGap = 12.dp
private val SettleThreshold = 6.dp
private const val SETTLE_VELOCITY_PX = 400F
private const val SCRIM_ALPHA = 0.32F

/**
 * The Inventory as a sheet over the player. Collapsed it is a card at the bottom edge, under the navigation bar,
 * with how many snips and reached strips it holds. Its header follows the finger up or down, and let go anywhere
 * it carries on from there: up opens it all the way, down closes it. A tap on the header does the same.
 */
@Composable
internal fun InventorySheet(
  bookId: BookId,
  count: Int,
  palette: CrawlPalette,
  inventoryContent: InventoryContent,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  var open by rememberSaveable { mutableStateOf(false) }
  var progress by remember { mutableFloatStateOf(if (open) 1F else 0F) }
  var settling by remember { mutableStateOf<Job?>(null) }
  val scope = rememberCoroutineScope()
  val showContent by remember { derivedStateOf { progress > 0F } }

  fun settle(target: Float) {
    settling?.cancel()
    open = target > 0.5F
    settling = scope.launch {
      animate(initialValue = progress, targetValue = target) { value, _ -> progress = value }
    }
  }
  BackHandler(enabled = open) { settle(0F) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val sheetHeight = maxHeight - statusBar - SheetTopGap
    val peekPx = with(density) { (InventoryPeekHeight + navigationBar).toPx() }
    val rangePx = (with(density) { sheetHeight.toPx() } - peekPx).coerceAtLeast(1F)
    var dragStartProgress by remember { mutableFloatStateOf(0F) }
    val dragState = rememberDraggableState { delta ->
      progress = (progress - delta / rangePx).coerceIn(0F, 1F)
    }

    if (showContent) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = progress }
          .background(Color.Black.copy(alpha = SCRIM_ALPHA))
          .clickable(interactionSource = null, indication = null) { settle(0F) },
      )
    }
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(sheetHeight)
        .graphicsLayer { translationY = (1F - progress) * rangePx }
        .clip(SheetShape)
        .background(palette.background)
        .border(width = 1.5.dp, color = palette.content, shape = SheetShape),
    ) {
      InventoryHeader(
        count = count,
        palette = palette,
        progress = { progress },
        modifier = Modifier
          .clickable(role = Role.Button) { settle(if (open) 0F else 1F) }
          .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = {
              settling?.cancel()
              dragStartProgress = progress
            },
            onDragStopped = { velocity ->
              val movedPx = (progress - dragStartProgress) * rangePx
              val upward = when {
                abs(movedPx) > with(density) { SettleThreshold.toPx() } -> movedPx > 0F
                abs(velocity) > SETTLE_VELOCITY_PX -> velocity < 0F
                else -> dragStartProgress > 0.5F
              }
              settle(if (upward) 1F else 0F)
            },
          ),
      )
      if (showContent) {
        inventoryContent.Content(
          bookId = bookId,
          onClose = { settle(0F) },
          modifier = Modifier
            .weight(1F)
            .fillMaxWidth()
            .padding(bottom = navigationBar),
        )
      }
    }
  }
}

@Composable
private fun InventoryHeader(
  count: Int,
  palette: CrawlPalette,
  progress: () -> Float,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(InventoryPeekHeight),
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 8.dp)
        .size(width = 36.dp, height = 4.dp)
        .clip(CircleShape)
        .background(palette.content.copy(alpha = 0.4F)),
    )
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 20.dp, end = 12.dp, top = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = PosterIcons.Inventory,
        contentDescription = null,
        tint = palette.content,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = stringResource(id = R.string.inventory_title).uppercase(),
        style = CrawlType.button,
        color = palette.content,
        maxLines = 1,
        modifier = Modifier.weight(1F),
      )
      Text(
        text = count.toString(),
        style = CrawlType.speed,
        color = palette.content,
      )
      Icon(
        imageVector = VoiceIcons.ExpandMore,
        contentDescription = null,
        tint = palette.content,
        modifier = Modifier
          .size(24.dp)
          .graphicsLayer { rotationZ = 180F * (1F - progress()) },
      )
    }
  }
}
