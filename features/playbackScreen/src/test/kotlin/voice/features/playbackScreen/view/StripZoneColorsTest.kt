package voice.features.playbackScreen.view

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StripZoneColorsTest {

  @Test
  fun `a chapter always gets the same colours`() {
    assertEquals(stripZoneColors(3), stripZoneColors(3))
  }

  @Test
  fun `neighbouring chapters get different colours`() {
    (1..30).forEach { chapter ->
      assertNotEquals(stripZoneColors(chapter), stripZoneColors(chapter + 1), "chapters $chapter and ${chapter + 1}")
    }
  }

  @Test
  fun `the palette repeats after six strips`() {
    assertEquals(stripZoneColors(1), stripZoneColors(7))
    assertEquals(6, (1..6).map { stripZoneColors(it).light }.toSet().size)
  }

  @Test
  fun `chapters outside the normal range still get a colour`() {
    assertEquals(stripZoneColors(6), stripZoneColors(0))
    assertEquals(stripZoneColors(2), stripZoneColors(-4))
  }

  @Test
  fun `each colour is a pastel that deepens a little from its light end`() {
    (1..6).forEach { chapter ->
      val colors = stripZoneColors(chapter)
      assertTrue(colors.light.luminance() > colors.deep.luminance(), "chapter $chapter")
      assertTrue(colors.light.luminance() > 0.55f, "chapter $chapter is not light enough")
    }
  }

  @Test
  fun `the first strip is a soft peach`() {
    val peach = stripZoneColors(1).light
    assertEquals(0.9325f, peach.red, 0.003f)
    assertEquals(0.828f, peach.green, 0.003f)
    assertEquals(0.7675f, peach.blue, 0.003f)
  }

  private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
