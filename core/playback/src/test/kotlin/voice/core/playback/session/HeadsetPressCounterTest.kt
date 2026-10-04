package voice.core.playback.session

import kotlin.test.Test
import kotlin.test.assertEquals

class HeadsetPressCounterTest {

  private val counter = HeadsetPressCounter(windowMs = 1_500)

  @Test
  fun `presses inside the window keep counting`() {
    assertEquals(1, counter.onPress(NEXT, 0))
    assertEquals(2, counter.onPress(NEXT, 1_000))
    assertEquals(3, counter.onPress(NEXT, 2_400))
  }

  @Test
  fun `a gap longer than the window starts over`() {
    assertEquals(1, counter.onPress(NEXT, 0))
    assertEquals(1, counter.onPress(NEXT, 1_501))
  }

  @Test
  fun `a different key starts over`() {
    assertEquals(1, counter.onPress(NEXT, 0))
    assertEquals(1, counter.onPress(PREVIOUS, 100))
    assertEquals(2, counter.onPress(PREVIOUS, 200))
  }

  @Test
  fun `reset makes the next press the first of a new sequence`() {
    assertEquals(1, counter.onPress(NEXT, 0))
    assertEquals(2, counter.onPress(NEXT, 100))
    counter.reset()
    assertEquals(1, counter.onPress(NEXT, 200))
  }

  private companion object {
    const val NEXT = 87
    const val PREVIOUS = 88
  }
}
