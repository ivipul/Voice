package voice.core.copilot

import dev.zacsweers.metro.Inject
import voice.core.companion.BookMaterials
import voice.core.companion.CompanionStores
import voice.core.companion.MaterialSection
import voice.core.companion.activeAt
import voice.core.data.Book
import voice.core.transcript.BookSection
import voice.core.transcript.TranscriptCue
import voice.core.transcript.TranscriptRepository
import voice.core.transcript.currentSection
import voice.core.transcript.sections
import kotlin.time.Duration.Companion.minutes

/** What goes to Claude with a question: the excerpts in the user turn, and a cached block of background. */
data class AskContext(
  /** Labelled transcript excerpts, newest last. Empty when there is no transcript yet. */
  val excerpts: List<Excerpt>,
  /** Summaries of the chapters already finished, sent as the cached start of the instructions. */
  val background: String? = null,
  val webSearch: Boolean = false,
) {

  data class Excerpt(
    val label: String,
    val text: String,
  )
}

/** Gathers just enough of the book for each route, and never anything past the listener's position. */
@Inject
class AskContextBuilder(
  private val transcriptRepository: TranscriptRepository,
  private val stores: CompanionStores,
) {

  suspend fun contextFor(book: Book, decision: AskDecision, question: String = ""): AskContext {
    val context = when (decision.route) {
      AskRoute.Recent, AskRoute.BuildExtra -> AskContext(listOfNotNull(recent(book, decision.recentMinutes)))
      AskRoute.Web -> AskContext(listOfNotNull(recent(book, decision.recentMinutes)), webSearch = true)
      AskRoute.Chapter -> AskContext(listOfNotNull(chapter(book) ?: recent(book, BOOK_RECENT_MINUTES)))
      AskRoute.Book -> bookContext(book, decision.searchTerms)
    }
    val material = relevantSections(stores.materials.get(book.id), book.position, question + " " + decision.searchTerms.joinToString(" "))
      .map { AskContext.Excerpt("From the PDF that comes with the book, \"${it.title}\" (pages ${it.pages})", it.extract) }
    return context.copy(excerpts = material + context.excerpts)
  }

  private suspend fun recent(book: Book, minutes: Int): AskContext.Excerpt? =
    transcriptRepository.textForPrecedingWindow(book, minutes.minutes.inWholeMilliseconds)
      ?.let { AskContext.Excerpt("The last $minutes minutes", it) }

  private suspend fun chapter(book: Book): AskContext.Excerpt? {
    val text = transcriptRepository.chapterSoFar(book) ?: return null
    return AskContext.Excerpt("This chapter (\"${book.currentSection().name}\") so far", text.takeLast(MAX_CHAPTER_CHARS))
  }

  private suspend fun bookContext(book: Book, terms: List<String>): AskContext {
    val current = book.currentSection()
    val finished = book.sections().take(current.index)
    val recent = recent(book, BOOK_RECENT_MINUTES)
    val heard = finished.mapNotNull { section -> transcriptRepository.cues(section.chapterId)?.let { section to it.within(section) } }
    val heardChars = heard.sumOf { (_, cues) -> cues.sumOf { it.text.length + 1 } }
    // A short book fits whole, which beats any summary.
    if (heardChars <= WHOLE_BOOK_CHARS) {
      val earlier = heard.joinToString("\n\n") { (section, cues) -> "${section.name}: ${cues.joinToString(" ") { it.text.trim() }}" }
      val excerpts = listOfNotNull(
        earlier.takeIf { it.isNotBlank() }?.let { AskContext.Excerpt("Everything heard before this chapter", it) },
        chapter(book) ?: recent,
      )
      return AskContext(excerpts)
    }
    val summaries = stores.summaries.get(book.id).byKey
    val background = summariesBlock(book.content.name, finished, summaries)
    val passages = passagesFor(heard, terms, MAX_PASSAGE_CHARS)
    val excerpts = listOfNotNull(
      passages.takeIf { it.isNotBlank() }?.let { AskContext.Excerpt("Earlier passages that mention ${terms.joinToString()}", it) },
      recent,
    )
    return AskContext(excerpts, background = background)
  }

  private companion object {
    const val BOOK_RECENT_MINUTES = 10
    const val MAX_CHAPTER_CHARS = 120_000
    const val WHOLE_BOOK_CHARS = 120_000
    const val MAX_PASSAGE_CHARS = 24_000
  }
}

