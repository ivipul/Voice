package voice.core.playback.story

import voice.core.data.Chapter

/** Where [bookMs] falls in the book's files: the file's index and the position inside it, or null past the end. */
internal fun List<Chapter>.fileLocationOf(bookMs: Long): Pair<Int, Long>? {
  if (bookMs < 0) return null
  var offset = 0L
  forEachIndexed { index, chapter ->
    if (bookMs < offset + chapter.duration) return index to bookMs - offset
    offset += chapter.duration
  }
  return null
}

/** The book time of a position inside the file at [index]. */
internal fun List<Chapter>.bookMsOf(index: Int, positionInFileMs: Long): Long =
  take(index).sumOf { it.duration } + positionInFileMs
