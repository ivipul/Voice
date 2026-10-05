package voice.features.playbackScreen.deck

import voice.core.xray.XRayEntityCue
import voice.core.xray.XRayEntityInfo
import voice.core.xray.XRayManifest
import voice.core.xray.card.PlayerCardData
import voice.core.xray.card.PlayerCardEntry
import voice.core.xray.card.PlayerCardFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeckBuilderTest {

  private fun manifest(
    id: String,
    title: String,
    cueStartMs: Long,
  ) = XRayManifest(
    entities = listOf(XRayEntityInfo(id = id, title = title, description = "$title in this book", image = "img/$id.png")),
    timeline = listOf(XRayEntityCue(entity = id, startMs = cueStartMs, endMs = cueStartMs + 10_000)),
  )

  private fun card(
    id: String,
    lookAtMs: Long = 0,
  ) = PlayerCardData(
    id = id,
    entries = listOf(
      PlayerCardEntry(ms = lookAtMs, field = PlayerCardFields.LOOK, image = "look/$id.png"),
      PlayerCardEntry(ms = lookAtMs, field = PlayerCardFields.LEVEL, value = "1"),
    ),
  )

  private fun book(
    number: Int,
    positionMs: Long,
    manifest: XRayManifest?,
    vararg cards: PlayerCardData,
  ) = DeckBook(key = "book$number", number = number, positionMs = positionMs, manifest = manifest, cards = cards.toList())

  @Test
  fun `a crawler the listener has not reached yet is a locked slot that only names the book`() {
    val deck = buildDeck(listOf(book(1, positionMs = 5_000, manifest("carl", "Carl", cueStartMs = 60_000), card("carl"))))

    val slot = assertIs<DeckSlot.Locked>(deck.single())
    assertEquals(1, slot.firstBook)
  }

  @Test
  fun `a crawler is unlocked once the position reaches their first cue`() {
    val deck = buildDeck(listOf(book(1, positionMs = 60_000, manifest("carl", "Carl", cueStartMs = 60_000), card("carl"))))

    val slot = assertIs<DeckSlot.Unlocked>(deck.single())
    assertEquals("Carl", slot.name)
    assertEquals(60_000, slot.positionMs)
    assertEquals("img/carl.png", slot.portrait)
  }

  @Test
  fun `a card whose first look comes after the cue stays locked until the look`() {
    val locked = buildDeck(
      listOf(book(1, positionMs = 90_000, manifest("zev", "Zev", cueStartMs = 60_000), card("zev", lookAtMs = 120_000))),
    )
    val unlocked = buildDeck(
      listOf(book(1, positionMs = 120_000, manifest("zev", "Zev", cueStartMs = 60_000), card("zev", lookAtMs = 120_000))),
    )

    assertIs<DeckSlot.Locked>(locked.single())
    assertIs<DeckSlot.Unlocked>(unlocked.single())
  }

  @Test
  fun `a book that was never started unlocks nothing even for a crawler at its very start`() {
    val deck = buildDeck(listOf(book(2, positionMs = 0, manifest("carl", "Carl", cueStartMs = 0), card("carl"))))

    assertIs<DeckSlot.Locked>(deck.single())
  }

  @Test
  fun `a crawler in two books shows the card of the latest book the listener reached`() {
    val deck = buildDeck(
      listOf(
        book(1, positionMs = 900_000, manifest("carl", "Carl", cueStartMs = 1_000), card("carl")),
        book(2, positionMs = 30_000, manifest("carl", "Carl, later", cueStartMs = 5_000), card("carl")),
      ),
    )

    val slot = assertIs<DeckSlot.Unlocked>(deck.single())
    assertEquals("book2", slot.bookKey)
    assertEquals(30_000, slot.positionMs)
    assertEquals("Carl, later in this book", slot.description)
  }

  @Test
  fun `a crawler whose later book is not reached yet keeps the earlier card`() {
    val deck = buildDeck(
      listOf(
        book(1, positionMs = 900_000, manifest("carl", "Carl", cueStartMs = 1_000), card("carl")),
        book(2, positionMs = 0, manifest("carl", "Carl", cueStartMs = 1_000), card("carl")),
      ),
    )

    assertEquals("book1", assertIs<DeckSlot.Unlocked>(deck.single()).bookKey)
  }

  @Test
  fun `slots are ordered by the book a crawler first shows up in and then by when`() {
    val deck = buildDeck(
      listOf(
        book(2, positionMs = 10_000, manifest("mordecai", "Mordecai", 1_000), card("mordecai")),
        DeckBook(
          key = "book1",
          number = 1,
          positionMs = 1_000_000,
          manifest = XRayManifest(
            entities = emptyList(),
            timeline = listOf(
              XRayEntityCue("donut", startMs = 500_000, endMs = 510_000),
              XRayEntityCue("carl", startMs = 1_000, endMs = 2_000),
            ),
          ),
          cards = listOf(card("donut"), card("carl")),
        ),
      ),
    )

    assertEquals(listOf("carl", "donut", "mordecai"), deck.map(DeckSlot::id))
    assertTrue(deck.all { it is DeckSlot.Unlocked })
  }

  @Test
  fun `a crawler without X-Ray info is named after the card id`() {
    val deck = buildDeck(listOf(book(1, positionMs = 10_000, manifest = null, card("the_maestro"))))

    assertEquals("The maestro", assertIs<DeckSlot.Unlocked>(deck.single()).name)
  }
}
