package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import voice.core.companion.BookLook
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.logging.api.Logger
import voice.core.ui.crawl.BookStyle
import voice.core.ui.crawl.toStyle
import voice.core.ui.formatTime

@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: String?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
  /** The book's colors and display type, which its library card takes; null until its look is loaded. */
  val bookStyle: BookStyle? = null,
)

internal fun Book.toItemViewState(look: BookLook? = null): BookOverviewItemViewState = BookOverviewItemViewState(
  name = content.name,
  author = content.author,
  cover = content.coverUrl,
  id = id,
  progress = progress(),
  remainingTime = formatTime(duration - position),
  bookStyle = look?.toStyle(),
)

private fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Couldn't determine progress for book=$this")
  }
  return progress.coerceIn(0F, 1F)
}
