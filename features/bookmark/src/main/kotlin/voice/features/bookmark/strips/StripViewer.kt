package voice.features.bookmark.strips

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.strips.AvailableStrip
import voice.core.ui.rememberPlayIconPainter
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

@Composable
fun StripCard(
  strip: AvailableStrip,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    AsyncImage(
      model = strip.coverFile(),
      contentDescription = strip.manifest.title,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(strip.coverAspect ?: DEFAULT_COVER_ASPECT),
    )
    Text(
      text = strip.manifest.title,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
  }
}

/** A two-column staggered gallery of the strips, each card at its own image's aspect ratio. */
@Composable
fun StripCardList(
  strips: List<AvailableStrip>,
  onClick: (AvailableStrip) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Fixed(GALLERY_COLUMNS),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalItemSpacing = 8.dp,
  ) {
    items(strips, key = { it.manifest.chapter }) { strip ->
      StripCard(strip = strip, onClick = { onClick(strip) })
    }
  }
}

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
private const val GALLERY_COLUMNS = 2
private const val DEFAULT_COVER_ASPECT = 9f / 16f
