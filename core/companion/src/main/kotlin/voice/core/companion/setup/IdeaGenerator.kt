package voice.core.companion.setup

import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.companion.BookKind
import voice.core.companion.BookProfile
import voice.core.companion.CompanionStores
import voice.core.companion.ExtensionPoint
import voice.core.companion.FeasibilityVerdict
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaFeature
import voice.core.companion.IdeaStatus
import voice.core.companion.MaterialSection
import voice.core.companion.Schema
import voice.core.companion.StepState
import voice.core.companion.companionJson
import voice.core.data.BookId
import voice.core.logging.api.Logger
import java.util.UUID

/**
 * Proposes Extras for a book from its profile, and checks ideas the listener types in. Both are limited to the three
 * extension points, so every card can actually be built.
 */
@Inject
class IdeaGenerator(
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
) {

  /** Adds a fresh set of suggestions to the book's board, keeping the cards the listener already acted on. */
  @IgnorableReturnValue
  suspend fun suggest(bookId: BookId): List<IdeaCard> {
    stores.setup.update(bookId) { it.copy(ideas = StepState.Running, ideasError = null) }
    return try {
      val profile = stores.profile.get(bookId)
      val existing = stores.ideas.get(bookId).cards
      val sections = stores.materials.get(bookId).sections
      val answer = companionJson.decodeFromString<IdeaAnswer>(
        claude.askJson(system = IDEAS_SYSTEM, prompt = ideasPrompt(profile, existing, sections), schema = IDEAS_SCHEMA),
      )
      val sectionIds = sections.map { it.id }.toSet()
      val cards = answer.ideas.mapNotNull { it.toCard(fromListener = false) }
        .map { card -> card.copy(materialSectionId = card.materialSectionId?.takeIf { it in sectionIds }) }
      stores.ideas.update(bookId) { board ->
        board.copy(cards = board.cards.filter { it.status != IdeaStatus.Suggested } + cards)
      }
      stores.setup.update(bookId) { it.copy(ideas = StepState.Done) }
      cards
    } catch (e: Exception) {
      Logger.w(e, "Idea suggestions failed for $bookId")
      stores.setup.update(bookId) { it.copy(ideas = StepState.Failed, ideasError = e.message ?: e.javaClass.simpleName) }
      throw e
    }
  }

  /** Checks the listener's own idea. A feasible one goes on the board as a card ready to build. */
  suspend fun check(bookId: BookId, idea: String): FeasibilityVerdict {
    val profile = stores.profile.get(bookId)
    val sections = stores.materials.get(bookId).sections
    val answer = companionJson.decodeFromString<VerdictAnswer>(
      claude.askJson(system = FEASIBILITY_SYSTEM, prompt = feasibilityPrompt(profile, idea, sections), schema = VERDICT_SCHEMA),
    )
    val point = answer.point?.let(::extensionPointOf)
    val verdict = FeasibilityVerdict(
      feasible = answer.feasible && point != null,
      point = point,
      title = answer.title,
      description = answer.description,
      reason = answer.reason,
      alternative = answer.alternative,
    )
    if (verdict.feasible && point != null) {
      val card = IdeaCard(
        id = UUID.randomUUID().toString(),
        title = verdict.title,
        description = verdict.description,
        why = idea,
        point = point,
        kind = answer.kind,
        fromListener = true,
        features = featuresOf(answer.features),
        materialSectionId = materialSectionOf(answer.materialSection, point, sections),
      )
      stores.ideas.update(bookId) { it.copy(cards = it.cards + card) }
    }
    return verdict
  }
}

@Serializable
internal data class IdeaAnswer(val ideas: List<IdeaItem>)

@Serializable
internal data class IdeaItem(
  val title: String,
  val description: String,
  val why: String,
  val point: String,
  val kind: String,
  val features: List<String> = emptyList(),
  val materialSection: String? = null,
) {
  fun toCard(fromListener: Boolean): IdeaCard? {
    val extensionPoint = extensionPointOf(point) ?: return null
    return IdeaCard(
      id = UUID.randomUUID().toString(),
      title = title,
      description = description,
      why = why,
      point = extensionPoint,
      kind = kind,
      fromListener = fromListener,
      features = featuresOf(features),
      materialSectionId = materialSection?.takeIf { extensionPoint == ExtensionPoint.Shelf },
    )
  }
}

