package voice.features.playbackScreen.view

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnipFrameLoaderTest {

  @Test
  fun `the reveal plays for an image that arrives after the loader appeared`() {
    assertTrue(shouldPlayReveal(imagePathOnFirstComposition = null))
  }

  @Test
  fun `an image that is already there is shown without replaying the reveal`() {
    assertFalse(shouldPlayReveal(imagePathOnFirstComposition = "/frames/m1.png"))
  }

  @Test
  fun `the scale pop dips to 0_96 mid-reveal and returns to full size at both ends`() {
    assertEquals(1f, revealScale(0f), 0.0001f)
    assertEquals(0.96f, revealScale(0.5f), 0.0001f)
    assertEquals(1f, revealScale(1f), 0.0001f)
  }
}
