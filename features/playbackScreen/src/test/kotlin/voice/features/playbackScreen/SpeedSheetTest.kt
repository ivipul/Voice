package voice.features.playbackScreen

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedSheetTest {

  @Test
  fun labelsShowOneDecimalUnlessTheSpeedNeedsTwo() {
    assertEquals("1.0", speedLabel(1F, Locale.US))
    assertEquals("0.8", speedLabel(0.8F, Locale.US))
    assertEquals("1.2", speedLabel(1.2F, Locale.US))
    assertEquals("1.25", speedLabel(1.25F, Locale.US))
    assertEquals("2.0", speedLabel(2F, Locale.US))
  }

  @Test
  fun labelsUseTheLocalesDecimalSeparator() {
    assertEquals("1,5", speedLabel(1.5F, Locale.GERMANY))
  }

  @Test
  fun sliderValuesSnapToTenths() {
    assertEquals(1.2F, snapSpeed(1.2000000476F))
    assertEquals(1.3F, snapSpeed(1.26F))
    assertEquals(0.5F, snapSpeed(0.5F))
    assertEquals(3F, snapSpeed(2.9999998F))
  }
}
