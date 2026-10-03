package voice.features.playbackScreen.view.holocard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

internal const val TiltRangeX = 22f
internal const val TiltRangeY = 28f

/**
 * Where the card is tilted to and where the light sits. Follows the finger while it is down, sways gently
 * otherwise. With [reducedMotion] there is no idle sway and no smoothing, the card only moves with the finger.
 */
@Stable
internal class HoloTiltState(private val reducedMotion: Boolean) {

  var rotationX by mutableFloatStateOf(0f)
    private set
  var rotationY by mutableFloatStateOf(0f)
    private set

  /** Horizontal light position, 0..1. */
  var pointerX by mutableFloatStateOf(0.5f)
    private set

  /** Vertical light position, 0..1. */
  var pointerY by mutableFloatStateOf(0.5f)
    private set

  private var targetRotationX = 0f
  private var targetRotationY = 0f
  private var targetPointerX = 0.5f
  private var targetPointerY = 0.5f
  private var touching = false

  /** Tilt around the vertical axis as -0.5..0.5, used to shift parallax layers. */
  val offsetX: Float get() = rotationY / TiltRangeY

  /** Tilt around the horizontal axis as -0.5..0.5, used to shift parallax layers. */
  val offsetY: Float get() = rotationX / TiltRangeX

  /** How far the card is tilted, 0..1, used to fade the foil in. */
  val strength: Float get() = hypot(offsetY, offsetX).coerceAtMost(1f)

  fun onPointer(
    x: Float,
    y: Float,
  ) {
    touching = true
    val nx = x.coerceIn(0f, 1f)
    val ny = y.coerceIn(0f, 1f)
    targetRotationY = (nx - 0.5f) * TiltRangeY
    targetRotationX = (0.5f - ny) * TiltRangeX
    targetPointerX = nx
    targetPointerY = ny
  }

  fun onRelease() {
    touching = false
    targetRotationX = 0f
    targetRotationY = 0f
    targetPointerX = 0.5f
    targetPointerY = 0.5f
  }

  fun step(
    timeSeconds: Float,
    deltaSeconds: Float,
  ) {
    if (!touching && !reducedMotion) {
      targetRotationY = sin(timeSeconds * 0.9f) * 9f
      targetRotationX = cos(timeSeconds * 0.7f) * 5f
      targetPointerX = 0.5f + sin(timeSeconds * 0.9f) * 0.32f
      targetPointerY = 0.5f + cos(timeSeconds * 0.7f) * 0.22f
    }
    val rotationBlend = if (reducedMotion) 1f else 1f - exp(-8f * deltaSeconds)
    val pointerBlend = if (reducedMotion) 1f else 1f - exp(-10f * deltaSeconds)
    rotationX += (targetRotationX - rotationX) * rotationBlend
    rotationY += (targetRotationY - rotationY) * rotationBlend
    pointerX += (targetPointerX - pointerX) * pointerBlend
    pointerY += (targetPointerY - pointerY) * pointerBlend
  }
}
