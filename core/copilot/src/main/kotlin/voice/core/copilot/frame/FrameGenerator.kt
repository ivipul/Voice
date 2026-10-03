package voice.core.copilot.frame

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import voice.core.copilot.GeminiClient
import voice.core.data.Book
import voice.core.logging.api.Logger
import java.util.Locale

internal class FrameRequest(
  val book: Book,
  val highlight: String,
  val transcript: String,
  val positionMs: Long,
)

/**
 * The one-frame job, run at the moment the listener snips: Gemini plans the creative fields,
 * Ideogram draws the base plate (the frame's camera) from the style images, then each character
 * is added with one masked precise edit into the previous call's output. The mask box is refined
 * on the actual image by a Gemini vision query before every edit. Captions and bubbles are
 * never drawn by the model; the Feed shows the snipped text next to the image.
 *
 * At most 1 + [MAX_CHARACTERS] Ideogram calls at $0.06, so a frame costs at most $0.24.
 */
@Inject
class FrameGenerator(
  private val framePack: FramePack,
  private val framePlanner: FramePlanner,
  private val ideogramClient: IdeogramClient,
  private val geminiClient: GeminiClient,
) {

  /** The finished frame as PNG/JPEG bytes. Throws if any step fails; callers treat that as "no image". */
  internal suspend fun generate(request: FrameRequest): ByteArray {
    val pack = framePack.load(request.book.content.name)
    check(pack.styleImages.isNotEmpty()) { "No style images in the frame pack" }

    val plan = framePlanner.plan(request.highlight, request.transcript, pack.characters.map { it.name })
    Logger.d("FrameGenerator: planned ${plan.characters.size} character(s), establishing=${plan.establishing}")

    val nameGuard = NameGuard(pack.characters.map { it.name })
    var image = ideogramClient.generatePlate(FramePrompts.platePrompt(plan), pack.styleImages)
    for (planned in plan.characters) {
      val character = planned.character?.let { name -> pack.characters.firstOrNull { it.name.equals(name, ignoreCase = true) } }
      val look = character?.lookAt(request.positionMs)
      val framing = checkNotNull(Framing.of(planned.framing))
      val box = refineBox(image, planned, framing)
      // The look note is reinforcement only, so one that carries a name is dropped, not sent.
      val lookNote = look?.description?.let(PromptSanitizer::clean)?.takeIf { it.isNotBlank() && nameGuard.firstViolation(it) == null }
      val prompt = FramePrompts.characterPrompt(plan, planned, hasLook = look != null, lookNote = lookNote)
      check(nameGuard.firstViolation(prompt) == null) { "A character name reached an image prompt" }
      image = ideogramClient.preciseEdit(
        prompt = prompt,
        image = image,
        mask = MaskPng.box(FRAME_WIDTH, FRAME_HEIGHT, box),
        references = look?.references.orEmpty(),
      )
    }
    return image
  }

  /**
   * The planned box is a first estimate. Ask a vision model for where this figure belongs on the
   * actual image and use that when it is a sane box; otherwise keep the planned one.
   */
  private suspend fun refineBox(image: ByteArray, planned: PlannedCharacter, framing: Framing): MaskBox {
    val planBox = planned.maskBox.let { MaskBox(it[0], it[1], it[2], it[3]) }
    return try {
      val reply = geminiClient.askJson(
        systemPrompt = VISION_SYSTEM_PROMPT,
        userPrompt = visionPrompt(planned, planBox),
        images = listOf(image),
      )
      parseBox(reply)?.let {
        val box = FramePlanValidator.normalizeBox(it, framing, planned.prominencePct)
        if (box.similarSizeTo(planBox)) box else planBox
      } ?: planBox
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Logger.w(e, "FrameGenerator: vision box failed, using the planned box")
      planBox
    }
  }

  private fun visionPrompt(planned: PlannedCharacter, planBox: MaskBox): String =
    "This is a 736x1312 portrait comic panel. A new figure is to be drawn into it, clear of any figure already there: " +
      "${planned.noun} ${planned.placement.trim().trimEnd('.')}. " +
      "A first estimate of the box is [${planBox.x0.fmt()}, ${planBox.y0.fmt()}, ${planBox.x1.fmt()}, ${planBox.y1.fmt()}]. " +
      "Return the bounding box on this actual image where the whole figure belongs, over empty background " +
      "that physically fits it, as {\"box\": [x0, y0, x1, y1]} with values from 0 to 1."

  internal companion object {
    private const val VISION_SYSTEM_PROMPT =
      "You are a precise image-layout assistant. Reply with JSON only: {\"box\": [x0, y0, x1, y1]}, normalized 0 to 1."
    private val HEIGHT_RATIO = 0.7..1.4
    private val WIDTH_RATIO = 0.5..1.6
    private val AREA_RATIO = 0.5..1.8

    internal fun parseBox(reply: String): List<Double>? = try {
      val box = Json.parseToJsonElement(reply.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        .jsonObject["box"] as? JsonArray
      box?.map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }?.takeIf { it.size == 4 }
    } catch (e: Exception) {
      null
    }

    private fun Double.fmt(): String = String.format(Locale.US, "%.2f", this)

    // The vision box may move the figure but must keep roughly the size the plan intended;
    // a whole-frame or sliver answer is a model slip, not a refinement.
    private fun MaskBox.similarSizeTo(plan: MaskBox): Boolean =
      height / plan.height in HEIGHT_RATIO &&
        width / plan.width in WIDTH_RATIO &&
        (width * height) / (plan.width * plan.height) in AREA_RATIO
  }
}
