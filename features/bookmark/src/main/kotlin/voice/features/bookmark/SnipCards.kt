package voice.features.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import voice.core.ui.FRAME_ASPECT
import voice.core.ui.crawl.CrawlFonts
import voice.core.ui.crawl.CrawlType
import java.io.File
import voice.core.strings.R as StringsR

private val CardShape = RoundedCornerShape(20.dp)
private val FrameShape = RoundedCornerShape(12.dp)
private val FrameWidth = 96.dp
private val SnipTitle = TextStyle(
  fontFamily = CrawlFonts.InstrumentSans,
  fontWeight = FontWeight.Bold,
  fontSize = 17.sp,
  lineHeight = 22.sp,
)

/** The book's snips as cards, newest first, or a hint on how to make one. */
@Composable
internal fun SnipCardList(
  snips: List<SnipCardViewState>,
  onClick: (SnipCardViewState) -> Unit,
  onImageClick: (SnipCardViewState) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (snips.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(id = StringsR.string.inventory_snips_empty),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
      )
    }
  } else {
    LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(items = snips, key = { it.id }) { snip ->
        SnipCard(
          snip = snip,
          onClick = { onClick(snip) },
          onImageClick = { onImageClick(snip) },
          modifier = Modifier.animateItem(),
        )
      }
    }
  }
}

/**
 * The snip's comic frame on the left, when it has one, and its place in the book, title and text on the right.
 * A tap on the frame opens it full screen; a tap anywhere else plays on from the snip.
 */
@Composable
private fun SnipCard(
  snip: SnipCardViewState,
  onClick: () -> Unit,
  onImageClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(CardShape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onClick)
      .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    when {
      snip.imagePath != null -> AsyncImage(
        model = File(snip.imagePath),
        contentDescription = stringResource(id = StringsR.string.inventory_snip_frame),
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .width(FrameWidth)
          .aspectRatio(FRAME_ASPECT)
          .clip(FrameShape)
          .clickable(onClick = onImageClick),
      )
      snip.drawingImage -> Box(
        modifier = Modifier
          .width(FrameWidth)
          .aspectRatio(FRAME_ASPECT)
          .clip(FrameShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      }
    }
    Column(
      modifier = Modifier.weight(1F),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      snip.location?.let { location ->
        Text(
          text = location.uppercase(),
          style = CrawlType.label,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      snip.title?.let { title ->
        Text(
          text = title,
          style = SnipTitle,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Text(
        text = snip.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
