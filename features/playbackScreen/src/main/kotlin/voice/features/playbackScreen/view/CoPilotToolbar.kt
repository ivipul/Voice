package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.icons.VoiceIcons

private val RoundButtonSize = 60.dp
private val RoundButtonIconSize = 24.dp
private val PillButtonHeight = 60.dp
private val PillOuterCornerRadius = 999.dp
private val PillInnerCornerRadius = 6.dp
private val PillButtonContainerColor = Color.White
private val PillButtonContentColor = Color(0xFF05090A)
private val PillButtonIconColor = Color(0xFF06A76F)

@Composable
internal fun CoPilotToolbar(
  onCatchMeUpClick: () -> Unit,
  onAskClick: () -> Unit,
  onSnipClick: () -> Unit,
  onFeedClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RoundIconButton(
      icon = VoiceIcons.History,
      contentDescription = stringResource(id = R.string.copilot_action_catch_me_up),
      onClick = onCatchMeUpClick,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      PillButton(
        icon = VoiceIcons.Mic,
        label = stringResource(id = R.string.copilot_action_ask),
        onClick = onAskClick,
        shape = RoundedCornerShape(
          topStart = PillOuterCornerRadius,
          bottomStart = PillOuterCornerRadius,
          topEnd = PillInnerCornerRadius,
          bottomEnd = PillInnerCornerRadius,
        ),
      )
      PillButton(
        icon = VoiceIcons.CollectionsBookmark,
        label = stringResource(id = R.string.copilot_action_snip),
        onClick = onSnipClick,
        shape = RoundedCornerShape(
          topStart = PillInnerCornerRadius,
          bottomStart = PillInnerCornerRadius,
          topEnd = PillOuterCornerRadius,
          bottomEnd = PillOuterCornerRadius,
        ),
      )
    }
    RoundIconButton(
      icon = VoiceIcons.Chat,
      contentDescription = stringResource(id = R.string.copilot_action_feed),
      onClick = onFeedClick,
    )
  }
}

@Composable
private fun RoundIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
) {
  FilledIconButton(
    onClick = onClick,
    shape = CircleShape,
    colors = IconButtonDefaults.filledIconButtonColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
    modifier = Modifier.size(RoundButtonSize),
  ) {
    Icon(
      modifier = Modifier.size(RoundButtonIconSize),
      imageVector = icon,
      contentDescription = contentDescription,
    )
  }
}

@Composable
private fun PillButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  shape: Shape,
) {
  FilledTonalButton(
    onClick = onClick,
    shape = shape,
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = PillButtonContainerColor,
      contentColor = PillButtonContentColor,
    ),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    modifier = Modifier.height(PillButtonHeight),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = PillButtonIconColor,
      modifier = Modifier.size(RoundButtonIconSize),
    )
    Spacer(modifier = Modifier.size(6.dp))
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
  }
}
