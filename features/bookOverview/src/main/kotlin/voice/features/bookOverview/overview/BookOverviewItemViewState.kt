package voice.features.bookOverview.overview

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.logging.api.Logger
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.crawlEditionOf
import voice.core.ui.formatTime

@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: String?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
  /** The Crawl's own cover art for a series book, shown instead of [cover]. */
  @DrawableRes val artwork: Int? = null,
  /** The series book this is, whose colors and display type its library card takes; null for any other book. */
  val edition: CrawlEdition? = null,
)

internal fun Book.toItemViewState(): BookOverviewItemViewState {
  val edition = crawlEditionOf(content.name)
  return BookOverviewItemViewState(
    name = content.name,
    author = content.author,
    cover = content.coverUrl,
    id = id,
    progress = progress(),
    remainingTime = formatTime(duration - position),
    artwork = edition?.cover,
    edition = edition,
  )
}

private fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Couldn't determine progress for book=$this")
  }
  return progress.coerceIn(0F, 1F)
}
