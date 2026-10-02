package voice.core.copilot.frame

import kotlin.math.abs

/**
 * Kotlin port of the storyboard builder's shared templates (storyboard authoring guide v2), for
 * a one-frame job: one base plate (the frame's camera) and one masked precise edit per character.
 * Wording is deliberately identical to `build_storyboard.py`: style-reference sentence, STYLE
 * LOCK in every character add, prominence and visibility sentences, the KEEP clause, and no
 * proper names anywhere.
 */
internal object FramePrompts {

  private const val PREFIX = "Comic-book panel. Full-bleed illustration, no border."
  private const val STYLE_REF =
    "The attached images are style references only: copy their comic-book ink linework, colour palette and " +
      "lighting mood, but do not copy any of their subjects, objects or layouts."
  private const val NO_TEXT_BASE = "No text, lettering or signs with writing anywhere."
  private const val NO_PEOPLE = "No people or characters in the image."
  private const val KEEP = "Keep everything else exactly the same."

  private val FRACTIONS = mapOf(
    90 to "nine tenths", 85 to "about five sixths", 80 to "about four fifths", 75 to "about three quarters",
    70 to "about seven tenths", 65 to "about two thirds", 60 to "about three fifths", 55 to "a little over half",
    50 to "about half", 45 to "a little under half", 40 to "about two fifths",
  )
  private val POSSESSIVE = mapOf("he" to "his", "she" to "her", "it" to "its", "they" to "their")

  fun styleLock(palette: String): String =
    "Keep the exact same comic-book ink illustration style as the source image: bold black linework, flat cel " +
      "colouring, ${palette.trim().trimEnd('.')}. It must look like a hand-drawn comic panel, not a photograph, " +
      "not realistic, not 3D."

  fun platePrompt(plan: FramePlan): String = listOfNotNull(
    PREFIX,
    plan.location.sentence(),
    plan.viewpoint.sentence(),
    plan.roomFor?.sentence(),
    STYLE_REF,
    NO_PEOPLE,
    NO_TEXT_BASE,
  ).joinToString(" ")

  /** [lookNote] is the look table's description for the look in force at the snip's timestamp. */
  fun characterPrompt(plan: FramePlan, c: PlannedCharacter, hasLook: Boolean, lookNote: String?): String {
    val poss = POSSESSIVE.getValue(c.pron)
    val placement = c.placement.trim().trimEnd('.')
    val intro = if (hasLook) "Add the ${c.noun} shown in the reference images, $placement." else "Add ${c.description.trim().trimEnd('.')}, $placement."
    val drawn = if (hasLook) {
      val note = lookNote?.let { " This outfit and look: ${it.sentence()}" }.orEmpty()
      " Drawn exactly as in the reference images: $poss exact face, hair and build.$note"
    } else {
      ""
    }
    val parts = mutableListOf(intro + drawn)
    parts += visibility(c, plan.establishing)
    parts += styleLock(plan.palette)
    if (c.lowAngle) parts += "Low camera angle."
    c.pose?.let { parts += it.sentence() }
    c.gaze?.let { parts += it.sentence() }
    parts += KEEP
    return parts.filter { it.isNotBlank() }.joinToString(" ")
  }

  private fun visibility(c: PlannedCharacter, establishing: Boolean): String {
    val poss = POSSESSIVE.getValue(c.pron)
    val cap = c.pron.replaceFirstChar { it.uppercase() }
    val possCap = poss.replaceFirstChar { it.uppercase() }
    val fraction = c.prominencePct?.let(::fractionOf).orEmpty()
    return when (Framing.of(c.framing)) {
      Framing.FULL_BODY -> {
        val where = if (establishing) {
          "$cap stands in the middle ground, filling $fraction of the frame height from head to feet"
        } else {
          "$cap is close to the camera and large in the frame, filling $fraction of the frame height from head to feet"
        }
        "$where, with $poss whole body clearly visible and uncropped: head, torso, both legs and both feet fully " +
          "on the ground, nothing hidden behind other objects. " +
          "Strong, expressive, well-drawn face with sharp detail, clearly visible."
      }
      Framing.UPPER_BODY ->
        "$cap is close to the camera, shown from the waist up, head and shoulders large in the frame, filling " +
          "$fraction of the frame height, nothing hidden behind other objects. Strong, expressive, well-drawn face " +
          "with sharp detail, clearly visible, not covered by hands or arms."
      Framing.FACE ->
        "$possCap face is shown in a tight close-up, filling $fraction of the frame height. Strong, expressive, " +
          "well-drawn face with sharp detail, clearly visible, not covered by hands or arms."
      Framing.SMALL ->
        "${c.scale.orEmpty().trim().trimEnd('.')}. $possCap whole body is visible, nothing hidden behind other " +
          "objects, and the face is sharp and clearly readable."
      Framing.GROUP ->
        "${c.layout.orEmpty().sentence()} Every figure is whole, clearly visible and uncropped, with sharp, " +
          "readable faces."
      null -> ""
    }
  }

  private fun fractionOf(pct: Int): String = FRACTIONS.minBy { abs(it.key - pct) }.value

  private fun String.sentence(): String = trim().let { if (it.isNotEmpty() && it.last() !in ".!?") "$it." else it }
}
