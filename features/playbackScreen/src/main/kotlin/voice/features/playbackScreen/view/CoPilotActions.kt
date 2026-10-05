package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.unit.sp
import voice.core.strings.R
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.icons.VoiceIcons

/** Ask system AI, filled in the accent color, and Snip as an outline, side by side at the same width. */
@Composable
internal fun CoPilotActions(
  palette: CrawlPalette,
  onAskClick: () -> Unit,
  onSnipClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ActionPill(
      icon = VoiceIcons.Mic,
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
      modifier = Modifier.weight(1F),
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
) {
  Row(
    modifier = modifier
      .height(48.dp)
      .clip(CircleShape)
      .then(
        if (container != null) {
          Modifier.background(container)
        } else {
          Modifier.border(width = 1.5.dp, color = content, shape = CircleShape)
        },
      )
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label.uppercase(),
      style = CrawlType.button,
      color = content,
      maxLines = 1,
      autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = CrawlType.button.fontSize, stepSize = 0.5.sp),
    )
  }
}
