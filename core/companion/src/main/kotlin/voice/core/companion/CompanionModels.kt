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
  /** The last generated cover, as a file in the book's folder. */
  val generatedCover: String? = null,
  /** What the listener added to the base cover prompt. */
  val extraCoverPrompt: String = "",
  /** The display face for chapter names, one of the app's named faces ("anton", "serif", ...). */
  val displayFont: String = "anton",
  val snipStyle: SnipPictureStyle? = null,
)

@Serializable
enum class LookSource {
  /** The app's cream, red and black. */
  Default,

  /** Colors picked from the book's own cover. */
  Cover,

  /** A cover Earwig generated, and colors picked from it. */
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
