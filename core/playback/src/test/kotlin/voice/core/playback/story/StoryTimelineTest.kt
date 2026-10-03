package voice.core.playback.story

import voice.core.data.Chapter
import voice.core.data.ChapterId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryTimelineTest {

  private val chapters = listOf(file("a", 2_000L), file("b", 20_000L))

  @Test
  fun `book time inside the first file`() {
    assertEquals(0 to 1_500L, chapters.fileLocationOf(1_500L))
  }

  @Test
  fun `book time after the first file lands in the second at the right offset`() {
    assertEquals(1 to 1_000L, chapters.fileLocationOf(3_000L))
  }

  @Test
  fun `the boundary belongs to the next file`() {
    assertEquals(1 to 0L, chapters.fileLocationOf(2_000L))
  }

  @Test
  fun `before the start or past the end there is no location`() {
    assertNull(chapters.fileLocationOf(-1L))
    assertNull(chapters.fileLocationOf(22_000L))
  }

  @Test
  fun `book time of a position is the earlier files plus the position`() {
    assertEquals(1_500L, chapters.bookMsOf(0, 1_500L))
    assertEquals(3_000L, chapters.bookMsOf(1, 1_000L))
  }
}

private fun file(
  name: String,
  durationMs: Long,
) = Chapter(
  id = ChapterId(name),
  name = name,
  duration = durationMs,
  fileLastModified = Instant.EPOCH,
  fileSize = 0,
  markData = emptyList(),
)
