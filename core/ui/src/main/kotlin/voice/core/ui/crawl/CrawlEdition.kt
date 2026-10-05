package voice.core.ui.crawl

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import voice.core.data.leadingBookNumber
import voice.core.ui.R

/** A book of the Dungeon Crawler Carl series, with the cover art, colors and display type The Crawl gives it. */
enum class CrawlEdition(
  val number: Int,
  val title: String,
  @DrawableRes val cover: Int,
  val palette: CrawlPalette,
  val chapterType: CrawlDisplayType,
) {
  DungeonCrawlerCarl(
    number = 1,
    title = "Dungeon Crawler Carl",
    cover = R.drawable.crawl_cover_book1,
    palette = CrawlPalette(
      background = Color(0xFFFDD463),
      content = Color(0xFF2B1F18),
      highlight = Color(0xFF2753A4),
      onHighlight = Color(0xFFFFF4D6),
      accent = Color(0xFFC44C1C),
      onAccent = Color(0xFFFFF4D6),
      shadow = Color(0xFFC44C1C),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.InstrumentSerif, FontWeight.Normal, 46.sp),
  ),
  CarlsDoomsdayScenario(
    number = 2,
    title = "Carl's Doomsday Scenario",
    cover = R.drawable.crawl_cover_book2,
    palette = CrawlPalette(
      background = Color(0xFF3F5B57),
      content = Color(0xFFFBEBDD),
      highlight = Color(0xFFEB745D),
      onHighlight = Color(0xFF1B2927),
      accent = Color(0xFFF19F89),
      onAccent = Color(0xFF14201F),
      shadow = Color(0xFF26302F),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.BigShouldersDisplay, FontWeight.SemiBold, 44.sp, uppercase = true),
  ),
  DungeonAnarchistsCookbook(
    number = 3,
    title = "The Dungeon Anarchist's Cookbook",
    cover = R.drawable.crawl_cover_book3,
    palette = CrawlPalette(
      background = Color(0xFFFC6C44),
      content = Color(0xFF2E2427),
      highlight = Color(0xFFF4F1C2),
      onHighlight = Color(0xFF2E2427),
      accent = Color(0xFF2E2427),
      onAccent = Color(0xFFF4F1C2),
      shadow = Color(0xFFB85238),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.BagelFatOne, FontWeight.Normal, 40.sp),
  ),
  GateOfTheFeralGods(
    number = 4,
    title = "The Gate of the Feral Gods",
    cover = R.drawable.crawl_cover_book4,
    palette = CrawlPalette(
      background = Color(0xFF4F2567),
      content = Color(0xFFFBEFD9),
      highlight = Color(0xFFF7BD26),
      onHighlight = Color(0xFF301D3E),
      accent = Color(0xFFFF7FB0),
      onAccent = Color(0xFF301D3E),
      shadow = Color(0xFF301D3E),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.Anton, FontWeight.Normal, 44.sp, uppercase = true),
  ),
  ButchersMasquerade(
    number = 5,
    title = "The Butcher's Masquerade",
    cover = R.drawable.crawl_cover_book5,
    palette = CrawlPalette(
      background = Color(0xFFFA83A7),
      content = Color(0xFF3A0F1E),
      highlight = Color(0xFF6A1230),
      onHighlight = Color(0xFFFDE3EC),
      accent = Color(0xFF4F7A3E),
      onAccent = Color(0xFFFDE3EC),
      shadow = Color(0xFFD85D8A),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.PlayfairDisplay, FontWeight.ExtraBold, 40.sp),
  ),
  EyeOfTheBedlamBride(
    number = 6,
    title = "The Eye of the Bedlam Bride",
    cover = R.drawable.crawl_cover_book6,
    palette = CrawlPalette(
      background = Color(0xFF354CB7),
      content = Color(0xFFF6E6C6),
      highlight = Color(0xFFF6E6C6),
      onHighlight = Color(0xFF253271),
      accent = Color(0xFFF39A3D),
      onAccent = Color(0xFF253271),
      shadow = Color(0xFF253271),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.PlayfairDisplay, FontWeight.Bold, 40.sp),
  ),
  ThisInevitableRuin(
    number = 7,
    title = "This Inevitable Ruin",
    cover = R.drawable.crawl_cover_book7,
    palette = CrawlPalette(
      background = Color(0xFFFDECDB),
      content = Color(0xFF2E3F3D),
      highlight = Color(0xFFDE8945),
      onHighlight = Color(0xFFFFF6EA),
      accent = Color(0xFF3F5A58),
      onAccent = Color(0xFFFFF6EA),
      shadow = Color(0xFFB8642E),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.BagelFatOne, FontWeight.Normal, 40.sp),
  ),
  AParadeOfHorribles(
    number = 8,
    title = "A Parade of Horribles",
    cover = R.drawable.crawl_cover_book8,
    palette = CrawlPalette(
      background = Color(0xFFEDE4CA),
      content = Color(0xFF17171C),
      highlight = Color(0xFFD62828),
      onHighlight = Color(0xFFFFF4EE),
      accent = Color(0xFF17171C),
      onAccent = Color(0xFFFFF4EE),
      shadow = Color(0xFFC2AB98),
    ),
    chapterType = CrawlDisplayType(CrawlFonts.Anton, FontWeight.Normal, 44.sp, uppercase = true),
  ),
}

private val BOOK_IN_NAME = Regex("""\bBook (\d+)\b""", RegexOption.IGNORE_CASE)

/**
 * The series book a library entry is, from its name, e.g. "2. Carl's Doomsday Scenario" or
 * "The Gate of the Feral Gods: Dungeon Crawler Carl, Book 4"; null for any other book.
 * A book's own title wins, so only a name that just says "Dungeon Crawler Carl" falls back to its number.
 */
fun crawlEditionOf(bookName: String): CrawlEdition? {
  val name = bookName.replace('’', '\'')
  CrawlEdition.entries
    .firstOrNull { it != CrawlEdition.DungeonCrawlerCarl && name.contains(it.title, ignoreCase = true) }
    ?.let { return it }
  if (!name.contains(CrawlEdition.DungeonCrawlerCarl.title, ignoreCase = true)) return null
  val number = leadingBookNumber(name)
    ?: BOOK_IN_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull()
    ?: CrawlEdition.DungeonCrawlerCarl.number
  return CrawlEdition.entries.firstOrNull { it.number == number }
}
