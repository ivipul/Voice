package voice.core.copilot.frame

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import voice.core.copilot.GeminiClient
import voice.core.logging.api.Logger

/**
 * Has Gemini write one frame's creative fields (location, camera, palette, characters with
 * placement, pose, gaze and a first-estimate mask box) from the snipped moment. Gemini never
 * writes an Ideogram prompt; [FramePrompts] does. One retry is allowed, with the validator's
 * complaint fed back, for plans that name characters or break a mechanical rule.
 */
@Inject
class FramePlanner(private val geminiClient: GeminiClient) {

  private val json = Json { ignoreUnknownKeys = true }

  internal suspend fun plan(highlight: String, transcript: String, knownNames: List<String>): FramePlan {
    val validator = FramePlanValidator(NameGuard(knownNames))
    var feedback: String? = null
    var lastError: Exception? = null
    repeat(MAX_ATTEMPTS) { attempt ->
      try {
        val reply = geminiClient.askJson(
          systemPrompt = SYSTEM_PROMPT,
          userPrompt = userPrompt(highlight, transcript, knownNames, feedback),
        )
        return validator.validate(json.decodeFromString(FramePlan.serializer(), reply.stripFences()))
      } catch (e: CancellationException) {
        throw e
      } catch (e: InvalidFramePlanException) {
        Logger.w("FramePlanner: plan rejected (attempt ${attempt + 1}): ${e.message}")
        feedback = e.message
        lastError = e
      } catch (e: SerializationException) {
        Logger.w("FramePlanner: unreadable plan (attempt ${attempt + 1}): ${e.message}")
        feedback = "the reply was not valid JSON in the required shape"
        lastError = e
      }
    }
    throw lastError ?: IllegalStateException("FramePlanner failed")
  }

  internal fun userPrompt(highlight: String, transcript: String, knownNames: List<String>, feedback: String?): String =
    buildString {
      appendLine("Moment caption:")
      appendLine(highlight)
      appendLine()
      appendLine("Excerpt (the last few minutes before the listener tapped Snip, ending on the moment):")
      appendLine("\"\"\"")
      appendLine(transcript)
      appendLine("\"\"\"")
      appendLine()
      appendLine(
        if (knownNames.isEmpty()) {
          "Known characters: none. Set \"character\" to null for everyone."
        } else {
          "Known characters (set \"character\" to the exact name when that person is in the frame): ${knownNames.joinToString(", ")}"
        },
      )
      if (feedback != null) {
        appendLine()
        appendLine("Your previous answer was rejected: $feedback. Fix that and answer again.")
      }
    }

  private fun String.stripFences(): String =
    trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

  internal companion object {
    private const val MAX_ATTEMPTS = 2

    const val SYSTEM_PROMPT = """You are a comic storyboard artist planning ONE vertical (9:16) comic-book panel that freezes the single most dramatic moment at the end of an audiobook excerpt. Reply with JSON only, in exactly this shape:
{
  "palette": "colours of the moment's own light, in words, e.g. the same cold blue night palette and sodium-orange lamp glow",
  "location": "the empty location only: concrete, specific anchors (buildings, trees, props, weather, light). No people or creatures. Signs are blank and bare.",
  "viewpoint": "one concrete camera sentence, e.g. The camera is at street level and tilts up in a dramatic wide-angle low-angle shot.",
  "room_for": "one sentence naming the empty area left for the characters, e.g. Leave an empty stretch of pavement beside the tree.",
  "establishing": false,
  "characters": [
    {
      "character": "exact known-character name, or null",
      "noun": "the man / the woman / the cat / the goblin",
      "pron": "he | she | it | they",
      "description": "a nameless physical description starting with a or an, used if no reference images exist",
      "placement": "where this figure physically is in the location, concrete",
      "framing": "full_body | upper_body | face | small | group",
      "prominence_pct": 75,
      "low_angle": true,
      "pose": "an action pose with one concrete interaction, not a loose verb",
      "gaze": "a gaze that is not at the viewer, said positively, with the face visible and not covered by arms or hands",
      "scale": "only for small: realistic scale in words, e.g. a normal house cat about a quarter of the man's height",
      "layout": "only for group: one sentence placing every figure",
      "mask_box": [x0, y0, x1, y1]
    }
  ]
}
Rules:
- Never write a character's name or nickname in any field except "character". Say "the man", "the cat". Place names are fine.
- 0 to 3 characters, largest foreground figure first. Use "character": null for anyone not in the known list.
- Choose a dramatic camera that fits the beat (low angle looking up, high angle, side-on, extreme close-up), not a flat eye-level default.
- prominence_pct is the percent of frame height the figure fills: 75 by default, never below 55, or 40 only when "establishing" is true for a pure establishing wide. It applies to full_body, upper_body and face. Two standing humans in one frame: 55 to 65 each, placed left and right.
- mask_box is normalized [x0, y0, x1, y1] on a 736x1312 portrait frame where the figure will be drawn. Its height is the prominence plus 0 to 20 points. Boxes of different figures must not overlap much. Keep animal boxes small (about 0.16 wide by 0.08 high) and put them where the animal physically belongs.
- Show the face: no arms over the head covering it. Use "small" for animals and small subjects, with a scale in words.
- No text, lettering, signs with writing, sound effects or speech bubbles anywhere; those are drawn separately.
- Stay inside what the excerpt shows. Do not invent plot."""
  }
}
