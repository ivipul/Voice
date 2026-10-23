package voice.features.extras

import androidx.compose.ui.graphics.toArgb
import voice.core.ui.crawl.DefaultPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtraThemeTest {

  @Test
  fun pagesGetTheBookColors() {
    val theme = DefaultPalette.toExtraTheme(displayFont = "anton")

    assertEquals(DefaultPalette.background.toArgb(), theme.background)
    assertEquals(DefaultPalette.content.toArgb(), theme.ink)
    assertEquals(DefaultPalette.highlight.toArgb(), theme.accent)
    assertEquals(DefaultPalette.onHighlight.toArgb(), theme.accentInk)
    assertTrue(theme.css().contains("--bg:#E4E5FC"))
  }

  @Test
  fun everyDisplayFaceMapsToAPlainFontList() {
    listOf("anton", "serif", "condensed", "rounded", "classic", "unknown").forEach { key ->
      assertFalse(cssDisplayFont(key).contains(';'), key)
    }
  }
}
