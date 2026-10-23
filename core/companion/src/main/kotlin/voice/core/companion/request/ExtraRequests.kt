package voice.core.companion.request

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.companion.CompanionStores
import voice.core.companion.ExtensionPoint
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaStatus
import voice.core.companion.Schema
import voice.core.companion.companionJson
import voice.core.companion.setup.EXTENSION_POINTS
import voice.core.companion.setup.extensionPointOf
import voice.core.companion.setup.profileText
import voice.core.companion.work.BookSetupRunner
import voice.core.data.BookId
import voice.core.logging.api.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Extras asked for in the middle of listening, through Ask. A request that fits the extension points is built right
 * away; one that repeats or overlaps an Extra the book already has, or that can only be built as a nearby
 * alternative, waits for the listener's yes. Whether an utterance is a request at all is decided before, in Ask.
 */
@SingleIn(AppScope::class)
@Inject
class ExtraRequests(
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
  private val runner: BookSetupRunner,
) {

  private val pending = ConcurrentHashMap<BookId, PendingRequest>()

  /** True while a reply of ours ends in a yes-or-no question, so the headset should listen once more. */
  fun awaitingReply(bookId: BookId): Boolean = pendingFor(bookId) != null

  /**
   * The reply when [utterance] answers a yes-or-no question of ours: yes starts the build, no drops it. Anything
   * else drops the question too and returns null, so the utterance is handled as a new question.
   */
  suspend fun replyToPending(bookId: BookId, utterance: String): String? {
    val waiting = pendingFor(bookId) ?: return null
    pending.remove(bookId)
    return when {
      isAffirmative(utterance) -> {
        start(bookId, waiting.card)
        startedReply(waiting.card)
      }
      isNegative(utterance) -> DECLINED_REPLY
      else -> null
    }
  }

  /**
   * The reply to a request for an Extra, or null when Claude reads [utterance] as an ordinary question after all.
   * [recentTranscript] is what was playing when the listener asked, so the Extra is about that.
   */
  suspend fun request(
    bookId: BookId,
    utterance: String,
    positionMs: Long,
    recentTranscript: String?,
  ): String? {
    val plan = try {
      plan(bookId, utterance, recentTranscript)
    } catch (e: Exception) {
      Logger.w(e, "Planning the requested Extra failed")
      return null
    }
    if (!plan.isRequest) return null
    val brief = requestBrief(utterance, recentTranscript)
    return when (val outcome = outcomeOf(plan, brief, positionMs, existing(bookId))) {
      is RequestOutcome.Build -> {
        start(bookId, outcome.card)
        startedReply(outcome.card)
      }
      is RequestOutcome.Confirm -> {
        pending[bookId] = PendingRequest(outcome.card, System.currentTimeMillis())
        outcome.question
      }
      is RequestOutcome.Refuse -> outcome.reply
    }
  }

  private fun pendingFor(bookId: BookId): PendingRequest? {
    val request = pending[bookId] ?: return null
    if (System.currentTimeMillis() - request.askedAtMs > PENDING_TTL_MS) {
      pending.remove(bookId)
      return null
    }
    return request
  }

  private suspend fun existing(bookId: BookId): List<ExistingExtra> {
    val built = stores.shelf.get(bookId).extras.map { ExistingExtra(it.id, it.title, it.description) }
    val cards = stores.ideas.get(bookId).cards
      .filter { it.status == IdeaStatus.Building || (it.status == IdeaStatus.Built && it.point != ExtensionPoint.Shelf) }
      .map { ExistingExtra(it.id, it.title, it.description) }
    return built + cards
  }

  private suspend fun plan(bookId: BookId, utterance: String, recentTranscript: String?): RequestPlan {
    val profile = stores.profile.get(bookId)
    val prompt = requestPrompt(profileText(profile), utterance, recentTranscript, existing(bookId))
    return companionJson.decodeFromString(RequestPlan.serializer(), claude.askJson(REQUEST_SYSTEM, prompt, REQUEST_SCHEMA))
  }

  private suspend fun start(bookId: BookId, card: IdeaCard) {
    stores.ideas.update(bookId) { it.copy(cards = it.cards + card) }
    runner.build(bookId, card.id)
  }

  private data class PendingRequest(
    val card: IdeaCard,
    val askedAtMs: Long,
  )

  private companion object {
    const val PENDING_TTL_MS = 3 * 60 * 1000L
  }
}

