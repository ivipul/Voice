package voice.core.copilot.frame

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The creative fields for one comic frame, written by Gemini from the snipped transcript.
 * Everything that reaches an Ideogram prompt is composed from these by [FramePrompts], never
 * written freehand, so every rule of the storyboard authoring guide lands identically.
 */
@Serializable
internal data class FramePlan(
  val palette: String,
  val location: String,
  val viewpoint: String,
  @SerialName("room_for") val roomFor: String? = null,
  val establishing: Boolean = false,
  val characters: List<PlannedCharacter> = emptyList(),
)

@Serializable
internal data class PlannedCharacter(
  /** Exact name from the known-characters list when this is one of them, else null. Never sent to Ideogram. */
  val character: String? = null,
  val noun: String,
  val pron: String = "he",
  /** Nameless physical description starting with an article; used when there are no look images. */
  val description: String,
  val placement: String,
  val framing: String = Framing.FULL_BODY.key,
  @SerialName("prominence_pct") val prominencePct: Int? = null,
  @SerialName("low_angle") val lowAngle: Boolean = false,
  val pose: String? = null,
  val gaze: String? = null,
  val scale: String? = null,
  val layout: String? = null,
  @SerialName("mask_box") val maskBox: List<Double>,
)

internal enum class Framing(val key: String) {
  FULL_BODY("full_body"),
  UPPER_BODY("upper_body"),
  FACE("face"),
  SMALL("small"),
  GROUP("group"),
  ;

  companion object {
    fun of(key: String): Framing? = entries.firstOrNull { it.key == key }
  }
}

/** Normalized box, black = edit in the mask. Coordinates are fractions of the frame. */
internal data class MaskBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
  val width get() = x1 - x0
  val height get() = y1 - y0
}

internal const val FRAME_WIDTH = 736
internal const val FRAME_HEIGHT = 1312
internal const val MAX_CHARACTERS = 3
