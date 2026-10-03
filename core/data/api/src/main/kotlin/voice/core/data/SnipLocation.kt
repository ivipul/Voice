package voice.core.data

import java.util.concurrent.TimeUnit

/**
 * Where a snipped moment sits in the book: the chapter it falls in and how far into that chapter.
 * Exactly one of [chapterNumber] and [chapterTitle] is set: untitled chapters get their position
 * in the book's chapter list, titled ones without a number in the title (credits, prologue)
 * show their title.
 */
public data class SnipLocation(
  val chapterNumber: Int?,
  val chapterTitle: String?,
  val offsetInChapterMs: Long,
)

/**
 * Looks up the book chapter containing [positionInChapterMs] of the file [chapterId]. A file
 * (what the data layer calls a Chapter) can hold many chapters as marks, e.g. a single M4B, so
 * the position is resolved against the marks rather than the file.
 */
public fun List<Chapter>.snipLocation(chapterId: ChapterId, positionInChapterMs: Long): SnipLocation? {
  val fileIndex = indexOfFirst { it.id == chapterId }
  if (fileIndex == -1) return null
  val file = this[fileIndex]
  val mark = file.markForPosition(positionInChapterMs)
  val offset = (positionInChapterMs - mark.startMs).coerceAtLeast(0L)
  val title = mark.name?.trim().orEmpty()
  val numberInTitle = chapterNumberInTitle(title)
  return when {
    numberInTitle != null -> SnipLocation(numberInTitle, null, offset)
    title.isEmpty() -> {
      val index = take(fileIndex).sumOf { it.chapterMarks.size } + file.chapterMarks.indexOf(mark)
      SnipLocation(index + 1, null, offset)
    }
    else -> SnipLocation(null, title, offset)
  }
}

/** "Chapter 12 : 03:41", or "Prologue : 03:41" when the chapter's title carries no number. */
public fun SnipLocation.formatted(): String {
  val chapter = chapterNumber?.let { "Chapter $it" } ?: chapterTitle.orEmpty()
  return "$chapter : ${formatOffset(offsetInChapterMs)}"
}

private val chapterWordNumber = Regex("""\bchapter\s*(\d+)""", RegexOption.IGNORE_CASE)
private val leadingNumber = Regex("""^(\d+)(?:\s*[.:)\-–—]|$)""")

private fun chapterNumberInTitle(title: String): Int? {
  val match = chapterWordNumber.find(title) ?: leadingNumber.find(title)
  return match?.groupValues?.get(1)?.toIntOrNull()
}

private fun formatOffset(timeMs: Long): String {
  val hours = TimeUnit.MILLISECONDS.toHours(timeMs)
  val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60
  val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%02d:%02d".format(minutes, seconds)
  }
}
