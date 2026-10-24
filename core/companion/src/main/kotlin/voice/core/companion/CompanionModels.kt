package voice.core.companion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BookKind {
  @SerialName("fiction")
  Fiction,

  @SerialName("nonfiction")
  NonFiction,
}

/** What Claude found out about a book online, before or while it is transcribed. Spoiler-free by design. */
@Serializable
data class BookProfile(
  val kind: BookKind = BookKind.NonFiction,
  val title: String = "",
  val author: String = "",
  /** Two or three sentences on what the book is about, without spoilers. */
  val summary: String = "",
  val genre: String = "",
  val themes: List<String> = emptyList(),
  /** Fiction: whether the cast is big enough that character chips really help. */
  val largeCast: Boolean = false,
  /** Fiction: the main characters a new reader meets early on. */
  val mainCharacters: List<String> = emptyList(),
  /** Non-fiction: the key ideas, methods or topics the book teaches. */
  val keyTopics: List<String> = emptyList(),
  val tone: String = "",
  /** Art direction for pictures, covers and Extras that suits the book, e.g. "clean editorial infographics in navy". */
  val visualStyle: String = "",
)

/** Where a book is in its setup: transcription, research and idea generation, shown on the setup screen. */
@Serializable
data class BookSetup(
  val transcription: TranscriptionState = TranscriptionState(),
  val research: StepState = StepState.NotStarted,
  val researchError: String? = null,
  val ideas: StepState = StepState.NotStarted,
  val ideasError: String? = null,
  val scene: StepState = StepState.NotStarted,
  val sceneError: String? = null,
  /** Short summaries of each chapter, which let Ask answer questions about the whole book cheaply. */
  val summaries: StepState = StepState.NotStarted,
  /** Reading the PDFs that came with the book, then finding where the narration refers to them. */
  val materials: StepState = StepState.NotStarted,
  val materialsError: String? = null,
  /** The listener closed the setup screen once; the library then stops pointing at it. */
  val dismissed: Boolean = false,
)

@Serializable
enum class StepState {
  NotStarted,
  Running,
  Done,
  Failed,
}

@Serializable
data class TranscriptionState(
  val state: StepState = StepState.NotStarted,
  val partsDone: Int = 0,
  val partsTotal: Int = 0,
  /** Audio files of the book done out of all of them, for books made of several files. */
  val filesDone: Int = 0,
  val filesTotal: Int = 0,
  val error: String? = null,
) {

  val fraction: Float
    get() = when {
      state == StepState.Done -> 1F
      partsTotal == 0 -> 0F
      else -> partsDone.toFloat() / partsTotal
    }
}

/** The places an Extra can plug into. Claude can build for these and nothing else. */
@Serializable
enum class ExtensionPoint {
  /** The style of the picture drawn for each snip. */
  @SerialName("snip_picture")
  SnipPicture,

  /** An interactive page on the Extras shelf of the Notebook. */
  @SerialName("shelf")
  Shelf,

  /** Character or topic chips on the player while they are in the scene. */
  @SerialName("scene_chips")
  SceneChips,
}

@Serializable
enum class IdeaStatus {
  Suggested,
  Building,
  Built,
  Skipped,
  Failed,
}

/** One proposed Extra, shown as a card on the setup screen. */
@Serializable
data class IdeaCard(
  val id: String,
  val title: String,
  val description: String,
  /** Why it helps with this particular book. */
  val why: String = "",
  val point: ExtensionPoint,
  /** Shelf Extras: what kind of page (calculator, explainer, quiz, timeline, reference). */
  val kind: String = "",
  val status: IdeaStatus = IdeaStatus.Suggested,
  /** True for an idea the listener typed in. */
  val fromListener: Boolean = false,
  val error: String? = null,
  /** The built Extra, once there is one. */
  val extraId: String? = null,
  /** For an Extra asked for through Ask: where in the whole book the listener was. */
  val requestedAtMs: Long? = null,
  /** The parts the Extra would have, each one ticked until the listener unticks it. */
  val features: List<IdeaFeature> = emptyList(),
  /** The section of the book's PDF material this Extra brings to life, if any. */
  val materialSectionId: String? = null,
  /** What the listener typed on the card to add to or change the idea. */
  val listenerNote: String = "",
  /** Changes the listener asked for on the built Extra, while they are being made or after they failed. */
  val pendingChange: String = "",
) {

  val includedFeatures: List<String> get() = features.filter { it.included }.map { it.label }
  val leftOutFeatures: List<String> get() = features.filterNot { it.included }.map { it.label }
}

@Serializable
data class IdeaFeature(
  val label: String,
  val included: Boolean = true,
)

@Serializable
data class IdeaBoard(
  val cards: List<IdeaCard> = emptyList(),
)

/** Claude's answer to an idea the listener typed in. */
@Serializable
data class FeasibilityVerdict(
  val feasible: Boolean,
  val point: ExtensionPoint? = null,
  val title: String = "",
  val description: String = "",
  val reason: String = "",
  /** When it can't be built as asked: the nearest thing that can. */
  val alternative: String = "",
)

