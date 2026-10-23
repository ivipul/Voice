package voice.core.companion.scene

import com.anthropic.models.messages.OutputConfig
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.ai.ClaudeModels
import voice.core.ai.ReplyCutOffException
import voice.core.companion.BookKind
import voice.core.companion.CompanionStores
import voice.core.companion.SceneCue
import voice.core.companion.SceneEntry
import voice.core.companion.SceneIndex
import voice.core.companion.SceneNote
import voice.core.companion.Schema
import voice.core.companion.StepState
import voice.core.companion.companionJson
import voice.core.companion.firstCueMs
import voice.core.companion.setup.profileText
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.TranscriptRepository

/** One transcript line placed in the whole book. */
internal data class BookLine(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

/**
 * Extension point 3: scans the whole transcript, window by window, for who (fiction) or what (non-fiction) is in the
 * scene. Claude only picks transcript lines; the times come from the lines, so chips line up with the audio. Each
 * window can add a note to an entry, shown only once the listener reaches it, so cards never spoil.
 */
@Inject
class SceneIndexer(
  private val bookRepository: BookRepository,
  private val transcriptRepository: TranscriptRepository,
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
) {

  @IgnorableReturnValue
  suspend fun index(bookId: BookId): SceneIndex {
    stores.setup.update(bookId) { it.copy(scene = StepState.Running, sceneError = null) }
    return try {
      val profile = stores.profile.get(bookId)
      val lines = bookLines(bookId)
      check(lines.isNotEmpty()) { "The book has no transcript yet" }
      val builder = SceneIndexBuilder(profile.kind)
      windowsOf(lines).forEach { window ->
        splittingWhenCutOff(window) { part ->
          val answer = companionJson.decodeFromString<WindowAnswer>(
            claude.askJson(
              system = sceneSystem(profile.kind),
              prompt = profileText(profile) + "\n" + windowPrompt(part, builder.known()),
              schema = WINDOW_SCHEMA,
              model = ClaudeModels.SCAN,
              maxTokens = WINDOW_MAX_TOKENS,
              effort = OutputConfig.Effort.LOW,
            ),
          )
          builder.add(part, answer)
        }
      }
      val index = builder.build()
      val linked = linkExistingExtras(bookId, index)
      stores.scene.set(bookId, linked)
      stores.setup.update(bookId) { it.copy(scene = StepState.Done) }
      linked
    } catch (e: Exception) {
      Logger.w(e, "Scene indexing failed for $bookId")
      stores.setup.update(bookId) { it.copy(scene = StepState.Failed, sceneError = e.message ?: e.javaClass.simpleName) }
      throw e
    }
  }

  private suspend fun bookLines(bookId: BookId): List<BookLine> {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    return transcriptRepository.bookLines(book)
  }

  /** Extras built before the scan get tied to their topic now, and unlock when it first comes up. */
  private suspend fun linkExistingExtras(bookId: BookId, index: SceneIndex): SceneIndex {
    val loose = stores.shelf.get(bookId).extras.filter { it.topicId == null }
    if (loose.isEmpty() || index.entries.isEmpty()) return index
    val answer = try {
      companionJson.decodeFromString<LinkAnswer>(
        claude.askJson(
          system = LINK_SYSTEM,
          prompt = linkPrompt(index, loose.map { it.id to "${it.title}: ${it.description}" }),
          schema = LINK_SCHEMA,
          effort = OutputConfig.Effort.LOW,
        ),
      )
    } catch (e: Exception) {
      Logger.w(e, "Linking Extras to topics failed")
      return index
    }
    val links = answer.links.filter { link -> index.entries.any { it.id == link.topicId } && loose.any { it.id == link.extraId } }
      .associate { it.extraId to it.topicId }
    stores.shelf.update(bookId) { shelf ->
      shelf.copy(
        extras = shelf.extras.map { extra ->
          val topic = links[extra.id] ?: return@map extra
          extra.copy(topicId = topic, unlockAtMs = index.firstCueMs(topic) ?: extra.unlockAtMs)
        },
      )
    }
    val extraByTopic = links.entries.associate { (extra, topic) -> topic to extra }
    return index.copy(entries = index.entries.map { entry -> entry.copy(extraId = entry.extraId ?: extraByTopic[entry.id]) })
  }

  private companion object {
    const val WINDOW_MAX_TOKENS = 16_000L
  }
}

/** Every transcript line of [book], with times in the whole book. */
internal suspend fun TranscriptRepository.bookLines(book: Book): List<BookLine> {
  var offset = 0L
  return book.chapters.flatMap { chapter ->
    val start = offset
    offset += chapter.duration
    cues(chapter.id).orEmpty().map { BookLine(start + it.timestampStartMs, start + it.timestampEndMs, it.text) }
  }
}

internal data class LineWindow(
  val firstIndex: Int,
  val lines: List<BookLine>,
)

/** About 45 minutes of narration per request: enough context to tell a scene from a passing mention. */
internal fun windowsOf(lines: List<BookLine>, windowMs: Long = 45 * 60 * 1000L): List<LineWindow> {
  val windows = mutableListOf<LineWindow>()
  var start = 0
  while (start < lines.size) {
    val limit = lines[start].startMs + windowMs
    var end = start
    while (end < lines.size && lines[end].startMs < limit) end++
    windows += LineWindow(firstIndex = start, lines = lines.subList(start, end))
    start = end
  }
  return windows
}

/**
 * Runs [scan] on [window], and when the reply is cut off, on each half of it instead, so a busy stretch of the
 * book still fits in a reply.
 */
internal suspend fun splittingWhenCutOff(window: LineWindow, scan: suspend (LineWindow) -> Unit) {
  try {
    scan(window)
  } catch (e: ReplyCutOffException) {
    if (window.lines.size < MIN_SPLIT_LINES) throw e
    window.halves().forEach { splittingWhenCutOff(it, scan) }
  }
}

internal fun LineWindow.halves(): List<LineWindow> {
  val middle = lines.size / 2
  return listOf(
    LineWindow(firstIndex = firstIndex, lines = lines.subList(0, middle)),
    LineWindow(firstIndex = firstIndex + middle, lines = lines.subList(middle, lines.size)),
  )
}

private const val MIN_SPLIT_LINES = 40

@Serializable
internal data class WindowAnswer(val entries: List<WindowEntry>)

@Serializable
internal data class WindowEntry(
  val name: String,
  val aliases: List<String>,
  val summary: String,
  val note: String,
  val spans: List<LineSpan>,
)

@Serializable
internal data class LineSpan(
  val from: Int,
  val to: Int,
)

/**
 * Joins the answers of every window into one index: entries matched by name or alias across windows, their line
 * spans turned into times with a short tail, and spans close together merged so a chip doesn't flicker.
 */
internal class SceneIndexBuilder(private val kind: BookKind) {

  private class Building(
    val id: String,
    var name: String,
    val aliases: MutableSet<String>,
    var summary: String,
    val notes: MutableList<SceneNote> = mutableListOf(),
    val spans: MutableList<LongRange> = mutableListOf(),
  )

  private val entries = mutableListOf<Building>()

  /** What earlier windows found, so the next window reuses the same names. */
  fun known(): List<Pair<String, List<String>>> = entries.map { it.name to it.aliases.toList() }

  fun add(window: LineWindow, answer: WindowAnswer) {
    answer.entries.forEach { found ->
      val times = found.spans.mapNotNull { span -> span.toTimes(window) }
      if (times.isEmpty()) return@forEach
      val entry = match(found) ?: Building(
        id = uniqueId(found.name),
        name = found.name.trim(),
        aliases = mutableSetOf(),
        summary = found.summary.trim(),
      ).also { entries += it }
      entry.aliases += found.aliases.map { it.trim() }.filter { it.isNotEmpty() && !it.equals(entry.name, ignoreCase = true) }
      if (entry.summary.isBlank()) entry.summary = found.summary.trim()
      val firstMs = times.minOf { it.first }
      if (found.note.isNotBlank()) entry.notes += SceneNote(fromMs = firstMs, text = found.note.trim())
      entry.spans += times
    }
  }

  fun build(): SceneIndex {
    val kept = entries
      .map { it to mergeSpans(it.spans, gapMs = gapFor(it)) }
      .filter { (_, spans) -> spans.sumOf { it.last - it.first } >= MIN_TOTAL_MS }
      .sortedBy { (_, spans) -> spans.first().first }
      .take(MAX_ENTRIES)
    return SceneIndex(
      kind = kind,
      entries = kept.map { (entry, _) ->
        SceneEntry(id = entry.id, name = entry.name, summary = entry.summary, notes = entry.notes.sortedBy { it.fromMs })
      },
      cues = kept.flatMap { (entry, spans) -> spans.map { SceneCue(entry = entry.id, startMs = it.first, endMs = it.last) } }
        .sortedBy { it.startMs },
    )
  }

  private fun LineSpan.toTimes(window: LineWindow): LongRange? {
    val from = (from - window.firstIndex).coerceIn(window.lines.indices)
    val to = (to - window.firstIndex).coerceIn(window.lines.indices)
    if (to < from) return null
    return window.lines[from].startMs..(window.lines[to].endMs + TAIL_MS)
  }

  private fun match(found: WindowEntry): Building? {
    val names = (found.aliases + found.name).map(::normalized).toSet()
    return entries.firstOrNull { entry -> (entry.aliases + entry.name).any { normalized(it) in names } }
  }

  private fun uniqueId(name: String): String {
    val base = normalized(name).replace(' ', '-').ifEmpty { "entry" }
    var id = base
    var n = 2
    while (entries.any { it.id == id }) id = "$base-${n++}"
    return id
  }

  /** The main character is in most scenes, so their chip holds through short cutaways; others drop sooner. */
  private fun gapFor(entry: Building): Long {
    val total = entry.spans.sumOf { it.last - it.first }
    val busiest = entries.maxOfOrNull { other -> other.spans.sumOf { it.last - it.first } } ?: 0L
    return if (kind == BookKind.Fiction && total * 2 >= busiest) LONG_GAP_MS else SHORT_GAP_MS
  }

  private companion object {
    const val TAIL_MS = 15_000L
    const val SHORT_GAP_MS = 90_000L
    const val LONG_GAP_MS = 300_000L
    const val MIN_TOTAL_MS = 60_000L
    const val MAX_ENTRIES = 60
  }
}

internal fun normalized(name: String): String =
  name.lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("^(the|a|an) "), "").trim().replace(Regex(" +"), " ")

