package voice.core.companion.look

import voice.core.companion.StoredPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuratedPalettesTest {

  private fun coverColors(background: Long, highlight: Long, accent: Long) = StoredPalette(
    background = background.toInt(),
    content = 0xFF000000.toInt(),
    highlight = highlight.toInt(),
    onHighlight = 0xFF000000.toInt(),
    accent = accent.toInt(),
    onAccent = 0xFF000000.toInt(),
    shadow = 0xFF000000.toInt(),
  )

  @Test
  fun aGarishOrangeCoverSnapsToCoralAndCream() {
    val closest = closestCuratedPalette(coverColors(0xFFFF5A1F, 0xFFFFFF99, 0xFF333333))

    assertEquals("Coral & Cream", closest.name)
  }

  @Test
  fun aDarkTealCoverSnapsToDeepTealAndSalmon() {
    val closest = closestCuratedPalette(coverColors(0xFF1B4D57, 0xFFFF7F66, 0xFFFFD24D))

    assertEquals("Deep Teal & Salmon", closest.name)
  }

  @Test
  fun highlightAndAccentMatchEitherWayRound() {
    val closest = closestCuratedPalette(coverColors(0xFFF3EFE4, 0xFFC0402C, 0xFF2C50C0))

    assertEquals("Paper & Cobalt", closest.name)
  }

  @Test
  fun everyCuratedPaletteReads() {
    CURATED_PALETTES.forEach { (name, palette) ->
      assertTrue(contrast(palette.content, palette.background) >= 4.5, "$name text")
      assertTrue(contrast(palette.onHighlight, palette.highlight) >= 4.5, "$name play label")
      assertTrue(contrast(palette.onAccent, palette.accent) >= 4.5, "$name Ask label")
    }
  }

  @Test
  fun everyPaletteIsOfferedToTheCoverPlanner() {
    CURATED_PALETTES.forEach { assertTrue("- ${it.name}: ${it.inks()}" in COVER_PROMPT_SYSTEM, it.name) }
  }
}
