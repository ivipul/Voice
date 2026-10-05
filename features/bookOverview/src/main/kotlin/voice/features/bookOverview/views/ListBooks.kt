package voice.features.bookOverview.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.sharedCoverElementModifier
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.core.ui.R as UiR

@Composable
internal fun ListBookRow(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  BookCard(
    bookId = book.id,
    onBookClick = onBookClick,
    onBookLongClick = onBookLongClick,
    modifier = modifier,
  ) {
    Column(Modifier.padding()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CoverImage(book.id, book.artwork ?: book.cover)

        Column(
          Modifier
            .padding(start = 12.dp)
            .weight(1f),
        ) {
          if (book.author != null) {
            Text(
              text = book.author.toUpperCase(LocaleList.current),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }

          Text(
            text = book.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
          )

          BookRemainingProgressRow(
            modifier = Modifier
              .padding(end = 12.dp),
            remainingTime = book.remainingTime,
            progress = book.progress,
            remainingTimeMaxLines = 1,
            progressMaxLines = 1,
          )
        }
      }

      if (book.progress > 0.05f) {
        Spacer(Modifier.size(0.dp))
        BookProgressIndicator(
          progress = book.progress,
          modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .height(4.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun CoverImage(
  bookId: BookId,
  cover: Any?,
) {
  val startPadding = 16.dp
  val endPadding = 16.dp
  AsyncImage(
    modifier = Modifier
      .padding(top = 8.dp, start = 8.dp, bottom = 8.dp)
      .size(76.dp)
      .sharedCoverElementModifier(bookId)
      .clip(RoundedCornerShape(topStart = startPadding, bottomStart = startPadding, topEnd = endPadding, bottomEnd = endPadding)),
    model = cover,
    placeholder = painterResource(id = UiR.drawable.album_art),
    error = painterResource(id = UiR.drawable.album_art),
    contentScale = ContentScale.Crop,
    contentDescription = null,
  )
}

@Composable
@Preview
private fun ListBookRowPreviewWithProgress() {
  ListBookRow(BookOverviewPreviewParameterProvider().book().copy(progress = 0.6f), {}, {})
}

@Composable
@Preview
private fun ListBookRowPreviewWithoutProgress() {
  ListBookRow(BookOverviewPreviewParameterProvider().book().copy(progress = 0f), {}, {})
}
