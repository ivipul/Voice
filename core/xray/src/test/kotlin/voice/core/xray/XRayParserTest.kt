package voice.core.xray

import org.junit.Assert.assertEquals
import org.junit.Test

class XRayParserTest {

  @Test
  fun `parses entities and timeline`() {
    val json = """
      {
        "entities": [
          { "id": "carl", "title": "Carl", "description": "A crawler.", "image": "https://example.com/carl.jpg" },
          { "id": "donut", "title": "Princess Donut", "description": "A cat." }
        ],
        "timeline": [
          { "entity": "carl", "start_ms": 0, "end_ms": 1000 },
          { "entity": "donut", "start_ms": 500, "end_ms": 1500 }
        ]
      }
    """.trimIndent()

    val manifest = XRayParser.parse(json)

    assertEquals(2, manifest.entities.size)
    assertEquals("Carl", manifest.entities[0].title)
    assertEquals("https://example.com/carl.jpg", manifest.entities[0].image)
    assertEquals(null, manifest.entities[1].image)
    assertEquals(2, manifest.timeline.size)
    assertEquals(XRayEntityCue(entity = "carl", startMs = 0, endMs = 1000), manifest.timeline[0])
  }
}
