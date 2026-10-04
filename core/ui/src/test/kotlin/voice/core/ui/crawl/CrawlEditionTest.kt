package voice.core.ui.crawl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrawlEditionTest {

  @Test
  fun findsEveryBookByItsNumberedLibraryName() {
    val names = listOf(
      "1. Dungeon Crawler Carl",
      "2. Carl's Doomsday Scenario",
      "3. The Dungeon Anarchist's Cookbook",
      "4. The Gate of the Feral Gods",
      "5. The Butcher's Masquerade",
      "6. The Eye of the Bedlam Bride",
      "7. This Inevitable Ruin",
      "8. A Parade of Horribles",
    )
    assertEquals(CrawlEdition.entries.toList(), names.map(::crawlEditionOf))
    assertEquals((1..8).toList(), CrawlEdition.entries.map { it.number })
  }

  @Test
  fun aBooksOwnTitleWinsOverTheSeriesName() {
    assertEquals(CrawlEdition.CarlsDoomsdayScenario, crawlEditionOf("Carl's Doomsday Scenario: Dungeon Crawler Carl, Book 2"))
    assertEquals(CrawlEdition.GateOfTheFeralGods, crawlEditionOf("Dungeon Crawler Carl 4: The Gate of the Feral Gods"))
  }

  @Test
  fun matchesTypographicApostrophesAndAnyCase() {
    assertEquals(CrawlEdition.ButchersMasquerade, crawlEditionOf("5. The Butcher’s Masquerade"))
    assertEquals(CrawlEdition.ThisInevitableRuin, crawlEditionOf("this inevitable ruin"))
  }

  @Test
  fun theSeriesNameAloneFallsBackToTheBookNumber() {
    assertEquals(CrawlEdition.DungeonCrawlerCarl, crawlEditionOf("Dungeon Crawler Carl"))
    assertEquals(CrawlEdition.DungeonAnarchistsCookbook, crawlEditionOf("Dungeon Crawler Carl, Book 3"))
    assertNull(crawlEditionOf("9. Dungeon Crawler Carl"))
  }

  @Test
  fun otherBooksHaveNoEdition() {
    assertNull(crawlEditionOf("1. Project Hail Mary"))
    assertNull(crawlEditionOf("Das Ende der Welt"))
  }

  @Test
  fun systemBarIconsFollowTheTextColor() {
    val lightIcons = CrawlEdition.entries.filterNot { it.palette.darkSystemBarIcons }
    assertEquals(
      listOf(CrawlEdition.CarlsDoomsdayScenario, CrawlEdition.GateOfTheFeralGods, CrawlEdition.EyeOfTheBedlamBride),
      lightIcons,
    )
  }

  @Test
  fun theColorSchemeUsesThePalette() {
    val palette = CrawlEdition.DungeonCrawlerCarl.palette
    val scheme = palette.colorScheme()
    assertEquals(palette.background, scheme.surface)
    assertEquals(palette.content, scheme.onSurface)
    assertEquals(palette.highlight, scheme.primary)
    assertEquals(palette.accent, scheme.secondary)
    assertTrue(palette.darkSystemBarIcons)
    assertFalse(CrawlEdition.GateOfTheFeralGods.palette.darkSystemBarIcons)
  }
}
