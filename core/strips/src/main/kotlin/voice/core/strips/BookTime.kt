package voice.core.strips

import voice.core.data.Chapter
import voice.core.data.ChapterId

fun List<Chapter>.bookPositionOf(
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

fun List<Chapter>.chapterPositionOf(bookMs: Long): Pair<ChapterId, Long>? {
  if (bookMs < 0) return null
  var offset = 0L
  for (chapter in this) {
    if (bookMs < offset + chapter.duration) return chapter.id to bookMs - offset
    offset += chapter.duration
  }
  return null
}