internal fun mergeSpans(spans: List<LongRange>, gapMs: Long): List<LongRange> {
  val merged = mutableListOf<LongRange>()
  spans.sortedBy { it.first }.forEach { span ->
    val last = merged.lastOrNull()
    if (last != null && span.first <= last.last + gapMs) {
      merged[merged.lastIndex] = last.first..maxOf(last.last, span.last)
    } else {
      merged += span
    }
  }
  return merged
}

internal fun sceneSystem(kind: BookKind): String = if (kind == BookKind.Fiction) {
  "You index an audiobook's characters for chips on the player that show who is in the scene. You get numbered " +
    "transcript lines. List the characters who are present in the scene: speaking, acting, or being directly " +
    "addressed. Leave out characters who are only mentioned in passing or remembered. For each, give spans of " +
    "line numbers where they are in the scene (one span per stretch, from the first line to the last). Reuse the " +
    "exact name of a known character when it is the same person. summary is one line on who they are as first " +
    "introduced. note is one sentence on what this part reveals about them, or empty. Never mention events from " +
    "outside these lines."
} else {
  "You index a non-fiction audiobook's key topics for chips on the player that show what is being discussed. You " +
    "get numbered transcript lines. List the main ideas, concepts, methods or people being explained or discussed " +
    "at length; leave out passing mentions. Name topics in 1 to 4 words, the way the author does. For each, give " +
    "spans of line numbers where it is being discussed. Reuse the exact name of a known topic when it is the same " +
    "one. summary is one plain line defining it. note is one sentence on the key point made about it here, or empty."
}

