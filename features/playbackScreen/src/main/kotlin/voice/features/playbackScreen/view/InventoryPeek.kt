package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.icons.VoiceIcons

private val PeekShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val PeekHeight = 60.dp
private val SwipeUpToOpen = 24.dp

/**
 * The top of the Inventory peeking up from the bottom edge, with how many snips and reached strips it holds.
 * It runs under the navigation bar, keeping its row above [bottomInset]. A tap or a swipe up opens the Inventory.
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
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp)
      .clip(PeekShape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(role = Role.Button, onClick = onClick)
      .pointerInput(Unit) {
        var dragged = 0F
        detectVerticalDragGestures(
          onDragStart = { dragged = 0F },
          onDragEnd = { if (dragged < -SwipeUpToOpen.toPx()) currentOnClick() },
          onVerticalDrag = { _, amount -> dragged += amount },
        )
      }
      .padding(bottom = bottomInset)
      .height(PeekHeight)
      .padding(start = 20.dp, end = 12.dp),
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