internal data class ExistingExtra(
  val id: String,
  val title: String,
  val description: String,
)

internal sealed interface RequestOutcome {
  data class Build(val card: IdeaCard) : RequestOutcome

  /** Waits for a yes: the request overlaps an existing Extra, or only an alternative can be built. */
  data class Confirm(
    val card: IdeaCard,
    val question: String,
  ) : RequestOutcome

  data class Refuse(val reply: String) : RequestOutcome
}

@Serializable
internal data class RequestPlan(
  val isRequest: Boolean,
  val feasible: Boolean,
  val point: String?,
  val title: String,
  val description: String,
  val kind: String,
  val existingId: String?,
  val existingSummary: String,
  val reason: String,
  val alternativePoint: String?,
  val alternativeTitle: String,
  val alternativeDescription: String,
)

/** Turns Claude's reading of a request into what happens next; no Claude calls, so it is easy to test. */
internal fun outcomeOf(
  plan: RequestPlan,
  brief: String,
  positionMs: Long,
  existing: List<ExistingExtra>,
): RequestOutcome {
  val point = plan.point?.let(::extensionPointOf)
  if (!plan.feasible || point == null) {
    val alternative = plan.alternativePoint?.let(::extensionPointOf)
    val reason = plan.reason.ifBlank { "That's not something I can build here." }.trimEnd('.')
    if (alternative == null || plan.alternativeTitle.isBlank()) {
      return RequestOutcome.Refuse("$reason. Ask me for a quiz, explainer, timeline, map or calculator instead.")
    }
    val card = requestedCard(plan.alternativeTitle, plan.alternativeDescription, alternative, plan.kind, brief, positionMs)
    return RequestOutcome.Confirm(
      card = card,
      question = "I can't build that exactly: $reason. The closest I can make is “${card.title}”, " +
        "${plan.alternativeDescription.lowercaseFirst().trimEnd('.')}. Want me to build that?",
    )
  }
  val card = requestedCard(plan.title, plan.description, point, plan.kind, brief, positionMs)
  val duplicate = existing.firstOrNull { it.id == plan.existingId }
  if (duplicate != null) {
    val what = plan.existingSummary.ifBlank { duplicate.description }.lowercaseFirst().trimEnd('.')
    return RequestOutcome.Confirm(
      card = card,
      question = "There's already “${duplicate.title}”, which $what. Do you still want me to make a new one?",
    )
  }
  return RequestOutcome.Build(card)
}

private fun requestedCard(
  title: String,
  description: String,
  point: ExtensionPoint,
  kind: String,
  brief: String,
  positionMs: Long,
) = IdeaCard(
  id = UUID.randomUUID().toString(),
  title = title.ifBlank { "Requested Extra" },
  description = description,
  why = brief,
  point = point,
  kind = if (point == ExtensionPoint.Shelf) kind else "",
  fromListener = true,
  requestedAtMs = positionMs,
)

internal fun startedReply(card: IdeaCard): String = when (card.point) {
  ExtensionPoint.Shelf -> "On it. I'll build “${card.title}” and put it on your Notebook shelf when it's ready."
  ExtensionPoint.SnipPicture -> "On it. Your snip pictures will switch to “${card.title}” in a minute."
  ExtensionPoint.SceneChips -> "On it. I'll build “${card.title}”; the chips show up on the player once " +
    "the whole book is transcribed."
}

internal const val DECLINED_REPLY = "Okay, I'll leave it."

private val AFFIRMATIVE = Regex(
  "^(yes|yeah|yep|yup|sure|ok|okay|please|do it|go ahead|go for it|build it|make it|absolutely|of course|definitely)\\b",
  RegexOption.IGNORE_CASE,
)
private val NEGATIVE = Regex(
  "^(no|nope|nah|don't|do not|never mind|nevermind|cancel|skip it|forget it|not now)\\b",
  RegexOption.IGNORE_CASE,
)

internal fun isAffirmative(utterance: String): Boolean = AFFIRMATIVE.containsMatchIn(utterance.trim())

