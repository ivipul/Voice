package voice.core.copilot.frame

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FramePackTest {

  private val externalDir: File = createTempDirectory("ext").toFile()
  private val context = mockk<Context> { every { getExternalFilesDir(null) } returns externalDir }
  private val pack = FramePack(context)
  private val root = File(externalDir, "frame-pack")

  private fun write(path: String, text: String = "x") {
    val file = File(root, path)
    checkNotNull(file.parentFile).mkdirs()
    file.writeText(text)
  }

  private fun writeLooks() = write(
    "looks.json",
    """
    {"book_title": "Dungeon Crawler Carl", "characters": [
      {"name": "Carl", "slug": "carl", "ignored": 1, "looks": [
        {"seq": 1, "first_ms": 93800, "description": "boxers", "standing": "images/carl/look-001-standing.png", "action": "images/carl/look-001-action.png"},
        {"seq": 2, "first_ms": 6436624, "description": "cloak", "standing": "images/carl/look-002-standing.png", "action": null}
      ]},
      {"name": "Zev", "slug": "zev", "looks": [{"seq": 1, "first_ms": 0, "standing": null, "action": null}]}
    ]}
    """.trimIndent(),
  )

  @Test
  fun `without style images the pack is not available`() {
    assertFalse(pack.isAvailable())

    write("style/style-1.png")

    assertTrue(pack.isAvailable())
  }

  @Test
  fun `at most three style images are used, in name order`() {
    listOf("style-4.png", "style-2.jpeg", "style-1.png", "style-3.jpg", "notes.txt").forEach { write("style/$it") }

    assertEquals(listOf("style-1.png", "style-2.jpeg", "style-3.jpg"), pack.load("Any").styleImages.map { it.name })
  }

  @Test
  fun `looks resolve to the images that exist on disk`() {
    writeLooks()
    write("images/carl/look-001-standing.png")
    write("images/carl/look-002-standing.png")

    val carl = pack.load("Dungeon Crawler Carl").characters.first { it.slug == "carl" }

    assertEquals(listOf(1, 2), carl.looks.map { it.seq })
    assertEquals(null, carl.looks[0].action)
    assertEquals("look-001-standing.png", carl.looks[0].standing?.name)
    assertEquals(listOf("look-002-standing.png"), carl.looks[1].references.map { it.name })
    assertNull(pack.load("Dungeon Crawler Carl").characters.first { it.slug == "zev" }.lookAt(1_000))
  }

  @Test
  fun `looks apply to book 1 by either of its titles but not to another book`() {
    writeLooks()

    assertEquals(2, pack.load("Dungeon Crawler Carl").characters.size)
    assertEquals(2, pack.load("Dungeon Crawler Carl - Dungeon Crawler Carl, Book 1").characters.size)
    assertTrue(pack.load("Carl's Doomsday Scenario - Dungeon Crawler Carl, Book 2").characters.isEmpty())
  }

  @Test
  fun `a missing or broken looks file just means no known characters`() {
    assertTrue(pack.load("Dungeon Crawler Carl").characters.isEmpty())

    write("looks.json", "not json")

    assertTrue(pack.load("Dungeon Crawler Carl").characters.isEmpty())
  }
}
