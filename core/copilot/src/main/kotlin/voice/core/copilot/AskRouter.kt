package voice.core.copilot

import com.anthropic.models.messages.OutputConfig
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import voice.core.ai.ClaudeClient
import voice.core.ai.ClaudeModels
import voice.core.companion.BookKind
import voice.core.companion.BookProfile
import voice.core.companion.Schema
import voice.core.companion.request.looksLikeExtraRequest
import voice.core.data.Book
import voice.core.logging.api.Logger
import voice.core.transcript.currentSection

/** Where the answer to a question should come from, which decides how much of the transcript goes with it. */
enum class AskRoute {
  /** General knowledge beyond the book: web search, plus the last few minutes for context. */
  Web,

  /** What is being said or happening right now. */
  Recent,

  /** The chapter playing now. */
  Chapter,

  /** Anything earlier in the book: chapter summaries plus the passages that mention what was asked about. */
  Book,

  /** A request to make an Extra; no transcript goes to the answer. */
  BuildExtra,
}

data class AskDecision(
  val route: AskRoute,
  val recentMinutes: Int = DEFAULT_RECENT_MINUTES,
  /** Names, places or terms to look for earlier in the book, for [AskRoute.Book]. */
  val searchTerms: List<String> = emptyList(),
)

internal const val DEFAULT_RECENT_MINUTES = 4

/**
 * Picks the route for a question. Obvious questions are settled on the phone by their wording, with no call at all;
 * the rest go to the small, fast model with only the question, the chapter name and the last half minute.
 */
@Inject
class AskRouter(private val claude: ClaudeClient) {

  suspend fun route(
    book: Book,
    profile: BookProfile?,
    question: String,
    lastSeconds: String?,
  ): AskDecision {
    quickRoute(question)?.let { return it }
    return try {
      val answer = Json.decodeFromString(
        RouteAnswer.serializer(),
        claude.askJson(
          system = ROUTER_SYSTEM,
          prompt = routerPrompt(book, profile, question, lastSeconds),
          schema = ROUTE_SCHEMA,
          model = ClaudeModels.SCAN,
          maxTokens = ROUTER_MAX_TOKENS,
          effort = OutputConfig.Effort.LOW,
        ),
      )
      answer.toDecision()
    } catch (e: Exception) {
      Logger.w(e, "Ask routing failed, answering from the last minutes")
      FALLBACK_DECISION
    }.also { Logger.d("Ask route: $it") }
  }

  private fun routerPrompt(
    book: Book,
    profile: BookProfile?,
    question: String,
    lastSeconds: String?,
  ): String = buildString {
    append("Book: \"${book.content.name}\"")
    book.content.author?.takeIf { it.isNotBlank() }?.let { append(" by $it") }
    if (profile != null) append(if (profile.kind == BookKind.Fiction) " (fiction)" else " (non-fiction)")
    append("\nChapter playing: ${book.currentSection().name}\n")
    if (!lastSeconds.isNullOrBlank()) append("Just heard: \"${lastSeconds.takeLast(LAST_SECONDS_CHARS)}\"\n")
    append("Question: \"$question\"")
  }

  private companion object {
    const val ROUTER_MAX_TOKENS = 300L
    const val LAST_SECONDS_CHARS = 600
  }
}

/** Used when the router can't be reached: the last half hour, as Ask answered before it had routes. */
internal val FALLBACK_DECISION = AskDecision(AskRoute.Recent, recentMinutes = 30)

private val CHAPTER_WORDS = Regex(
  "\\b(this|the current|current) (chapter|section|part)\\b|\\bchapter so far\\b",
  RegexOption.IGNORE_CASE,
)
private val RECENT_WORDS = Regex(
  "\\b(just (said|say|mentioned|happened|now)|right now|a (moment|minute|second) ago|" +
    "did (he|she|they|the (author|narrator|speaker)) (just )?(say|mean)|what does (that|this) mean)\\b",
  RegexOption.IGNORE_CASE,
)

/** The routes the wording alone makes clear, so they skip the router. */
internal fun quickRoute(question: String): AskDecision? = when {
  looksLikeExtraRequest(question) -> AskDecision(AskRoute.BuildExtra)
  CHAPTER_WORDS.containsMatchIn(question) -> AskDecision(AskRoute.Chapter)
  RECENT_WORDS.containsMatchIn(question) -> AskDecision(AskRoute.Recent, recentMinutes = 2)
  else -> null
}

@Serializable
internal data class RouteAnswer(
  val route: String,
  val recentMinutes: Int,
  val searchTerms: List<String>,
) {

  fun toDecision(): AskDecision {
    val route = when (route) {
      "web" -> AskRoute.Web
      "chapter" -> AskRoute.Chapter
      "book" -> AskRoute.Book
      "build_extra" -> AskRoute.BuildExtra
      else -> AskRoute.Recent
    }
    return AskDecision(
      route = route,
      recentMinutes = recentMinutes.coerceIn(MIN_RECENT_MINUTES, MAX_RECENT_MINUTES),
      searchTerms = searchTerms.map { it.trim() }.filter { it.length >= 2 }.distinct().take(MAX_SEARCH_TERMS),
    )
  }
}

private const val MIN_RECENT_MINUTES = 2
private const val MAX_RECENT_MINUTES = 10
private const val MAX_SEARCH_TERMS = 6

internal const val ROUTER_SYSTEM = "You route a question an audiobook listener asked to the source that answers it " +
  "best, so the app sends no more of the book than needed. Routes:\n" +
  "- recent: about what is being said or happening right now, or something just mentioned.\n" +
  "- chapter: about the chapter playing now as a whole.\n" +
  "- book: about earlier parts of the book, a person, place or idea across the book, or how things connect.\n" +
  "- web: general knowledge the book isn't the source for: real-world facts, history, science, the author, " +
  "definitions, the world beyond the story.\n" +
  "- build_extra: asking the app to make something, like a quiz, map, timeline or calculator.\n" +
  "recentMinutes is how many minutes of the latest audio help (2 to 10; more for 'what's been going on'). " +
  "searchTerms are 1 to 6 names, places or terms to find earlier in the book (book route), else empty."

private val ROUTES = listOf("recent", "chapter", "book", "web", "build_extra")

internal val ROUTE_SCHEMA = Schema.obj(
  "route" to Schema.enum(ROUTES),
  "recentMinutes" to Schema.integer(),
  "searchTerms" to Schema.array(Schema.string()),
)
