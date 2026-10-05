package voice.features.bookOverview.views

import kotlin.test.Test
import kotlin.test.assertEquals

class CardTiltTest {

  @Test
  fun evenASlowScrollDownLeansTheCardsForward() {
    assertEquals(10.5F, cardTilt(speedPx = -1F))
  }

  @Test
  fun scrollingUpLeansTheCardsBack() {
    assertEquals(-15F, cardTilt(speedPx = 10F))
  }

  @Test
  fun aFastFlingLeansThemNoFurtherThan20Degrees() {
    assertEquals(20F, cardTilt(speedPx = -400F))
    assertEquals(-20F, cardTilt(speedPx = 400F))
  }

  @Test
  fun aStillListStandsTheCardsUpright() {
    assertEquals(0F, cardTilt(speedPx = 0F))
    assertEquals(0F, cardTilt(speedPx = -0.2F))
  }

  @Test
  fun eachFrameMovesTheSpeedAFifthOfTheWay() {
    assertEquals(4F, smoothedScrollSpeed(previous = 0F, scrolledPx = 20F))
    assertEquals(-8F, smoothedScrollSpeed(previous = -10F, scrolledPx = 0F))
  }
}
