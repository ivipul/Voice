package voice.features.bookmark

import voice.core.copilot.CoPilotMessage
import voice.core.copilot.snipTextOf
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.formatted
import voice.core.data.snipLocation
import voice.core.companion.Extra
import voice.core.ui.crawl.BookStyle
import voice.core.ui.formatTime

data class BookmarkViewState(
  /** The book's snips, newest first. */
  val snips: List<SnipCardViewState> = emptyList(),
  /** The book's shelf of Extras, in the order they unlock. */
  val extras: List<ExtraCardViewState> = emptyList(),
  /** The book's colors and type. */
  val bookStyle: BookStyle? = null,
)

/** An Extra on the shelf. A locked one is still ahead in the book and shows only when it unlocks, nothing more. */
data class ExtraCardViewState(
  val id: String,
  val title: String,
  val description: String,
  /** What kind of page it is, like "calculator" or "explainer", as a tag. */
  val kind: String,
  /** Where in the book it unlocks, like "2:14:05", while it is still locked; null once it is unlocked. */
  val unlocksAt: String?,
) {
  val locked: Boolean get() = unlocksAt != null
}

internal fun List<Extra>.extraCards(bookPositionMs: Long): List<ExtraCardViewState> = this
  .sortedWith(compareBy<Extra> { it.unlockAtMs }.thenBy { it.createdAtMs })
  .map { extra ->
    ExtraCardViewState(
      id = extra.id,
      title = extra.title,
      description = extra.description,
      kind = extra.kind,
      unlocksAt = formatTime(extra.unlockAtMs).takeIf { extra.unlockAtMs > bookPositionMs },
    )
  }

/** A snip as a card: its picture (or the picture still being drawn), title, text and where in the book it was taken. */
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
