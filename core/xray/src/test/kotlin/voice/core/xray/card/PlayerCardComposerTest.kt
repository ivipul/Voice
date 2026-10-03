package voice.core.xray.card

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerCardComposerTest {

  private fun entry(
    ms: Long,
    field: String,
    key: String? = null,
    value: String? = null,
    modifier: String? = null,
    image: String? = null,
    note: String? = null,
  ) = PlayerCardEntry(ms = ms, field = field, key = key, value = value, modifier = modifier, image = image, note = note)

  private fun card(vararg entries: PlayerCardEntry) = PlayerCardData(id = "carl", crawlerId = "4,122", entries = entries.toList())

  @Test
  fun nothingIsRevealedBeforeTheFirstEntry() {
    val composed = card(entry(1_000, PlayerCardFields.LEVEL, value = "1")).composeAt(999)

    assertNull(composed.level)
    assertTrue(composed.stats.all { it.locked })
    assertTrue(composed.abilities.isEmpty())
    assertTrue(composed.loadout.isEmpty())
    assertFalse(composed.hasRevealedData)
  }

  @Test
  fun takesLatestValuePerFieldAtOrBeforePosition() {
    val data = card(
      entry(0, PlayerCardFields.LEVEL, value = "1"),
      entry(5_000, PlayerCardFields.LEVEL, value = "2"),
      entry(9_000, PlayerCardFields.LEVEL, value = "3"),
    )

    assertEquals(1, data.composeAt(4_999).level)
    assertEquals(2, data.composeAt(5_000).level)
    assertEquals(2, data.composeAt(8_999).level)
    assertEquals(3, data.composeAt(1_000_000).level)
  }

  @Test
  fun entriesOutOfOrderInTheFileAreStillComposedByTime() {
    val data = card(
      entry(9_000, PlayerCardFields.LEVEL, value = "3"),
      entry(0, PlayerCardFields.LEVEL, value = "1"),
    )

    assertEquals(1, data.composeAt(100).level)
    assertEquals(3, data.composeAt(9_000).level)
  }

  @Test
  fun statsKeepValueAndModifierAndStayLockedUntilRevealed() {
    val data = card(
      entry(1_000, PlayerCardFields.STAT_STR, value = "9", modifier = "+3"),
      entry(2_000, PlayerCardFields.STAT_STR, value = "12", modifier = "+4"),
      entry(3_000, PlayerCardFields.STAT_INT, value = "3"),
    )

    val early = data.composeAt(1_500).stats
    assertEquals(ComposedPlayerCard.Stat(CardStat.Strength, 9, "+3"), early[0])
    assertTrue(early.drop(1).all { it.locked })

    val late = data.composeAt(3_000).stats
    assertEquals(ComposedPlayerCard.Stat(CardStat.Strength, 12, "+4"), late[0])
    assertEquals(ComposedPlayerCard.Stat(CardStat.Intelligence, 3, null), late[3])
    assertEquals(listOf("STR", "DEX", "CON", "INT", "CHA"), late.map { it.stat.abbreviation })
  }

  @Test
  fun statWithoutValueStaysLockedEvenWithModifier() {
    val stat = card(entry(0, PlayerCardFields.STAT_DEX, value = null, modifier = "+1")).composeAt(10).stats[1]

    assertTrue(stat.locked)
  }

  @Test
  fun gearPersistsUntilReplacedInTheSameSlot() {
    val data = card(
      entry(1_000, PlayerCardFields.GEAR, key = "Feet", value = "Crocs"),
      entry(1_000, PlayerCardFields.GEAR, key = "Chest", value = "Jacket"),
      entry(4_000, PlayerCardFields.GEAR, key = "feet", value = "Boots"),
    )

    assertEquals(
      listOf(ComposedPlayerCard.GearItem("Feet", "Crocs"), ComposedPlayerCard.GearItem("Chest", "Jacket")),
      data.composeAt(3_999).loadout,
    )
    assertEquals(
      setOf(ComposedPlayerCard.GearItem("feet", "Boots"), ComposedPlayerCard.GearItem("Chest", "Jacket")),
      data.composeAt(100_000).loadout.toSet(),
    )
  }

  @Test
  fun clearingASlotWithANullValueEmptiesIt() {
    val data = card(
      entry(1_000, PlayerCardFields.GEAR, key = "Head", value = "Crown"),
      entry(2_000, PlayerCardFields.GEAR, key = "Head", value = null),
    )

    assertEquals(1, data.composeAt(1_500).loadout.size)
    assertTrue(data.composeAt(2_000).loadout.isEmpty())
  }

  @Test
  fun abilitiesAreSortedByRankThenRecency() {
    val data = card(
      entry(1_000, PlayerCardFields.SKILL, key = "Aiming", value = "4"),
      entry(2_000, PlayerCardFields.SKILL, key = "Explosives Handling", value = "7"),
      entry(3_000, PlayerCardFields.SKILL, key = "Swimming", value = "4"),
      entry(4_000, PlayerCardFields.SKILL, key = "aiming", value = "5"),
    )

    assertEquals(
      listOf("Explosives Handling" to 7, "aiming" to 5, "Swimming" to 4),
      data.composeAt(5_000).abilities.map { it.name to it.rank },
    )
    assertEquals(
      listOf("Explosives Handling", "Swimming", "Aiming"),
      data.composeAt(3_500).abilities.map { it.name },
    )
  }

  @Test
  fun lookIsTheLatestAtOrBeforePositionAndFallsBackToTheEarliest() {
    val data = card(
      entry(10_000, PlayerCardFields.LOOK, image = "carl/look-002.webp", note = "Barefoot"),
      entry(5_000, PlayerCardFields.LOOK, image = "carl/look-001.webp", note = "Crocs"),
    )

    assertEquals("carl/look-001.webp", data.composeAt(0).look?.image)
    assertEquals("carl/look-001.webp", data.composeAt(9_999).look?.image)
    assertEquals(ComposedPlayerCard.Look("carl/look-002.webp", "Barefoot"), data.composeAt(10_000).look)
  }

  @Test
  fun epithetPrefersClassAndFallsBackToCallSign() {
    val data = card(
      entry(0, PlayerCardFields.CALL_SIGN, value = "Royal Bodyguard"),
      entry(5_000, PlayerCardFields.CLASS, value = "Compensated Anarchist"),
    )

    data.composeAt(1_000).let {
      assertEquals("Royal Bodyguard", it.epithet)
      assertNull(it.callSign)
    }
    data.composeAt(5_000).let {
      assertEquals("Compensated Anarchist", it.epithet)
      assertEquals("Royal Bodyguard", it.callSign)
    }
  }

  @Test
  fun floorCarriesItsNameAndLevelAndRaceAreParsed() {
    val composed = card(
      entry(0, PlayerCardFields.FLOOR, value = "2", note = "Iron Tangle"),
      entry(0, PlayerCardFields.LEVEL, value = " 13 "),
      entry(0, PlayerCardFields.RACE, value = "Human"),
      entry(0, PlayerCardFields.LEVEL, value = "not a number"),
    ).composeAt(10)

    assertEquals(2, composed.floor)
    assertEquals("Iron Tangle", composed.floorName)
    assertEquals("Human", composed.race)
    assertNull(composed.level)
    assertEquals("4,122", composed.crawlerId)
  }

  @Test
  fun cardsWithOnlyLooksAndLabelsHaveNoGameStats() {
    val lookOnly = card(
      entry(0, PlayerCardFields.LOOK, image = "zev/look-001.webp", note = "A man."),
      entry(0, PlayerCardFields.RACE, value = "Human"),
      entry(0, PlayerCardFields.CALL_SIGN, value = "Guide"),
    )
    assertFalse(lookOnly.hasGameStats)
    assertTrue(card(entry(0, PlayerCardFields.GEAR, key = "head", value = "Crown")).hasGameStats)
    assertTrue(card(entry(0, PlayerCardFields.STAT_STR, value = "9")).hasGameStats)
    assertTrue(card(entry(0, PlayerCardFields.LEVEL, value = "2")).hasGameStats)
  }
}
