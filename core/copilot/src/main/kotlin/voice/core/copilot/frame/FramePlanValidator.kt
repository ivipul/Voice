package voice.core.copilot.frame

import kotlin.math.max
import kotlin.math.min

internal class InvalidFramePlanException(message: String) : IllegalArgumentException(message)

/**
 * Checks and normalizes a [FramePlan] against the authoring-guide rules that can be enforced
 * mechanically: prominence floors, mask size matching prominence, no proper names, and
 * sanitised wording. Throws [InvalidFramePlanException] with a message the planner can feed
 * back to Gemini for one retry.
 */
internal class FramePlanValidator(private val nameGuard: NameGuard) {

  fun validate(plan: FramePlan): FramePlan {
    if (plan.palette.isBlank() || plan.location.isBlank() || plan.viewpoint.isBlank()) {
      throw InvalidFramePlanException("palette, location and viewpoint must all be filled in")
    }
    val characters = plan.characters.take(MAX_CHARACTERS).map { normalize(it, plan.establishing) }
    val cleaned = plan.copy(
      palette = PromptSanitizer.clean(plan.palette),
      location = PromptSanitizer.clean(plan.location),
      viewpoint = PromptSanitizer.clean(plan.viewpoint),
      roomFor = plan.roomFor?.let(PromptSanitizer::clean)?.ifBlank { null },
      characters = characters,
    )
    nameGuard.firstViolation(cleaned.allText())?.let { name ->
      throw InvalidFramePlanException("the name \"$name\" appears in the plan; refer to people and animals only as \"the man\", \"the cat\" and so on")
    }
    return cleaned
  }

  private fun normalize(c: PlannedCharacter, establishing: Boolean): PlannedCharacter {
    val framing = Framing.of(c.framing) ?: throw InvalidFramePlanException("unknown framing \"${c.framing}\"")
    if (c.noun.isBlank() || c.description.isBlank() || c.placement.isBlank()) {
      throw InvalidFramePlanException("every character needs a noun, description and placement")
    }
    if (c.maskBox.size != 4) throw InvalidFramePlanException("mask_box must be [x0, y0, x1, y1]")
    if (framing == Framing.SMALL && c.scale.isNullOrBlank()) {
      throw InvalidFramePlanException("a small character needs a scale in words")
    }
    if (framing == Framing.GROUP && c.layout.isNullOrBlank()) {
      throw InvalidFramePlanException("a group needs a layout sentence")
    }
    val prominence = prominenceFor(framing, c.prominencePct, establishing)
    val box = normalizeBox(c.maskBox, framing, prominence)
    return c.copy(
      pron = c.pron.takeIf { it in PRONOUNS } ?: "he",
      description = PromptSanitizer.clean(c.description),
      placement = PromptSanitizer.clean(c.placement),
      framing = framing.key,
      prominencePct = prominence,
      pose = c.pose?.let(PromptSanitizer::clean)?.ifBlank { null },
      gaze = c.gaze?.let(PromptSanitizer::clean)?.ifBlank { null },
      scale = c.scale?.let(PromptSanitizer::clean)?.ifBlank { null },
      layout = c.layout?.let(PromptSanitizer::clean)?.ifBlank { null },
      maskBox = listOf(box.x0, box.y0, box.x1, box.y1),
    )
  }

  private fun prominenceFor(framing: Framing, requested: Int?, establishing: Boolean): Int? {
    if (framing == Framing.SMALL || framing == Framing.GROUP) return null
    val floor = if (establishing) ESTABLISHING_PROMINENCE else MIN_PROMINENCE
    return (requested ?: DEFAULT_PROMINENCE).coerceIn(floor, MAX_PROMINENCE)
  }

  companion object {
    private val PRONOUNS = setOf("he", "she", "it", "they")
    const val MIN_PROMINENCE = 55
    const val DEFAULT_PROMINENCE = 75
    const val ESTABLISHING_PROMINENCE = 40
    const val MAX_PROMINENCE = 90
    private const val MIN_BOX_SIDE = 0.06
    private const val FRAME_ASPECT = FRAME_HEIGHT.toDouble() / FRAME_WIDTH

    // Width a figure needs relative to its own height, so a tall mask is never a thin sliver.
    private fun widthToHeight(framing: Framing): Double = when (framing) {
      Framing.FULL_BODY -> 0.3
      Framing.UPPER_BODY -> 0.4
      Framing.FACE -> 0.6
      Framing.SMALL, Framing.GROUP -> 0.0
    }

    fun normalizeBox(raw: List<Double>, framing: Framing, prominencePct: Int?): MaskBox {
      var x0 = raw[0].coerceIn(0.0, 1.0)
      var y0 = raw[1].coerceIn(0.0, 1.0)
      var x1 = raw[2].coerceIn(0.0, 1.0)
      var y1 = raw[3].coerceIn(0.0, 1.0)
      if (x1 < x0) x0 = x1.also { x1 = x0 }
      if (y1 < y0) y0 = y1.also { y1 = y0 }
      val minHeight = min(prominencePct?.let { it / 100.0 } ?: MIN_BOX_SIDE, 1.0)
      val (ny0, ny1) = grow(y0, y1, minHeight)
      val minWidth = min(max((ny1 - ny0) * FRAME_ASPECT * widthToHeight(framing), MIN_BOX_SIDE), 1.0)
      val (nx0, nx1) = grow(x0, x1, minWidth)
      return MaskBox(nx0, ny0, nx1, ny1)
    }

    private fun grow(a: Double, b: Double, minSize: Double): Pair<Double, Double> {
      if (b - a >= minSize) return a to b
      val center = (a + b) / 2
      var lo = center - minSize / 2
      var hi = center + minSize / 2
      if (lo < 0) {
        hi -= lo
        lo = 0.0
      }
      if (hi > 1) {
        lo -= hi - 1
        hi = 1.0
      }
      return max(lo, 0.0) to min(hi, 1.0)
    }
  }
}

private fun FramePlan.allText(): String = buildString {
  appendLine(palette)
  appendLine(location)
  appendLine(viewpoint)
  roomFor?.let(::appendLine)
  characters.forEach { c ->
    listOfNotNull(c.noun, c.description, c.placement, c.pose, c.gaze, c.scale, c.layout).forEach(::appendLine)
  }
}