internal fun featuresOf(labels: List<String>): List<IdeaFeature> = labels
  .map { it.trim().trimEnd('.') }
  .filter { it.isNotBlank() }
  .distinct()
  .take(MAX_FEATURES)
  .map(::IdeaFeature)

private const val MAX_FEATURES = 4

@Serializable
internal data class VerdictAnswer(
  val feasible: Boolean,
  val point: String?,
  val kind: String,
  val title: String,
  val description: String,
  val reason: String,
  val alternative: String,
  val features: List<String> = emptyList(),
  val materialSection: String? = null,
)

internal fun extensionPointOf(value: String): ExtensionPoint? = when (value) {
  "snip_picture" -> ExtensionPoint.SnipPicture
  "shelf" -> ExtensionPoint.Shelf
  "scene_chips" -> ExtensionPoint.SceneChips
  else -> null
}

/** What each extension point can do, as Claude is told when it proposes or checks an idea. */
internal const val EXTENSION_POINTS = """The app can build exactly three kinds of Extras:
- snip_picture: when the listener taps Snip, the app writes a short note about the moment and draws a picture for
  it with an image model. An Extra of this kind is an art direction for those pictures (a sketchnote, an annotated
  diagram, a watercolor scene, a vintage poster). One per book; building a new one replaces the old.
- shelf: a self-contained interactive page on the book's shelf, written once in HTML, CSS and JavaScript. It runs
  offline in a sandbox with no network, but can call back into the app: read the book's title and the listener's
  position, read the transcript heard so far, ask Claude a question about the book (answered without spoilers), keep
  small saved values, and jump the player to a time. Good for calculators, explainers with diagrams, quizzes on what
  was heard, timelines, glossaries, decision tools, flashcards and maps drawn in SVG. It can't play audio, use the
  camera or reach the internet. A shelf Extra can be tied to one topic so it unlocks when the book reaches it.
- scene_chips: chips on the player that show who or what is in the scene right now, found by scanning the whole
  transcript. Fiction: characters, each with a simple card of what the listener knows so far. Non-fiction: key
  topics and ideas, each with a card, and shelf Extras tied to a topic unlock when its chip first appears. One per
  book."""

internal const val IDEAS_SYSTEM = "You design companion Explorables for an audiobook app. Suggest ideas that make this " +
  "particular book easier to follow, remember or put to use, and that are fun. Be specific to the book: name the " +
  "concept a calculator works out, the period a timeline covers, the skill a quiz drills. Never spoil the story. " +
  "The listener decides from a small card at a glance, so keep it short. Titles are 2 to 5 words. Descriptions " +
  "are one plain line of at most 12 words on what the listener gets. Features are 2 to 4 parts the Explorable will " +
  "have, 2 to 5 words each (\"Quiz after each chapter\", \"Hand-lettered labels\"), which the listener can untick; " +
  "leave features empty for scene_chips. Why is one sentence on why it suits this book. Kind is one word for shelf Explorables (calculator, explainer, quiz, timeline, glossary, map, " +
  "tracker, flashcards) and empty otherwise.\n\n$EXTENSION_POINTS"

internal fun ideasPrompt(
  profile: BookProfile,
  existing: List<IdeaCard>,
  sections: List<MaterialSection> = emptyList(),
): String = buildString {
  append(profileText(profile))
  if (sections.isNotEmpty()) {
    append("\nThe book comes with a PDF the narrator refers to. Its sections:\n")
    sections.forEach { append("- ${it.id}: ${it.title} (${it.contentType}, pages ${it.pages}). ${it.summary}\n") }
    append(
      "Lean on the PDF: make at least half of the shelf ideas turn one of its sections into something interactive " +
        "or visual (a checklist that remembers, a calculator, an infographic, a tracker, a quiz) and set " +
        "materialSection to that section's id. Ideas that don't use it have materialSection null.\n",
    )
  }
  if (existing.isNotEmpty()) {
    append("\nAlready on the board (don't repeat these):\n")
    existing.forEach { append("- ${it.title} (${it.point.name}, ${it.status.name})\n") }
  }
  append("\nSuggest ${if (sections.isEmpty()) 4 else 5} ideas: one snip_picture style, ")
  val chips = if (profile.kind == BookKind.Fiction) "characters" else "topics"
  append("one scene_chips for $chips if the book would benefit, and the rest shelf Explorables.")
}