private fun List<TranscriptCue>.within(section: BookSection): List<TranscriptCue> =
  filter { it.timestampStartMs >= section.startMs && it.timestampStartMs <= section.endMs }

/**
 * The PDF sections an answer can use: the ones the narration is referring to right now, then the ones the question
 * names by title or key term. At most [max], so the PDF never crowds out the book.
 */
internal fun relevantSections(
  materials: BookMaterials,
  positionMs: Long,
  question: String,
  max: Int = 2,
): List<MaterialSection> {
  val words = question.lowercase()
  val named = materials.sections.filter { section ->
    (listOf(section.title) + section.keyTerms).any { term -> term.length >= 4 && term.lowercase() in words }
  }
  return (materials.activeAt(positionMs) + named).distinctBy { it.id }.filter { it.extract.isNotBlank() }.take(max)
}

/** The summaries of [finished] chapters, in order, or null when none are written yet. */
internal fun summariesBlock(
  title: String,
  finished: List<BookSection>,
  summaries: Map<String, String>,
): String? {
  val lines = finished.mapNotNull { section -> summaries[section.key]?.let { "${section.name}: $it" } }
  if (lines.isEmpty()) return null
  return "Background on the audiobook \"$title\": summaries of the chapters the listener has finished, in " +
    "order.\n\n" + lines.joinToString("\n")
}

/**
 * The passages of [heard] that mention any of [terms], a few lines either side of each mention, in book order. When
 * there are more than fit in [maxChars], the ones with the most mentions win.
 */
internal fun passagesFor(
  heard: List<Pair<BookSection, List<TranscriptCue>>>,
  terms: List<String>,
  maxChars: Int,
): String {
  if (terms.isEmpty()) return ""
  val patterns = terms.map { Regex("\\b${Regex.escape(it)}", RegexOption.IGNORE_CASE) }
  data class Passage(val order: Int, val section: BookSection, val text: String, val hits: Int)
  val passages = mutableListOf<Passage>()
  heard.forEach { (section, cues) ->
    val hitLines = cues.indices.filter { i -> patterns.any { it.containsMatchIn(cues[i].text) } }
    mergedRanges(hitLines, PASSAGE_CONTEXT_LINES, cues.size).forEach { range ->
      val text = cues.subList(range.first, range.last + 1).joinToString(" ") { it.text.trim() }
      val hits = hitLines.count { it in range }
      passages += Passage(passages.size, section, text, hits)
    }
  }
  var budget = maxChars
  val chosen = passages.sortedByDescending { it.hits }.filter { passage ->
    (passage.text.length <= budget).also { fits -> if (fits) budget -= passage.text.length }
  }
  return chosen.sortedBy { it.order }.joinToString("\n\n") { "${it.section.name}: …${it.text}…" }
}

/** Ranges of [around] lines either side of each of [lines], merged where they touch. */
internal fun mergedRanges(lines: List<Int>, around: Int, size: Int): List<IntRange> {
  val ranges = mutableListOf<IntRange>()
  lines.forEach { line ->
    val range = (line - around).coerceAtLeast(0)..(line + around).coerceAtMost(size - 1)
    val last = ranges.lastOrNull()
    if (last != null && range.first <= last.last + 1) {
      ranges[ranges.lastIndex] = last.first..maxOf(last.last, range.last)
    } else {
      ranges += range
    }
  }
  return ranges
}

private const val PASSAGE_CONTEXT_LINES = 3
