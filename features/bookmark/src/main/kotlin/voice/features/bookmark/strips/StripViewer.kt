package voice.features.bookmark.strips

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.ui.icons.VoiceIcons
import voice.core.ui.rememberPlayIconPainter
import voice.core.strings.R as StringsR

@Composable
fun StripViewer(
  viewModel: StripViewerViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewState = viewModel.viewState()
  DisposableEffect(viewModel) {
    onDispose { viewModel.close() }
  }
  LaunchedEffect(Unit) { viewModel.start() }
  LaunchedEffect(viewState.closed) {
    if (viewState.closed) onClose()
  }
  val strip = viewModel.strip
  val frameImage = if (viewState.frameIndex < strip.manifest.frames.size) {
    strip.imageFile(strip.manifest.frames[viewState.frameIndex])
  } else {
    strip.stripImageFile()
  }
  var dragTotal by remember { mutableFloatStateOf(0f) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          if (offset.x < size.width / 3f) {
            viewModel.onPrevious()
          } else if (!viewModel.onNext()) {
            onClose()
          }
        }
      }
      .pointerInput(Unit) {
        detectVerticalDragGestures(
          onDragStart = { dragTotal = 0f },
          onDragEnd = { if (dragTotal > SWIPE_DOWN_CLOSE_PX) onClose() },
          onVerticalDrag = { _, amount -> dragTotal += amount },
        )
      },
  ) {
    AsyncImage(
      model = frameImage,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize(),
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        viewState.progress.forEach { fraction ->
          Box(
            modifier = Modifier
              .weight(1f)
              .height(3.dp)
              .background(Color.White.copy(alpha = 0.3f)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(Color.White),
            )
          }
        }
      }
    }
    IconButton(
      onClick = viewModel::togglePlaying,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .systemBarsPadding()
        .padding(8.dp),
    ) {
      Icon(
        painter = rememberPlayIconPainter(playing = viewState.isPlaying),
        contentDescription = stringResource(
          id = if (viewState.isPlaying) StringsR.string.playback_action_pause else StringsR.string.playback_action_play,
        ),
        tint = Color.White,
      )
    }
    IconButton(
      onClick = onClose,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .systemBarsPadding()
        .padding(top = 12.dp),
    ) {
      Icon(
        imageVector = VoiceIcons.Close,
        contentDescription = stringResource(id = StringsR.string.common_action_close),
        tint = Color.White,
      )
    }
  }
}

private const val SWIPE_DOWN_CLOSE_PX = 160f
