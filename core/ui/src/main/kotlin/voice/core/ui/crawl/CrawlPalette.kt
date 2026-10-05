package voice.core.ui.crawl

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** One book's colors, taken from its cover so the screen and the cover art read as one poster. */
@Immutable
data class CrawlPalette(
  /** The screen, and the flat background of the cover that fades into it. */
  val background: Color,
  /** Text and icons. */
  val content: Color,
  /** The cover title's color: the play button, the seek bar's fill, the X-Ray chips and the chapter name. */
  val highlight: Color,
  val onHighlight: Color,
  /** Ask AI and the comic strip stretches on the seek bar. */
  val accent: Color,
  val onAccent: Color,
  /** The hard, unblurred drop shadow under the play button and sheets. */
  val shadow: Color,
) {

  /** Whether the status and navigation bar icons should be dark, matching dark text on a light background. */
  val darkSystemBarIcons: Boolean get() = content.luminance() < 0.5F
}

/** How a book sets its chapter name: the display face of its cover. */
@Immutable
data class CrawlDisplayType(
  val fontFamily: FontFamily,
  val fontWeight: FontWeight,
  val fontSize: TextUnit,
  val uppercase: Boolean = false,
) {

  companion object {
    val Default = CrawlDisplayType(CrawlFonts.InstrumentSerif, FontWeight.Normal, 40.sp)
  }
}

/** A Material color scheme in the palette's colors, so menus, dialogs and sheets on the book's screens match it. */
fun CrawlPalette.colorScheme(): ColorScheme {
  fun tone(fraction: Float): Color = lerp(background, content, fraction)
  val base = if (darkSystemBarIcons) lightColorScheme() else darkColorScheme()
  return base.copy(
    primary = highlight,
    onPrimary = onHighlight,
    primaryContainer = highlight,
    onPrimaryContainer = onHighlight,
    inversePrimary = onHighlight,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = accent,
    onSecondaryContainer = onAccent,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = accent,
    onTertiaryContainer = onAccent,
    background = background,
    onBackground = content,
    surface = background,
    onSurface = content,
    surfaceVariant = tone(0.12F),
    onSurfaceVariant = content,
    surfaceTint = highlight,
    inverseSurface = content,
    inverseOnSurface = background,
    outline = tone(0.6F),
    outlineVariant = tone(0.25F),
    surfaceBright = tone(0.04F),
    surfaceDim = tone(0.1F),
    surfaceContainerLowest = background,
    surfaceContainerLow = tone(0.04F),
    surfaceContainer = tone(0.07F),
    surfaceContainerHigh = tone(0.1F),
    surfaceContainerHighest = tone(0.14F),
  )
}
