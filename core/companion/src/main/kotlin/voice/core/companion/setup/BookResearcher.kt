package voice.core.companion.setup

import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.companion.BookKind
import voice.core.companion.BookProfile
import voice.core.companion.CompanionStores
import voice.core.companion.Schema
import voice.core.companion.StepState
import voice.core.companion.companionJson
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.TranscriptRepository
import kotlin.time.Duration.Companion.milliseconds

/**
 * Looks a book up online while it is being transcribed: what kind of book it is, what it covers, who is in it, and
 * an art direction and display face that suit it. Claude searches the web first, then turns its notes into a profile.
 */
@Inject
class BookResearcher(
  private val bookRepository: BookRepository,
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
  private val transcriptRepository: TranscriptRepository,
) {

  @IgnorableReturnValue
  suspend fun research(bookId: BookId): BookProfile {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    stores.setup.update(bookId) { it.copy(research = StepState.Running, researchError = null) }
    return try {
      val notes = claude.research(system = RESEARCH_SYSTEM, prompt = researchPrompt(book, openingLines(book)))
      val answer = companionJson.decodeFromString<ProfileAnswer>(
        claude.askJson(system = PROFILE_SYSTEM, prompt = profilePrompt(book, notes), schema = PROFILE_SCHEMA),
      )
      val profile = answer.toProfile()
      stores.profile.set(bookId, profile)
      stores.look.update(bookId) { it.copy(displayFont = answer.displayFont) }
      stores.setup.update(bookId) { it.copy(research = StepState.Done) }
      profile
    } catch (e: Exception) {
      Logger.w(e, "Research failed for $bookId")
      stores.setup.update(bookId) { it.copy(research = StepState.Failed, researchError = e.message ?: e.javaClass.simpleName) }
      throw e
    }
  }

  /** The first lines of the transcript, when some exist: they help when the file name says little. */
  private suspend fun openingLines(book: Book): String? {
    val first = book.chapters.firstOrNull() ?: return null
    return transcriptRepository.cues(first.id)
      ?.takeWhile { it.timestampStartMs < OPENING_MS }
      ?.joinToString(" ") { it.text }
      ?.take(OPENING_CHARS)
      ?.takeIf { it.isNotBlank() }
  }

  private companion object {
    const val OPENING_MS = 5 * 60 * 1000L
    const val OPENING_CHARS = 4_000
  }
}

@Serializable
internal data class ProfileAnswer(
  val kind: String,
  val title: String,
  val author: String,
  val summary: String,
  val genre: String,
  val themes: List<String>,
  val largeCast: Boolean,
  val mainCharacters: List<String>,
  val keyTopics: List<String>,
  val tone: String,
  val visualStyle: String,
  val displayFont: String,
) {
  fun toProfile() = BookProfile(
    kind = if (kind == "fiction") BookKind.Fiction else BookKind.NonFiction,
    title = title,
    author = author,
    summary = summary,
    genre = genre,
    themes = themes,
    largeCast = largeCast,
    mainCharacters = mainCharacters,
    keyTopics = keyTopics,
    tone = tone,
    visualStyle = visualStyle,
  )
}

/** The display faces the app ships, with what each suits; the keys match the chapter types in core/ui. */
internal val DISPLAY_FONTS = mapOf(
  "anton" to "tall bold condensed sans, loud and punchy (thrillers, sci-fi, action, business bestsellers)",
  "serif" to "elegant light serif (literary fiction, memoir, history, essays)",
  "condensed" to "industrial condensed sans (tech, science, economics, true crime)",
  "classic" to "bold classic serif (classics, fantasy, romance, philosophy)",
)

internal const val RESEARCH_SYSTEM = "You research audiobooks for a listening companion app. Search the web to " +
  "find out what the book is, then write compact notes: the exact title and author, fiction or non-fiction, " +
  "genre, a spoiler-free premise, the main themes, for fiction the main characters a reader meets early and " +
  "whether the cast is large, for non-fiction the key ideas, methods or topics it teaches, the tone, and how its " +
  "covers and any illustrations look. Never include plot twists or endings. If you can't find the book, say so " +
  "and work from the title and the opening lines."

internal fun researchPrompt(book: Book, openingLines: String?): String = buildString {
  append("Audiobook file name: \"${book.content.name}\"\n")
  book.content.author?.takeIf { it.isNotBlank() }?.let { append("Author tag: $it\n") }
  append("Length: ${book.duration.milliseconds.inWholeMinutes / 60} h ${book.duration.milliseconds.inWholeMinutes % 60} min\n")
  if (openingLines != null) append("\nOpening lines of the narration:\n\"\"\"\n$openingLines\n\"\"\"\n")
  append("\nResearch this book and write the notes.")
}

internal const val PROFILE_SYSTEM = "You turn research notes about an audiobook into a structured profile for a " +
  "listening companion app. Stay spoiler-free: nothing past the opening of the story. Lists hold at most 8 short " +
  "items. visualStyle is one sentence of art direction for pictures that fit the book (medium, palette, mood), " +
  "without naming the book, its author or its characters."

internal fun profilePrompt(book: Book, notes: String): String = buildString {
  append("Audiobook file name: \"${book.content.name}\"\n\nResearch notes:\n\"\"\"\n$notes\n\"\"\"\n\n")
  append("Pick displayFont, the face for chapter names, from these:\n")
  DISPLAY_FONTS.forEach { (key, description) -> append("- $key: $description\n") }
  append("\nmainCharacters is empty for non-fiction; keyTopics is empty for fiction.")
}

internal val PROFILE_SCHEMA = Schema.obj(
  "kind" to Schema.enum(listOf("fiction", "nonfiction")),
  "title" to Schema.string(),
  "author" to Schema.string(),
  "summary" to Schema.string("Two or three spoiler-free sentences."),
  "genre" to Schema.string(),
  "themes" to Schema.array(Schema.string()),
  "largeCast" to Schema.boolean("Fiction: whether there are enough characters that a who's-who helps."),
  "mainCharacters" to Schema.array(Schema.string()),
  "keyTopics" to Schema.array(Schema.string()),
  "tone" to Schema.string(),
  "visualStyle" to Schema.string(),
  "displayFont" to Schema.enum(DISPLAY_FONTS.keys.toList()),
)
