package voice.features.bookOverview.views

import kotlin.test.Test
import kotlin.test.assertEquals

class CardTiltTest {

  @Test
  fun scrollingDownLeansTheCardsForward() {
    assertEquals(20F, cardTilt(scrolledPx = -20F))
  }

  @Test
  fun scrollingUpLeansTheCardsBack() {
    assertEquals(-20F, cardTilt(scrolledPx = 20F))
  }

  @Test
  fun aFastFlingLeansThemNoFurtherThan24Degrees() {
    assertEquals(24F, cardTilt(scrolledPx = -400F))
    assertEquals(-24F, cardTilt(scrolledPx = 400F))
  }

  @Test
  fun aStillListStandsTheCardsUpright() {
    assertEquals(0F, cardTilt(scrolledPx = 0F))
  }
}
