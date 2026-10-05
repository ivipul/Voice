package voice.features.playbackScreen.view

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import voice.core.strings.R
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.icons.VoiceIcons

private val PeekShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val PeekHeight = 60.dp
private val SwipeUpToOpen = 24.dp
private val MaxLift = 160.dp

/**
 * The top of the Inventory peeking up from the bottom edge, with a handle and how many snips and reached strips it
 * holds. It runs under the navigation bar, keeping its row above [bottomInset]. A tap, or a swipe up that follows the
 * finger and is let go a little way up, opens the Inventory sheet.
 */
@Composable
internal fun InventoryPeek(
  count: Int,
  palette: CrawlPalette,
  bottomInset: Dp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOnClick by rememberUpdatedState(onClick)
  val lift = remember { Animatable(0F) }
  val scope = rememberCoroutineScope()
  val fill = MaterialTheme.colorScheme.surfaceContainerHigh
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp)
      .graphicsLayer { translationY = -lift.value }
      .drawBehind {
        drawRect(color = fill, topLeft = Offset(0F, size.height), size = Size(size.width, lift.value))
      }
      .clip(PeekShape)
      .background(fill)
      .clickable(role = Role.Button, onClick = onClick)
      .pointerInput(Unit) {
        val openAt = SwipeUpToOpen.toPx()
        val maxLift = MaxLift.toPx()
        detectVerticalDragGestures(
          onDragEnd = {
            val opens = lift.value > openAt
            scope.launch { lift.animateTo(0F) }
            if (opens) currentOnClick()
          },
          onDragCancel = { scope.launch { lift.animateTo(0F) } },
          onVerticalDrag = { _, amount -> scope.launch { lift.snapTo((lift.value - amount).coerceIn(0F, maxLift)) } },
        )
      }
      .padding(bottom = bottomInset),
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
        .fillMaxWidth()
        .height(PeekHeight)
        .padding(start = 20.dp, end = 12.dp, top = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = PosterIcons.Inventory,
        contentDescription = null,
        tint = palette.content,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.size(12.dp))
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
        imageVector = VoiceIcons.ChevronRight,
        contentDescription = null,
        tint = palette.content,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
