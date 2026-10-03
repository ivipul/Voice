package voice.features.playbackScreen.view.holocard

import kotlin.math.exp

internal data class TiltTarget(
  val rotationX: Float,
  val rotationY: Float,
)

/**
 * Turns the direction of "up" as the phone sees it into a card tilt. The pose the phone is first held in is the
 * neutral one and it slowly re-centers, so changing how you hold the phone does not leave the card stuck at an angle.
 * The card behaves like an object that stays level while the phone turns around it.
 */
internal class DeviceTiltTracker {

  private var baseX = 0f
  private var baseY = 0f
  private var baseRotation = -1

  /**
   * [upX] and [upY] are the device-frame x and y components of the world's up vector (`R[6]` and `R[7]` of the
   * rotation matrix), [displayRotation] is `Surface.ROTATION_*`.
   */
  fun update(
    upX: Float,
    upY: Float,
    displayRotation: Int,
    deltaSeconds: Float,
  ): TiltTarget {
    val (x, y) = toScreenFrame(upX, upY, displayRotation)
    if (displayRotation != baseRotation) {
      baseRotation = displayRotation
      baseX = x
      baseY = y
    }
    val dx = x - baseX
    val dy = y - baseY
    val recenter = 1f - exp(-deltaSeconds / RecenterSeconds)
    baseX += dx * recenter
    baseY += dy * recenter
    return TiltTarget(
      rotationX = (dy * GainX).coerceIn(-TiltRangeX / 2f, TiltRangeX / 2f),
      rotationY = (dx * GainY).coerceIn(-TiltRangeY / 2f, TiltRangeY / 2f),
    )
  }

  private companion object {
    const val RecenterSeconds = 10f

    /** Degrees of card tilt per unit of change in the up vector, about full tilt at 18 degrees of phone tilt. */
    const val GainX = 36f
    const val GainY = 45f
  }
}

/** Maps device-frame x/y to the screen's x (right) / y (up) for a `Surface.ROTATION_*` value. */
internal fun toScreenFrame(
  x: Float,
  y: Float,
  displayRotation: Int,
): Pair<Float, Float> = when (displayRotation) {
  1 -> y to -x
  2 -> -x to -y
  3 -> -y to x
  else -> x to y
}
