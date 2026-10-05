package voice.core.ui.crawl

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import voice.core.ui.R

/** The typefaces of The Crawl's poster player, all from Google Fonts under the SIL Open Font License. */
object CrawlFonts {
  val Anton = FontFamily(Font(R.font.crawl_anton, FontWeight.Normal))
  val BagelFatOne = FontFamily(Font(R.font.crawl_bagel_fat_one, FontWeight.Normal))
  val BigShouldersDisplay = FontFamily(Font(R.font.crawl_big_shoulders_display_semibold, FontWeight.SemiBold))
  val ChakraPetch = FontFamily(Font(R.font.crawl_chakra_petch_bold, FontWeight.Bold))
  val DmMono = FontFamily(Font(R.font.crawl_dm_mono_medium, FontWeight.Medium))
  val InstrumentSans = FontFamily(
    Font(R.font.crawl_instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.crawl_instrument_sans_bold, FontWeight.Bold),
  )
  val InstrumentSerif = FontFamily(Font(R.font.crawl_instrument_serif, FontWeight.Normal))
  val PlayfairDisplay = FontFamily(
    Font(R.font.crawl_playfair_display_bold, FontWeight.Bold),
    Font(R.font.crawl_playfair_display_extrabold, FontWeight.ExtraBold),
  )
}

/** The small type around the poster: game-system labels in Chakra Petch, times in DM Mono. */
object CrawlType {
  val chip = TextStyle(
    fontFamily = CrawlFonts.ChakraPetch,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = 0.08.em,
  )
  val pill = TextStyle(
    fontFamily = CrawlFonts.ChakraPetch,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    letterSpacing = 0.08.em,
  )

  /** Ask AI and Snip, and the Inventory card under them. */
  val button = TextStyle(
    fontFamily = CrawlFonts.ChakraPetch,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    letterSpacing = 0.06.em,
  )
  val label = TextStyle(
    fontFamily = CrawlFonts.ChakraPetch,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 0.14.em,
  )
  val speed = TextStyle(
    fontFamily = CrawlFonts.ChakraPetch,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    fontFeatureSettings = "tnum",
  )
  val time = TextStyle(
    fontFamily = CrawlFonts.DmMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    fontFeatureSettings = "tnum",
  )
  val digits = TextStyle(
    fontFamily = CrawlFonts.InstrumentSans,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
  )
  val badge = TextStyle(
    fontFamily = CrawlFonts.InstrumentSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    fontFeatureSettings = "tnum",
  )
}
