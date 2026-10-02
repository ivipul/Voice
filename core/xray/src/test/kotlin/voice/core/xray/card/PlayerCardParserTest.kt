package voice.core.xray.card

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerCardParserTest {

  @Test
  fun parsesCardsAndIgnoresUnknownKeys() {
    val manifest = PlayerCardParser.parse(
      """
      {
        "version": 1,
        "cards": [
          {
            "id": "carl",
            "crawler_id": "4,122",
            "future_key": true,
            "entries": [
              {"ms": 1000, "field": "LEVEL", "value": "13"},
              {"ms": 2000, "field": "STAT_STR", "value": "9", "modifier": "+3"},
              {"ms": 3000, "field": "GEAR", "key": "Head", "value": "Crown"},
              {"ms": 4000, "field": "LOOK", "image": "carl/look-001.webp", "note": "Crocs"}
            ]
          },
          {"id": "mongo", "entries": []}
        ]
      }
      """.trimIndent(),
    )

    assertEquals(listOf("carl", "mongo"), manifest.cards.map { it.id })
    val carl = manifest.cards.first()
    assertEquals("4,122", carl.crawlerId)
    assertEquals(
      listOf(
        PlayerCardEntry(ms = 1000, field = "LEVEL", value = "13"),
        PlayerCardEntry(ms = 2000, field = "STAT_STR", value = "9", modifier = "+3"),
        PlayerCardEntry(ms = 3000, field = "GEAR", key = "Head", value = "Crown"),
        PlayerCardEntry(ms = 4000, field = "LOOK", image = "carl/look-001.webp", note = "Crocs"),
      ),
      carl.entries,
    )
    assertEquals(null, manifest.cards.last().crawlerId)
  }
}
