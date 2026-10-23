package voice.core.companion.look

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverPaletteTest {

  @Test
  fun aDarkCoverStillGetsALightBackground() {
    val navy = 0xFF0B1E3F.toInt()
    val gold = 0xFFE0A526.toInt()
    val pixels = IntArray(1000) { if (it < 850) navy else gold }

    val palette = paletteFromPixels(pixels)

    assertTrue(lightness(palette.background) > 0.75, "background should be light")
    assertTrue(contrast(palette.content, palette.background) >= 7.0, "text should read on the background")
    assertTrue(saturation(palette.highlight) > 0.5, "highlight should be the vivid gold")
  }

  @Test
  fun aVividColorBecomesTheHighlight() {
    val cream = 0xFFF2EAD8.toInt()
    val red = 0xFFD62828.toInt()
    val black = 0xFF111111.toInt()
    val pixels = IntArray(1000) { i -> if (i < 700) cream else if (i < 900) red else black }

    val palette = paletteFromPixels(pixels)

    assertEquals(red, palette.highlight)
    assertTrue(contrast(palette.onHighlight, palette.highlight) >= 3.0)
  }

  @Test
  fun anEmptyCoverFallsBackToTheAppColors() {
    assertEquals(0xFFE4E5FC.toInt(), paletteFromPixels(IntArray(0)).background)
  }
}
