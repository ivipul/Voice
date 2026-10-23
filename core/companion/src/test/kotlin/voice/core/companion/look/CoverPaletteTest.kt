package voice.core.companion.look

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverPaletteTest {

  /** A [size] square cover in [edge], with [blocks] of other colors filling rows from the middle down. */
  private fun cover(edge: Int, vararg blocks: Pair<Int, Int>, size: Int = 40): IntArray {
    val pixels = IntArray(size * size) { edge }
    var row = size / 4
    blocks.forEach { (color, rows) ->
      repeat(rows) {
        for (x in size / 4 until size * 3 / 4) pixels[row * size + x] = color
        row++
      }
    }
    return pixels
  }

  @Test
  fun theBackgroundIsTheColorAroundTheCoversEdges() {
    val yellow = 0xFFFDD562.toInt()
    val blue = 0xFF194BA8.toInt()
    val brown = 0xFF271B16.toInt()

    val palette = paletteFromPixels(cover(yellow, blue to 10, brown to 6), width = 40)

    assertEquals(yellow, palette.background)
    assertEquals(brown, palette.content)
    assertEquals(blue, palette.highlight)
  }

  @Test
  fun aDarkCoverKeepsItsDarkBackgroundWithReadableText() {
    val slate = 0xFF405C57.toInt()
    val coral = 0xFFFC765B.toInt()
    val bone = 0xFFE8EBEA.toInt()

    val palette = paletteFromPixels(cover(slate, coral to 12, bone to 4), width = 40)

    assertEquals(slate, palette.background)
    assertEquals(coral, palette.highlight)
    assertTrue(contrast(palette.content, palette.background) >= 4.5, "text should read on the background")
    assertTrue(contrast(palette.onHighlight, palette.highlight) >= 4.5, "the play icon should read on the highlight")
  }

  @Test
  fun aWarmColorStandsOutOnCreamOfTheSameHue() {
    val cream = 0xFFFDECDA.toInt()
    val orange = 0xFFDD8844.toInt()
    val ink = 0xFF323032.toInt()

    val palette = paletteFromPixels(cover(cream, orange to 10, ink to 4), width = 40)

    assertEquals(orange, palette.highlight)
    assertEquals(ink, palette.content)
  }

  @Test
  fun anEmptyCoverFallsBackToTheAppColors() {
    assertEquals(0xFFE4E5FC.toInt(), paletteFromPixels(IntArray(0), width = 0).background)
  }
}
