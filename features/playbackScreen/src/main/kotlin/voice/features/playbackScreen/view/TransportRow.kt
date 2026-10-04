package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.hardShadow
import voice.core.ui.playButtonSharedBoundsModifier
import voice.core.ui.rememberPlayIconPainter
import voice.features.playbackScreen.speedLabel

/** Smart Recap, back, play, forward and the playback speed, which opens the speed sheet. */
@Composable
internal fun TransportRow(
  playing: Boolean,
  playbackSpeed: Float,
  skipBackSeconds: Int,
  skipForwardSeconds: Int,
  palette: CrawlPalette,
  onSmartRecapClick: () -> Unit,
  onRewindClick: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onPlayClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSpeedClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onSmartRecapClick) {
      Icon(
        imageVector = PosterIcons.SmartRecap,
        contentDescription = stringResource(id = R.string.copilot_action_smart_recap),
        tint = palette.content,
        modifier = Modifier.size(26.dp),
      )
    }
    SkipButton(forward = false, seconds = skipBackSeconds, onClick = onRewindClick, onLongClick = onSkipToPrevious)
    PosterPlayButton(playing = playing, palette = palette, onClick = onPlayClick)
    SkipButton(forward = true, seconds = skipForwardSeconds, onClick = onFastForwardClick, onLongClick = onSkipToNext)
    SpeedButton(speed = playbackSpeed, palette = palette, onClick = onSpeedClick)
  }
}

/** The title-colored disc with a hard shadow, in the comic style of the holo cards. */
@Composable
private fun PosterPlayButton(
  playing: Boolean,
  palette: CrawlPalette,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(64.dp)
      .playButtonSharedBoundsModifier()
      .hardShadow(color = palette.shadow, shape = CircleShape, x = 3.dp, y = 3.dp)
      .clip(CircleShape)
      .background(palette.highlight)
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = rememberPlayIconPainter(playing = playing),
      contentDescription = stringResource(
        id = if (playing) R.string.playback_action_pause else R.string.playback_action_play,
      ),
      tint = palette.onHighlight,
      modifier = Modifier.size(28.dp),
    )
  }
}

@Composable
private fun SpeedButton(
  speed: Float,
  palette: CrawlPalette,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClickLabel = stringResource(id = R.string.playback_speed_title), onClick = onClick)
      .padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(id = R.string.playback_speed_value, speedLabel(speed)),
      style = CrawlType.speed,
      color = palette.content,
    )
  }
}
