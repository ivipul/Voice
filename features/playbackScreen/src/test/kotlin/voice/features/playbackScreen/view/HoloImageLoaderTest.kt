package voice.features.playbackScreen.view

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoloImageLoaderTest {

  @Test
  fun `the reveal plays for an image that arrives after the loader appeared`() {
    assertTrue(shouldPlayReveal(imagePathOnFirstComposition = null))
  }

  @Test
  fun `an image that is already there is shown without replaying the reveal`() {
    assertFalse(shouldPlayReveal(imagePathOnFirstComposition = "/frames/m1.png"))
  }
}
