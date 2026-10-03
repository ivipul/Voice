package voice.core.strips

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StripManifestTest {

  private val lenientJson = Json { ignoreUnknownKeys = true }

  private val json = """
    {
      "schema_version": 1, "book": 1, "book_title_prefix": "Dungeon Crawler Carl",
      "chapter": 1, "title": "The vanishing", "summary": "s", "hero_frames": [],
      "last_frame_hold_ms": 20000, "unknown_field": true,
      "frames": [
        {"frame": 1, "image": "f1-final.png", "start_ms": 510000},
        {"frame": 2, "image": "f2-final.png", "start_ms": 530000}
      ]
    }
  """.trimIndent()

  @Test
  fun parsesSnakeCaseManifestIgnoringUnknownKeys() {
    val manifest = lenientJson.decodeFromString<StripManifest>(json)
    assertEquals(1, manifest.schemaVersion)
    assertEquals(20_000L, manifest.lastFrameHoldMs)
    assertEquals(listOf(510_000L, 530_000L), manifest.frames.map { it.startMs })
    assertEquals("f2-final.png", manifest.frames[1].image)
  }

  @Test
  fun heroFallsBackToFirstFrameWhenNoHeroListed() {
    assertEquals(1, manifest(starts = listOf(0L, 5L), holdMs = 1L).heroFrame().frame)
  }

  @Test
  fun heroUsesFirstListedHeroFrame() {
    assertEquals(2, manifest(starts = listOf(0L, 5L), holdMs = 1L, heroFrames = listOf(2)).heroFrame().frame)
  }

  @Test
  fun matchesOnlyTheConfiguredBookTitle() {
    val strip = manifest(starts = listOf(0L), holdMs = 1L, book = 1)
    assertTrue(strip.matchesBook("Dungeon Crawler Carl - Dungeon Crawler Carl, Book 1"))
    assertFalse(strip.matchesBook("Dungeon Crawler Carl - Dungeon Crawler Carl, Book 2"))
    assertFalse(strip.matchesBook("Carl's Doomsday Scenario - Dungeon Crawler Carl, Book 2"))
  }
}
