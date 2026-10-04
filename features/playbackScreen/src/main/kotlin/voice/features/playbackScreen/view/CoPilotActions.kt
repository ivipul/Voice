package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType

/** Ask AI, filled in the accent color, then Snip and Inventory as outlines. A long press on Inventory adds a bookmark. */
@Composable
internal fun CoPilotActions(
  palette: CrawlPalette,
  onAskClick: () -> Unit,
  onSnipClick: () -> Unit,
  onInventoryClick: () -> Unit,
  onInventoryLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ActionPill(
      icon = PosterIcons.Ask,
      label = stringResource(id = R.string.copilot_action_ask_ai),
      container = palette.accent,
      content = palette.onAccent,
      onClick = onAskClick,
      modifier = Modifier.weight(1F),
    )
    ActionPill(
      icon = PosterIcons.Snip,
      label = stringResource(id = R.string.copilot_action_snip),
      container = null,
      content = palette.content,
      onClick = onSnipClick,
    )
    ActionPill(
      icon = PosterIcons.Inventory,
      label = stringResource(id = R.string.inventory_title),
      container = null,
      content = palette.content,
      onClick = onInventoryClick,
      onLongClick = onInventoryLongClick,
    )
  }
}

/** Filled in [container] when there is one, otherwise outlined in [content]. */
@Composable
private fun ActionPill(
  icon: ImageVector,
  label: String,
  container: Color?,
  content: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .height(40.dp)
      .clip(CircleShape)
      .then(
        if (container != null) {
          Modifier.background(container)
        } else {
          Modifier.border(width = 1.5.dp, color = content, shape = CircleShape)
        },
      )
      .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
      .padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = label.uppercase(),
      style = CrawlType.pill,
      color = content,
      maxLines = 1,
    )
  }
}
