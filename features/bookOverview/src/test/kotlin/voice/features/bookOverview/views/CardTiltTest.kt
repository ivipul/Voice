package voice.features.bookOverview.views

import kotlin.test.Test
import kotlin.test.assertEquals

class CardTiltTest {

  @Test
  fun evenASlowScrollDownLeansTheCardsForward() {
    assertEquals(7.5F, cardTilt(speedPx = -1F))
  }

  @Test
  fun scrollingUpLeansTheCardsBack() {
    assertEquals(-12F, cardTilt(speedPx = 10F))
  }

  @Test
  fun aFastFlingLeansThemNoFurtherThan14Degrees() {
    assertEquals(14F, cardTilt(speedPx = -400F))
    assertEquals(-14F, cardTilt(speedPx = 400F))
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
