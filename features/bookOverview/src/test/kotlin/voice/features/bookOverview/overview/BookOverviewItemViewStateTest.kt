package voice.features.bookOverview.overview

import voice.core.companion.BookLook
import voice.core.companion.LookSource
import voice.core.companion.StoredPalette
import voice.core.ui.crawl.toStyle
import voice.features.bookOverview.book
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookOverviewItemViewStateTest {

  @Test
  fun aBookCarriesItsLookForTheColorsAndTypeOfItsCard() {
    val look = BookLook(
      source = LookSource.Cover,
      palette = StoredPalette(0xFFE7EEF2.toInt(), 0xFF101820.toInt(), 0xFF2F5D8A.toInt(), -1, 0xFF101820.toInt(), -1, 0xFFB4C4CF.toInt()),
      displayFont = "serif",
    )

    assertEquals(look.toStyle(), book(name = "Project Hail Mary").toItemViewState(look).bookStyle)
  }

  @Test
  fun aBookWhoseLookHasNotLoadedHasNoStyleYet() {
    assertNull(book(name = "Project Hail Mary").toItemViewState().bookStyle)
  }
}
