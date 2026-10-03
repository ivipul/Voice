package voice.features.playbackScreen.view.holocard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HoloTiltStateTest {

  @Test
  fun `finger position maps to tilt and light position`() {
    val tilt = HoloTiltState(reducedMotion = true)

    tilt.onPointer(x = 1f, y = 0f)
    tilt.step(timeSeconds = 0f, deltaSeconds = 0.016f)

    assertEquals(expected = TiltRangeY / 2f, actual = tilt.rotationY, absoluteTolerance = 0.001f)
    assertEquals(expected = TiltRangeX / 2f, actual = tilt.rotationX, absoluteTolerance = 0.001f)
    assertEquals(expected = 1f, actual = tilt.pointerX, absoluteTolerance = 0.001f)
    assertEquals(expected = 0f, actual = tilt.pointerY, absoluteTolerance = 0.001f)
    assertEquals(expected = 0.5f, actual = tilt.offsetX, absoluteTolerance = 0.001f)
  }

  @Test
  fun `finger positions outside the card are clamped`() {
    val tilt = HoloTiltState(reducedMotion = true)

    tilt.onPointer(x = 5f, y = -3f)
    tilt.step(timeSeconds = 0f, deltaSeconds = 0.016f)

    assertEquals(expected = TiltRangeY / 2f, actual = tilt.rotationY, absoluteTolerance = 0.001f)
    assertEquals(expected = TiltRangeX / 2f, actual = tilt.rotationX, absoluteTolerance = 0.001f)
  }

  @Test
  fun `card sways on its own when idle`() {
    val tilt = HoloTiltState(reducedMotion = false)

    repeat(60) { tilt.step(timeSeconds = 1f + it * 0.016f, deltaSeconds = 0.016f) }

    assertNotEquals(illegal = 0f, actual = tilt.rotationY)
    assertNotEquals(illegal = 0f, actual = tilt.rotationX)
    assertTrue(tilt.strength > 0f)
  }

  @Test
  fun `reduced motion never sways and snaps to the finger`() {
    val tilt = HoloTiltState(reducedMotion = true)

    repeat(60) { tilt.step(timeSeconds = 1f + it * 0.016f, deltaSeconds = 0.016f) }
    assertEquals(expected = 0f, actual = tilt.rotationY)
    assertEquals(expected = 0f, actual = tilt.rotationX)

    tilt.onPointer(x = 0f, y = 1f)
    tilt.step(timeSeconds = 2f, deltaSeconds = 0.016f)
    assertEquals(expected = -TiltRangeY / 2f, actual = tilt.rotationY, absoluteTolerance = 0.001f)

    tilt.onRelease()
    tilt.step(timeSeconds = 3f, deltaSeconds = 0.016f)
    assertEquals(expected = 0f, actual = tilt.rotationY)
    assertEquals(expected = 0.5f, actual = tilt.pointerX)
  }

  @Test
  fun `tilt eases towards the finger instead of jumping`() {
    val tilt = HoloTiltState(reducedMotion = false)

    tilt.onPointer(x = 1f, y = 0.5f)
    tilt.step(timeSeconds = 0f, deltaSeconds = 0.016f)
    val afterOneFrame = tilt.rotationY
    repeat(120) { tilt.step(timeSeconds = 0f, deltaSeconds = 0.016f) }

    assertTrue(afterOneFrame > 0f && afterOneFrame < TiltRangeY / 2f)
    assertEquals(expected = TiltRangeY / 2f, actual = tilt.rotationY, absoluteTolerance = 0.01f)
  }

  @Test
  fun `strength stays within zero and one`() {
    val tilt = HoloTiltState(reducedMotion = true)

    tilt.onPointer(x = 1f, y = 0f)
    tilt.step(timeSeconds = 0f, deltaSeconds = 0.016f)

    assertTrue(tilt.strength in 0f..1f)
  }
}
