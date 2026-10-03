package voice.core.strips

import voice.core.data.Chapter
import voice.core.data.ChapterId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookTimeTest {

  private val first = chapter("first", durationMs = 1_000L)
  private val second = chapter("second", durationMs = 2_000L)
  private val chapters = listOf(first, second)

  @Test
  fun chapterPositionToBookPosition() {
    assertEquals(0L, chapters.bookPositionOf(first.id, 0L))
    assertEquals(1_500L, chapters.bookPositionOf(second.id, 500L))
  }

  @Test
  fun unknownChapterHasNoBookPosition() {
    assertNull(chapters.bookPositionOf(ChapterId("missing"), 0L))
  }

  @Test
  fun bookPositionToChapterPosition() {
    assertEquals(first.id to 0L, chapters.chapterPositionOf(0L))
    assertEquals(first.id to 999L, chapters.chapterPositionOf(999L))
    assertEquals(second.id to 0L, chapters.chapterPositionOf(1_000L))
    assertEquals(second.id to 1_500L, chapters.chapterPositionOf(2_500L))
  }

  @Test
  fun bookPositionOutsideBookHasNoChapter() {
    assertNull(chapters.chapterPositionOf(-1L))
    assertNull(chapters.chapterPositionOf(3_000L))
  }

  @Test
  fun roundTripKeepsPosition() {
    val (id, positionInChapter) = chapters.chapterPositionOf(2_345L)!!
    assertEquals(2_345L, chapters.bookPositionOf(id, positionInChapter))
  }
}

private fun chapter(
  name: String,
  durationMs: Long,
): Chapter = Chapter(
  id = ChapterId(name),
  name = name,
  duration = durationMs,
  fileLastModified = Instant.EPOCH,
  fileSize = 0,
  markData = emptyList(),
)
