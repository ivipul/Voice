package voice.features.bookOverview.overview

import voice.core.ui.crawl.CrawlEdition
import voice.features.bookOverview.book
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookOverviewItemViewStateTest {

  @Test
  fun aSeriesBookCarriesItsEditionForTheColorsAndTypeOfItsCard() {
    val item = book(name = "2. Carl's Doomsday Scenario").toItemViewState()

    assertEquals(CrawlEdition.CarlsDoomsdayScenario, item.edition)
    assertEquals(CrawlEdition.CarlsDoomsdayScenario.cover, item.artwork)
  }

  @Test
  fun anyOtherBookHasNoEditionAndKeepsItsOwnCover() {
    val item = book(name = "Project Hail Mary").toItemViewState()

    assertNull(item.edition)
    assertNull(item.artwork)
  }
}
