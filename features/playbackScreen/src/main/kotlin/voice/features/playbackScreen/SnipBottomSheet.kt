package voice.features.playbackScreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.features.playbackScreen.view.FRAME_ASPECT
import voice.features.playbackScreen.view.holocard.RainbowColors
import java.io.File
import voice.core.strings.R as StringsR

@Composable
internal fun SnipBottomSheet(
  state: SnipSheetViewState,
  frame: SnipFrameViewState,
  onDismiss: () -> Unit,
) {
  val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 2 / 3
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
    content = {
      Column(
        modifier = Modifier
          .heightIn(max = maxHeight)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp)
          .padding(bottom = 32.dp),
      ) {
        Text(
          text = stringResource(StringsR.string.copilot_snip_sheet_title),
          style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.size(16.dp))
        if (frame != SnipFrameViewState.None) {
          SnipFrameSlot(imagePath = (frame as? SnipFrameViewState.Image)?.path)
          Spacer(modifier = Modifier.size(16.dp))
        }
        when (state) {
          is SnipSheetViewState.Loading -> {
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text(
                text = stringResource(StringsR.string.copilot_snip_sheet_loading),
                style = MaterialTheme.typography.bodyLarge,
              )
            }
          }
          is SnipSheetViewState.Ready -> {
            Text(
              text = state.text,
              style = MaterialTheme.typography.bodyLarge,
            )
          }
        }
      }
    },
  )
}

/**
 * Holds the room for the comic frame. Until the image is there it pulses with a sweeping foil of
 * the holo-card colors; once it has loaded the image fades in over it and the foil fades out.
 */
@Composable
private fun SnipFrameSlot(imagePath: String?) {
  var loaded by remember(imagePath) { mutableStateOf(false) }
  val imageAlpha by animateFloatAsState(
    targetValue = if (loaded) 1f else 0f,
    animationSpec = tween(durationMillis = FRAME_FADE_MS),
    label = "snip-frame-image-alpha",
  )
  val transition = rememberInfiniteTransition(label = "snip-frame-holo")
  val sweep by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
    label = "snip-frame-holo-sweep",
  )
  val pulse by transition.animateFloat(
    initialValue = 0.45f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 1100), RepeatMode.Reverse),
    label = "snip-frame-holo-pulse",
  )
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(FRAME_ASPECT)
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer { alpha = pulse * (1f - imageAlpha) }
        .drawBehind {
          val width = size.width
          drawRect(
            Brush.linearGradient(
              colors = RainbowColors + RainbowColors.first(),
              start = Offset(sweep * width - width, 0f),
              end = Offset(sweep * width, size.height * 0.6f),
              tileMode = TileMode.Repeated,
            ),
          )
        },
    )
    if (imagePath != null) {
      AsyncImage(
        model = File(imagePath),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { loaded = true },
        modifier = Modifier
          .matchParentSize()
          .graphicsLayer { alpha = imageAlpha },
      )
    }
    Text(
      text = stringResource(StringsR.string.copilot_feed_frame_drawing),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.graphicsLayer { alpha = 1f - imageAlpha },
    )
  }
}

private const val FRAME_FADE_MS = 650