internal fun isNegative(utterance: String): Boolean = NEGATIVE.containsMatchIn(utterance.trim())

private val MAKE_VERB = Regex(
  "\\b(make|build|create|generate|draw|design|put together|set up|give me|show me|can i get|i want|i'd like|i would like)\\b",
  RegexOption.IGNORE_CASE,
)
private val EXTRA_NOUN = Regex(
  "\\b(extras?|artifacts?|quiz(zes)?|calculators?|timelines?|maps?|charts?|diagrams?|tables?|cheat ?sheets?|" +
    "flash ?cards?|glossar(y|ies)|trackers?|family trees?|infographics?|explainers?|tools?|checklists?|pages?|" +
    "widgets?|visuali[sz]ations?|summar(y|ies) page|chips|picture style|sketchnotes?)\\b",
  RegexOption.IGNORE_CASE,
)

/** A request names something to make and what to make; Ask uses this to skip asking Claude for the obvious ones. */
fun looksLikeExtraRequest(utterance: String): Boolean =
  MAKE_VERB.containsMatchIn(utterance) && EXTRA_NOUN.containsMatchIn(utterance)

internal fun requestBrief(utterance: String, recentTranscript: String?): String = buildString {
  append("The listener asked for this while listening: \"$utterance\"")
  if (!recentTranscript.isNullOrBlank()) {
    append("\nWhat was playing when they asked (build around this part of the book):\n")
    append(recentTranscript.takeLast(BRIEF_TRANSCRIPT_CHARS))
  }
}

private const val BRIEF_TRANSCRIPT_CHARS = 4_000

private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercase() }

internal const val REQUEST_SYSTEM = "You sit behind the Ask button of an audiobook companion app. Decide whether " +
  "the listener is asking the app to make something for them (an Extra) rather than asking a question about the " +
  "book. isRequest is false for questions, even ones that mention maps or characters (\"where is the castle?\" is " +
  "a question; \"make me a map of the places so far\" is a request). For a request, shape it into an Extra of one " +
  "of the three kinds below, about the part of the book that was playing when they asked. feasible is true only " +
  "if it fits a kind; point is that kind, else null. Title is 2 to 5 words, description one sentence on what the " +
  "listener gets, kind one word for shelf Extras (quiz, map, timeline, calculator, explainer, glossary, tracker, " +
  "flashcards) else empty. existingId is the id of an Extra the book already has that does the same thing or " +
  "something closely related, else null; existingSummary then says in a few words what it does, starting with a " +
  "verb (\"quizzes you on chapters 1 to 4\"). When it isn't feasible, reason is one plain sentence on why, without " +
  "a trailing period, and alternative* describe the closest Extra that can be built (alternativePoint null if " +
  "nothing comes close). Never spoil the story beyond what was playing.\n\n$EXTENSION_POINTS"

internal fun requestPrompt(
  profile: String,
  utterance: String,
  recentTranscript: String?,
  existing: List<ExistingExtra>,
): String = buildString {
  append(profile)
  append("\nExtras the book already has:\n")
  if (existing.isEmpty()) append("(none)\n")
  existing.forEach { append("- id ${it.id}: ${it.title}. ${it.description}\n") }
  if (!recentTranscript.isNullOrBlank()) {
    append("\nWhat was playing when they asked:\n\"\"\"\n${recentTranscript.takeLast(BRIEF_TRANSCRIPT_CHARS)}\n\"\"\"\n")
  }
  append("\nThe listener said:\n\"\"\"\n$utterance\n\"\"\"")
}

private val POINTS_OR_NULL = "One of snip_picture, shelf, scene_chips, or null."

internal val REQUEST_SCHEMA = Schema.obj(
  "isRequest" to Schema.boolean(),
  "feasible" to Schema.boolean(),
  "point" to Schema.nullableString(POINTS_OR_NULL),
  "title" to Schema.string(),
  "description" to Schema.string(),
  "kind" to Schema.string(),
  "existingId" to Schema.nullableString(),
  "existingSummary" to Schema.string(),
  "reason" to Schema.string(),
  "alternativePoint" to Schema.nullableString(POINTS_OR_NULL),
  "alternativeTitle" to Schema.string(),
  "alternativeDescription" to Schema.string(),
)
