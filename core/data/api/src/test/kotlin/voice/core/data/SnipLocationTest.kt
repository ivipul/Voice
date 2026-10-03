package voice.core.data

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SnipLocationTest {

  private val m4b = file(
    "Opening Credits" to 0.minutes,
    "Chapter 1" to 1.minutes,
    "Chapter 2" to 31.minutes,
    "Chapter 3: The Goblin" to 61.minutes,
    "Closing Credits" to 301.minutes,
  )

  @Test
  fun `number comes from the title, not the list index, when credits come first`() {
    val location = listOf(m4b).snipLocation(m4b.id, (61.minutes + 3.minutes + 41.seconds).inWholeMilliseconds)

    assertEquals(SnipLocation(3, null, (3.minutes + 41.seconds).inWholeMilliseconds), location)
    assertEquals("Chapter 3 : 03:41", location?.formatted())
  }

  @Test
  fun `a title without a number is shown instead of a number`() {
    val location = listOf(m4b).snipLocation(m4b.id, 30.seconds.inWholeMilliseconds)

    assertEquals(SnipLocation(null, "Opening Credits", 30.seconds.inWholeMilliseconds), location)
    assertEquals("Opening Credits : 00:30", location?.formatted())
  }

  @Test
  fun `bare and leading numbers are parsed`() {
    val chapters = file("12" to 0.minutes, "13. The Stairs" to 10.minutes, "14 - Down" to 20.minutes)

    assertEquals(12, listOf(chapters).snipLocation(chapters.id, 1.minutes.inWholeMilliseconds)?.chapterNumber)
    assertEquals(13, listOf(chapters).snipLocation(chapters.id, 11.minutes.inWholeMilliseconds)?.chapterNumber)
    assertEquals(14, listOf(chapters).snipLocation(chapters.id, 21.minutes.inWholeMilliseconds)?.chapterNumber)
  }

  @Test
  fun `untitled chapters fall back to their position in the whole book`() {
    val first = file("" to 0.minutes, "" to 10.minutes)
    val second = file("" to 0.minutes, "" to 10.minutes)

    val location = listOf(first, second).snipLocation(second.id, 12.minutes.inWholeMilliseconds)

    assertEquals(SnipLocation(4, null, 2.minutes.inWholeMilliseconds), location)
  }

  @Test
  fun `time over an hour into the chapter includes hours`() {
    val long = file("Chapter 7" to 0.minutes)

    val location = listOf(long).snipLocation(long.id, (1.hours + 2.minutes + 3.seconds).inWholeMilliseconds)

    assertEquals("Chapter 7 : 1:02:03", location?.formatted())
  }

  @Test
  fun `unknown file has no location`() {
    assertNull(listOf(m4b).snipLocation(ChapterId("missing"), 0))
  }

  private fun file(vararg marks: Pair<String, kotlin.time.Duration>): Chapter = Chapter(
    id = ChapterId(marks.joinToString { it.first } + System.nanoTime()),
    name = "file",
    duration = 400.minutes.inWholeMilliseconds,
    fileLastModified = Instant.EPOCH,
    fileSize = 0,
    markData = marks.map { (name, start) -> MarkData(start.inWholeMilliseconds, name) },
  )
}
