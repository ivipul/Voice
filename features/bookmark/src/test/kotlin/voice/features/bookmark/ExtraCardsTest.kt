package voice.features.bookmark

import voice.core.companion.Extra
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtraCardsTest {

  @Test
  fun extrasAreOrderedByWhereTheyUnlock() {
    val cards = listOf(
      extra(id = "late", unlockAtMs = 7_200_000L),
      extra(id = "always", unlockAtMs = 0L),
      extra(id = "mid", unlockAtMs = 600_000L),
    ).extraCards(bookPositionMs = 0L)

    assertEquals(listOf("always", "mid", "late"), cards.map { it.id })
  }

  @Test
  fun anExtraAheadOfThePositionIsLockedWithWhereItUnlocks() {
    val cards = listOf(
      extra(id = "reached", unlockAtMs = 60_000L),
      extra(id = "ahead", unlockAtMs = 3_723_000L),
    ).extraCards(bookPositionMs = 120_000L)

    assertFalse(cards.first { it.id == "reached" }.locked)
    val ahead = cards.first { it.id == "ahead" }
    assertTrue(ahead.locked)
    assertEquals("1:02:03", ahead.unlocksAt)
  }

  private fun extra(
    id: String,
    unlockAtMs: Long,
  ) = Extra(
    id = id,
    title = "Title $id",
    description = "About $id",
    kind = "explainer",
    html = "<p>$id</p>",
    createdAtMs = 0L,
    unlockAtMs = unlockAtMs,
  )
}
