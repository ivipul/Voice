package voice.core.data

/** The position in the whole book of [positionInChapterMs] in the audio file [chapterId], or null if it isn't in the book. */
public fun List<Chapter>.bookPositionOf(
  chapterId: ChapterId,
  positionInChapterMs: Long,
): Long? {
  var offset = 0L
  for (chapter in this) {
    if (chapter.id == chapterId) return offset + positionInChapterMs
    offset += chapter.duration
  }
  return null
}

/** The audio file and position in it of [bookMs], a position in the whole book; null past the end. */
public fun List<Chapter>.chapterPositionOf(bookMs: Long): Pair<ChapterId, Long>? {
  if (bookMs < 0) return null
  var offset = 0L
  for (chapter in this) {
    if (bookMs < offset + chapter.duration) return chapter.id to bookMs - offset
    offset += chapter.duration
  }
  return null
}
