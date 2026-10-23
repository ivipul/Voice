package voice.core.ui.crawl

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import voice.core.companion.BookLook
import voice.core.companion.LookSource
import voice.core.companion.StoredPalette

/**
 * How a book's screens look: its colors and how its chapter names are set. Every book starts in the app's periwinkle and
 * navy. A generated cover replaces the book's own cover file, so it needs nothing here.
 */
@Immutable
data class BookStyle(
  val palette: CrawlPalette = DefaultPalette,
  val chapterType: CrawlDisplayType = DefaultChapterType,
)

/** The app's own colors, from its icon: periwinkle paper, navy ink and a bold indigo. */
val DefaultPalette = CrawlPalette(
  background = Color(0xFFE4E5FC),
  content = Color(0xFF0B0D45),
  highlight = Color(0xFF3238C2),
  onHighlight = Color(0xFFF4F5FF),
  accent = Color(0xFF0B0D45),
  onAccent = Color(0xFFF4F5FF),
  shadow = Color(0xFFABAEEC),
)

val DefaultChapterType = CrawlDisplayType.Default

/** The display faces a book's chapter names can be set in; the research step picks one that suits the book. */
val ChapterTypes: Map<String, CrawlDisplayType> = mapOf(
  "anton" to DefaultChapterType,
  "serif" to CrawlDisplayType(CrawlFonts.InstrumentSerif, FontWeight.Normal, 46.sp),
  "condensed" to CrawlDisplayType(CrawlFonts.BigShouldersDisplay, FontWeight.SemiBold, 44.sp, uppercase = true),
  "rounded" to CrawlDisplayType(CrawlFonts.BagelFatOne, FontWeight.Normal, 40.sp),
  "classic" to CrawlDisplayType(CrawlFonts.PlayfairDisplay, FontWeight.Bold, 40.sp),
)

/** The style a book's stored look describes. */
fun BookLook.toStyle(): BookStyle = BookStyle(
  palette = palette?.takeIf { source != LookSource.Default }?.toCrawlPalette() ?: DefaultPalette,
  chapterType = ChapterTypes[displayFont] ?: DefaultChapterType,
)

fun StoredPalette.toCrawlPalette(): CrawlPalette = CrawlPalette(
  background = Color(background),
  content = Color(content),
  highlight = Color(highlight),
  onHighlight = Color(onHighlight),
  accent = Color(accent),
  onAccent = Color(onAccent),
  shadow = Color(shadow),
)

fun CrawlPalette.toStored(): StoredPalette = StoredPalette(
  background = background.toArgb(),
  content = content.toArgb(),
  highlight = highlight.toArgb(),
  onHighlight = onHighlight.toArgb(),
  accent = accent.toArgb(),
  onAccent = onAccent.toArgb(),
  shadow = shadow.toArgb(),
)