internal fun windowPrompt(window: LineWindow, known: List<Pair<String, List<String>>>): String = buildString {
  if (known.isNotEmpty()) {
    append("Known so far: ")
    append(known.joinToString("; ") { (name, aliases) -> if (aliases.isEmpty()) name else "$name (${aliases.joinToString()})" })
    append("\n\n")
  }
  append("Transcript lines:\n")
  window.lines.forEachIndexed { i, line -> append("[${window.firstIndex + i}] ${line.text}\n") }
}

internal val WINDOW_SCHEMA = Schema.obj(
  "entries" to Schema.array(
    Schema.obj(
      "name" to Schema.string(),
      "aliases" to Schema.array(Schema.string()),
      "summary" to Schema.string(),
      "note" to Schema.string(),
      "spans" to Schema.array(Schema.obj("from" to Schema.integer(), "to" to Schema.integer())),
    ),
  ),
)

@Serializable
internal data class LinkAnswer(val links: List<Link>)

@Serializable
internal data class Link(
  val extraId: String,
  val topicId: String,
)

internal const val LINK_SYSTEM = "Match companion Extras of an audiobook to the topic or character each is " +
  "mainly about. Leave out Extras that are about the whole book. Use only the ids given."

internal fun linkPrompt(index: SceneIndex, extras: List<Pair<String, String>>): String = buildString {
  append("Topics:\n")
  index.entries.forEach { append("- ${it.id}: ${it.name}\n") }
  append("\nExtras:\n")
  extras.forEach { (id, text) -> append("- $id: $text\n") }
}

internal val LINK_SCHEMA = Schema.obj(
  "links" to Schema.array(Schema.obj("extraId" to Schema.string(), "topicId" to Schema.string())),
)
