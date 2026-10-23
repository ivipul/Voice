package voice.core.companion.setup

import dev.zacsweers.metro.Inject
import voice.core.ai.ClaudeClient
import voice.core.ai.ClaudeModels
import voice.core.companion.CompanionStores
import voice.core.companion.StepState
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.TranscriptRepository
import voice.core.transcript.sections

/**
 * Writes a short summary of every chapter once the book is transcribed, with the small model. Ask sends the
 * summaries of the chapters the listener finished instead of the whole transcript, which keeps questions about the
 * whole book fast and cheap. Each summary is saved as soon as it exists, so a stopped run picks up where it left off.
 */
@Inject
class ChapterSummarizer(
  private val bookRepository: BookRepository,
  private val transcriptRepository: TranscriptRepository,
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
) {

  suspend fun summarize(bookId: BookId) {
    stores.setup.update(bookId) { it.copy(summaries = StepState.Running) }
    try {
      val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
      val profile = stores.profile.get(bookId)
      val done = stores.summaries.get(bookId).byKey
      book.sections().filter { it.key !in done }.forEach { section ->
        val text = transcriptRepository.sectionText(section) ?: return@forEach
        val summary = claude.ask(
          system = SUMMARY_SYSTEM,
          prompt = profileText(profile) + "\nChapter \"${section.name}\":\n\"\"\"\n${text.take(MAX_CHAPTER_CHARS)}\n\"\"\"",
          model = ClaudeModels.SCAN,
          maxTokens = SUMMARY_MAX_TOKENS,
        )
        stores.summaries.update(bookId) { it.copy(byKey = it.byKey + (section.key to summary.trim())) }
      }
      stores.setup.update(bookId) { it.copy(summaries = StepState.Done) }
    } catch (e: Exception) {
      Logger.w(e, "Chapter summaries failed for $bookId")
      stores.setup.update(bookId) { it.copy(summaries = StepState.Failed) }
      throw e
    }
  }

  private companion object {
    const val MAX_CHAPTER_CHARS = 400_000
    const val SUMMARY_MAX_TOKENS = 400L
  }
}

internal const val SUMMARY_SYSTEM = "You summarize one chapter of an audiobook from its transcript, for an app " +
  "that answers the listener's questions later. In at most 80 words, say what happens or what is explained: the " +
  "people, places, ideas and names that come up, so a later question can be matched to this chapter. Only this " +
  "chapter; nothing from later in the book. Plain text, no lists."
