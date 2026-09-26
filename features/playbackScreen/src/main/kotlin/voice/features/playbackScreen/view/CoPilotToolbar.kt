package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.icons.VoiceIcons

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
  ) {
    RoundIconButton(
      icon = VoiceIcons.History,
      contentDescription = stringResource(id = R.string.copilot_action_catch_me_up),
      onClick = onCatchMeUpClick,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PillButton(
        icon = VoiceIcons.Mic,
        label = stringResource(id = R.string.copilot_action_ask),
        onClick = onAskClick,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
      )
      PillButton(
        icon = VoiceIcons.CollectionsBookmark,
        label = stringResource(id = R.string.copilot_action_snip),
        onClick = onSnipClick,
        colors = ButtonDefaults.filledTonalButtonColors(),
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
    modifier = Modifier.size(53.dp),
  ) {
    Icon(imageVector = icon, contentDescription = contentDescription)
  }
}

@Composable
private fun PillButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  colors: ButtonColors,
) {
  FilledTonalButton(
    onClick = onClick,
    colors = colors,
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.size(6.dp))
    Text(text = label)
  }
}
