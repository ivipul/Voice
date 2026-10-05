package voice.features.bookmark

import voice.core.copilot.CoPilotMessage
import voice.core.copilot.snipTextOf
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.formatted
import voice.core.data.snipLocation
import voice.core.strips.AvailableStrip
import voice.core.ui.crawl.CrawlEdition

data class BookmarkViewState(
  /** The book's snips, newest first. */
  val snips: List<SnipCardViewState> = emptyList(),
  /** The strips reached so far, in chapter order. */
  val strips: List<AvailableStrip> = emptyList(),
  /** The strips still ahead, which show only as locked slots: no picture and no title. */
  val lockedStrips: List<AvailableStrip> = emptyList(),
  /** The series book this is, whose colors the screen takes; null for any other book. */
  val edition: CrawlEdition? = null,
)

/** A snip as a card: its comic frame (or the frame still being drawn), title, text and where in the book it was taken. */
data class SnipCardViewState(
  val id: String,
  val title: String?,
  val text: String,
  /** "Chapter 12 : 03:41", or null when the snip's file is no longer part of the book. */
  val location: String?,
  val imagePath: String?,
  val drawingImage: Boolean,
  val chapterId: ChapterId,
  val positionInChapterMs: Long,
)

internal fun List<CoPilotMessage>.snipCards(chapters: List<Chapter>): List<SnipCardViewState> = this
  .filter { it.isSnip }
  .sortedByDescending { it.timestampMs }
  .mapNotNull { message ->
    val chapterId = message.snipChapterId ?: return@mapNotNull null
    val positionInChapterMs = message.snipPositionInChapterMs ?: return@mapNotNull null
    val snip = snipTextOf(message.text)
    SnipCardViewState(
      id = message.id,
      title = snip.title,
      text = snip.text,
      location = chapters.snipLocation(chapterId, positionInChapterMs)?.formatted(),
      imagePath = message.imagePath,
      drawingImage = message.isGeneratingImage,
      chapterId = chapterId,
      positionInChapterMs = positionInChapterMs,
    )
  }