/** A built shelf Extra: one self-contained page plus what the shelf shows about it. */
@Serializable
data class Extra(
  val id: String,
  val title: String,
  val description: String,
  val kind: String = "",
  val html: String,
  val createdAtMs: Long,
  /** Locked until the listener reaches this point in the book, so an Extra never gets ahead of the story. */
  val unlockAtMs: Long = 0,
  /** Non-fiction: the topic chip that unlocks it, if any. */
  val topicId: String? = null,
  /** The prompt it was built from, kept for repairs and rebuilds. */
  val brief: String = "",
  val lastError: String? = null,
)

@Serializable
data class ExtraShelf(
  val extras: List<Extra> = emptyList(),
)

/** How the snip pictures of a book look, chosen by building a snip picture idea. */
@Serializable
data class SnipPictureStyle(
  val name: String = "",
  /** Art direction appended to every snip picture prompt. */
  val direction: String = "",
)

/** A book's colors and cover choices. */
@Serializable
data class BookLook(
  val source: LookSource = LookSource.Default,
  /** Colors taken from the cover (or a generated cover), as ARGB ints. */
  val palette: StoredPalette? = null,
  /** The generated cover in use, or last in use, as a file in the book's folder. */
  val generatedCover: String? = null,
  /** Every cover drawn for the book that is kept, newest first. */
  val generatedCovers: List<GeneratedCover> = emptyList(),
  /** When the cover being drawn right now started, if one is. */
  val drawingSinceMs: Long? = null,
  /** What the listener added to the base cover prompt. */
  val extraCoverPrompt: String = "",
  /** The layout of the last generated cover, so the next one tries another. */
  val coverComposition: String = "",
  /** The display face for chapter names, one of the app's named faces ("anton", "serif", ...). */
  val displayFont: String = "anton",
  val snipStyle: SnipPictureStyle? = null,
) {

  /** The drawn covers to choose from, including one drawn before the history was kept. */
  val coverHistory: List<GeneratedCover>
    get() = generatedCovers.ifEmpty { listOfNotNull(generatedCover).map { GeneratedCover(path = it) } }

  /** Whether a cover is being drawn; a draw cut off by the app closing stops counting after a few minutes. */
  fun isDrawing(nowMs: Long): Boolean = drawingSinceMs != null && nowMs - drawingSinceMs < DRAWING_TIMEOUT_MS
}

private const val DRAWING_TIMEOUT_MS = 5 * 60 * 1000L

/** A cover Tandem drew, with the layout, chapter-name face and curated palette it was planned with. */
@Serializable
data class GeneratedCover(
  val path: String,
  val composition: String = "",
  val font: String = "",
  val palette: String = "",
)

@Serializable
enum class LookSource {
  /** The app's cream, red and black. */
  Default,

  /** Colors picked from the book's own cover. */
  Cover,

  /** A cover Tandem generated, and colors picked from it. */
  Generated,
}

@Serializable
data class StoredPalette(
  val background: Int,
  val content: Int,
  val highlight: Int,
  val onHighlight: Int,
  val accent: Int,
  val onAccent: Int,
  val shadow: Int,
)

/** A few sentences per chapter, keyed by [voice.core.transcript.BookSection.key]. */
@Serializable
data class ChapterSummaries(
  val byKey: Map<String, String> = emptyMap(),
)

/** Material that came with the audiobook, like a companion PDF, and what Claude found in it. */
@Serializable
data class BookMaterials(
  val files: List<MaterialFile> = emptyList(),
  val sections: List<MaterialSection> = emptyList(),
  /** Where the narration refers to each section, as whole-book positions; [SceneCue.entry] is the section id. */
  val cues: List<SceneCue> = emptyList(),
  val linked: Boolean = false,
)

@Serializable
data class MaterialFile(
  val id: String,
  /** The name the listener's file had. */
  val name: String,
  /** The copy in the book's folder, relative to it. */
  val path: String,
  val studied: Boolean = false,
  val error: String? = null,
)

@Serializable
data class MaterialSection(
  val id: String,
  val fileId: String,
  val title: String,
  val pages: String = "",
  /** One or two sentences on what the section is for. */
  val summary: String = "",
  /** What kind of content it holds: checklist, table, framework, exercise, diagram, list, reference. */
  val contentType: String = "",
  /** The section's information in plain text, faithful to the PDF, which Extras and answers are built from. */
  val extract: String = "",
  val keyTerms: List<String> = emptyList(),
  /** The Extra built from this section, once there is one. */
  val extraId: String? = null,
)

/** The sections the narration refers to around [positionMs]. */
fun BookMaterials.activeAt(positionMs: Long): List<MaterialSection> {
  val ids = cues.filter { positionMs in it.startMs..it.endMs }.mapTo(mutableSetOf()) { it.entry }
  return sections.filter { it.id in ids }
}

/** The first time the narration refers to [sectionId], or null if it never does. */
fun BookMaterials.firstCueMs(sectionId: String): Long? = cues.filter { it.entry == sectionId }.minOfOrNull { it.startMs }