internal const val FEASIBILITY_SYSTEM = "You check whether an Explorable a listener asked for can be built in an " +
  "audiobook companion app, and if so, shape it into a card. feasible is true only if it fits one of the three " +
  "kinds below; point is that kind, or null when it fits none. If it can't be built as asked, explain why in one " +
  "plain sentence and give the nearest thing that can, as alternative. Title is 2 to 5 words, description one " +
  "line of at most 12 words, features 2 to 4 parts it will have in 2 to 5 words each (empty for scene_chips), " +
  "kind one word for shelf Explorables or empty.\n\n$EXTENSION_POINTS"

internal fun feasibilityPrompt(profile: BookProfile, idea: String, sections: List<MaterialSection> = emptyList()): String =
  profileText(profile) + pdfSectionsText(sections) + "\nThe listener's idea:\n\"\"\"\n$idea\n\"\"\""

/** The book's PDF sections, for a prompt that turns one idea into an Extra; empty when there's no PDF. */
internal fun pdfSectionsText(sections: List<MaterialSection>): String = if (sections.isEmpty()) {
  ""
} else {
  buildString {
    append("\nThe book comes with a PDF. Its sections:\n")
    sections.forEach { append("- ${it.id}: ${it.title} (${it.contentType}, pages ${it.pages}). ${it.summary}\n") }
    append("If a shelf Explorable would draw on one of these sections, set materialSection to its id so it is built from ")
    append("the PDF's content; otherwise materialSection is null.\n")
  }
}

/** A PDF section id Claude picked, kept only for a shelf Extra and only when the book has that section. */
internal fun materialSectionOf(id: String?, point: ExtensionPoint?, sections: List<MaterialSection>): String? =
  id?.takeIf { point == ExtensionPoint.Shelf && sections.any { section -> section.id == it } }

internal fun profileText(profile: BookProfile): String = buildString {
  append("Book: \"${profile.title}\"")
  if (profile.author.isNotBlank()) append(" by ${profile.author}")
  append(" (${if (profile.kind == BookKind.Fiction) "fiction" else "non-fiction"}, ${profile.genre})\n")
  if (profile.summary.isNotBlank()) append("About: ${profile.summary}\n")
  if (profile.themes.isNotEmpty()) append("Themes: ${profile.themes.joinToString()}\n")
  if (profile.mainCharacters.isNotEmpty()) append("Main characters: ${profile.mainCharacters.joinToString()}\n")
  if (profile.keyTopics.isNotEmpty()) append("Key topics: ${profile.keyTopics.joinToString()}\n")
  if (profile.tone.isNotBlank()) append("Tone: ${profile.tone}\n")
}

private val POINTS = listOf("snip_picture", "shelf", "scene_chips")

internal val IDEAS_SCHEMA = Schema.obj(
  "ideas" to Schema.array(
    Schema.obj(
      "title" to Schema.string(),
      "description" to Schema.string(),
      "why" to Schema.string(),
      "point" to Schema.enum(POINTS),
      "kind" to Schema.string(),
      "features" to Schema.array(Schema.string()),
      "materialSection" to Schema.nullableString("The id of the PDF section it brings to life, or null."),
    ),
  ),
)

internal val VERDICT_SCHEMA = Schema.obj(
  "feasible" to Schema.boolean(),
  "point" to Schema.nullableString("One of snip_picture, shelf, scene_chips, or null."),
  "kind" to Schema.string(),
  "title" to Schema.string(),
  "description" to Schema.string(),
  "reason" to Schema.string(),
  "alternative" to Schema.string(),
  "features" to Schema.array(Schema.string()),
  "materialSection" to Schema.nullableString("The id of the PDF section a shelf Explorable is built from, or null."),
)
