package voice.features.playbackScreen.view.holocard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceTiltTrackerTest {

  @Test
  fun `the first pose is neutral`() {
    val target = DeviceTiltTracker().update(upX = 0.1f, upY = 0.8f, displayRotation = 0, deltaSeconds = 0f)

    assertEquals(expected = TiltTarget(0f, 0f), actual = target)
  }

  @Test
  fun `turning the right edge down tilts the card the way a finger on the left would`() {
    val tracker = DeviceTiltTracker()
    tracker.calibrate(upX = 0f, upY = 0.8f)

    val target = tracker.update(upX = -0.15f, upY = 0.8f, displayRotation = 0, deltaSeconds = 0.016f)

    assertTrue(target.rotationY < 0f)
    assertEquals(expected = 0f, actual = target.rotationX, absoluteTolerance = 0.001f)
  }

  @Test
  fun `turning the top edge away tilts the card around the horizontal axis`() {
    val tracker = DeviceTiltTracker()
    tracker.calibrate(upX = 0f, upY = 0.8f)

    val target = tracker.update(upX = 0f, upY = 0.65f, displayRotation = 0, deltaSeconds = 0.016f)

    assertTrue(target.rotationX < 0f)
    assertEquals(expected = 0f, actual = target.rotationY, absoluteTolerance = 0.001f)
  }

  @Test
  fun `tilt is clamped to the finger range`() {
    val tracker = DeviceTiltTracker()
    tracker.calibrate(upX = 0f, upY = 0f)

    val target = tracker.update(upX = 1f, upY = 1f, displayRotation = 0, deltaSeconds = 0.016f)

    assertEquals(expected = TiltRangeY / 2f, actual = target.rotationY)
    assertEquals(expected = TiltRangeX / 2f, actual = target.rotationX)
  }

  @Test
  fun `holding a pose slowly brings the card back to level`() {
    val tracker = DeviceTiltTracker()
    tracker.calibrate(upX = 0f, upY = 0f)
    val first = tracker.update(upX = 0.2f, upY = 0f, displayRotation = 0, deltaSeconds = 0.016f)

    var last = first
    repeat(600) { last = tracker.update(upX = 0.2f, upY = 0f, displayRotation = 0, deltaSeconds = 0.1f) }

    assertTrue(first.rotationY > 5f)
    assertEquals(expected = 0f, actual = last.rotationY, absoluteTolerance = 0.1f)
  }

  @Test
  fun `changing the display rotation starts from a new neutral pose`() {
    val tracker = DeviceTiltTracker()
    tracker.calibrate(upX = 0f, upY = 0.8f)

    val target = tracker.update(upX = 0.8f, upY = 0f, displayRotation = 1, deltaSeconds = 0.016f)

    assertEquals(expected = TiltTarget(0f, 0f), actual = target)
  }

  @Test
  fun `device axes map to the screen for each display rotation`() {
    assertEquals(expected = 1f to 2f, actual = toScreenFrame(1f, 2f, 0))
    assertEquals(expected = 2f to -1f, actual = toScreenFrame(1f, 2f, 1))
    assertEquals(expected = -1f to -2f, actual = toScreenFrame(1f, 2f, 2))
    assertEquals(expected = -2f to 1f, actual = toScreenFrame(1f, 2f, 3))
  }

  private fun DeviceTiltTracker.calibrate(
    upX: Float,
    upY: Float,
  ) {
    assertEquals(expected = TiltTarget(0f, 0f), actual = update(upX = upX, upY = upY, displayRotation = 0, deltaSeconds = 0f))
  }
}
