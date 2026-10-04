package voice.features.playbackScreen.view

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.sharedCoverElementModifier
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun Cover(
  bookId: BookId,
  cover: Any?,
  fadeSides: Boolean,
  onDoubleClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AsyncImage(
    modifier = modifier
      .sharedCoverElementModifier(bookId)
      .pointerInput(onDoubleClick) {
        detectTapGestures(
          onDoubleTap = {
            onDoubleClick()
          },
        )
      }
      .fadeIntoBackground(sides = fadeSides),
    contentScale = ContentScale.Crop,
    model = cover,
    placeholder = painterResource(id = UiR.drawable.album_art),
    error = painterResource(id = UiR.drawable.album_art),
    contentDescription = stringResource(id = StringsR.string.cover_title),
  )
}

/** Fades the bottom fifth, and the sides when the cover is narrower than the screen, into the matching background. */
private fun Modifier.fadeIntoBackground(sides: Boolean): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    drawRect(
      brush = Brush.verticalGradient(0.8F to Color.Black, 1F to Color.Transparent),
      blendMode = BlendMode.DstIn,
    )
    if (sides) {
      drawRect(
        brush = Brush.horizontalGradient(
          0F to Color.Transparent,
          0.08F to Color.Black,
          0.92F to Color.Black,
          1F to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
      )
    }
  }
