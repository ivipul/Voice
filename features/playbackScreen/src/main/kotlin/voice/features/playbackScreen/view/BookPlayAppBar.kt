package voice.features.playbackScreen.view

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.icons.VoiceIcons

/** Only a way back and the overflow menu: the cover art carries the title. */
@Composable
internal fun BookPlayAppBar(
  skipSilence: Boolean,
  onCloseClick: () -> Unit,
  onFeedClick: () -> Unit,
  onSleepTimerClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
) {
  val iconColor = LocalContentColor.current
  TopAppBar(
    title = {},
    navigationIcon = {
      IconButton(onClick = onCloseClick) {
        Icon(
          imageVector = VoiceIcons.ArrowBack,
          contentDescription = stringResource(id = R.string.common_action_close),
        )
      }
    },
    actions = {
      OverflowMenu(
        skipSilence = skipSilence,
        onFeedClick = onFeedClick,
        onSleepTimerClick = onSleepTimerClick,
        onSkipSilenceClick = onSkipSilenceClick,
        onVolumeBoostClick = onVolumeBoostClick,
      )
    },
    expandedHeight = 48.dp,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color.Transparent,
      scrolledContainerColor = Color.Transparent,
      navigationIconContentColor = iconColor,
      actionIconContentColor = iconColor,
    ),
  )
}
