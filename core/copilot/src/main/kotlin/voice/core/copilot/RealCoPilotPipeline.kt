package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.TranscriptRepository
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AppScope::class)
@Inject
class RealCoPilotPipeline(
  private val bookRepository: BookRepository,
  private val transcriptRepository: TranscriptRepository,
  private val jevRouter: JevRouter,
  private val geminiClient: GeminiClient,
  private val coPilotRepository: CoPilotRepository,
) : CoPilotPipeline {

  override suspend fun ask(bookId: BookId, question: String): String {
    val book = bookRepository.get(bookId) ?: return FALLBACK_ANSWER
    val transcript = if (jevRouter.needsTranscriptContext(question)) {
      transcriptRepository.textForPrecedingWindow(book, ASK_TRANSCRIPT_WINDOW_MS)
    } else {
      null
    }
    return runCatching {
      geminiClient.ask(
        systemPrompt = askSystemPrompt(book),
        userPrompt = userQuestionPrompt(question, transcript),
      )
    }.getOrElse { e ->
      Logger.w(e, "CoPilot ask() failed")
      FALLBACK_ANSWER
    }
  }

  override suspend fun autoIdentify(bookId: BookId): String {
    val book = bookRepository.get(bookId) ?: return FALLBACK_ANSWER
    val last60Seconds = transcriptRepository.textForPrecedingWindow(book, AUTO_IDENTIFY_RECENT_WINDOW_MS)
      ?: return FALLBACK_NO_TRANSCRIPT
    val last30Minutes = transcriptRepository.textForPrecedingWindow(book, AUTO_IDENTIFY_CONTEXT_WINDOW_MS)
    val task = "Explain what is happening right now in the book, based specifically on the last 60 seconds " +
      "provided below. Use the last 30 minutes of context only to understand who or what is involved - don't " +
      "summarize that older context itself, focus your explanation on the current moment."
    return runCatching {
      geminiClient.ask(
        systemPrompt = spokenAnswerSystemPrompt(book, task),
        userPrompt = autoIdentifyPrompt(last30Minutes, last60Seconds),
      )
    }.getOrElse { e ->
      Logger.w(e, "CoPilot autoIdentify() failed")
      FALLBACK_ANSWER
    }
  }

  override suspend fun catchMeUp(bookId: BookId): String {
    val book = bookRepository.get(bookId) ?: return FALLBACK_ANSWER
    val transcript = transcriptRepository.textForPrecedingWindow(book, CATCH_UP_WINDOW_MS)
      ?: return FALLBACK_NO_TRANSCRIPT
    val task = "Give a spoken summary of what happened in the last 30 minutes of the book, shown below. " +
      "Start your answer with the exact words \"In the last 30 minutes,\" and continue directly from there."
    return runCatching {
      geminiClient.ask(spokenAnswerSystemPrompt(book, task), transcriptOnlyPrompt(transcript))
    }.getOrElse { e ->
      Logger.w(e, "CoPilot catchMeUp() failed")
      FALLBACK_ANSWER
    }
  }

  override suspend fun snip(bookId: BookId) {
    coPilotRepository.emitSnipEvent(SnipEvent.Started)
    val book = bookRepository.get(bookId) ?: return snipFailed()
    val transcript = transcriptRepository.textForPrecedingWindow(book, SNIP_TRANSCRIPT_WINDOW_MS) ?: return snipFailed()
    val highlight = runCatching {
      geminiClient.ask(
        systemPrompt = snipHighlightSystemPrompt(book),
        userPrompt = transcriptOnlyPrompt(transcript),
      )
    }.getOrElse { e ->
      Logger.w(e, "CoPilot snip() failed")
      return snipFailed()
    }
    // imagePath is left null for now: real comic-panel image generation is Phase 6's job,
    // via the Ideogram pipeline described in the PRD (character reference images, scene
    // planning, etc). An earlier attempt called Gemini's own image-generation endpoint
    // directly here, but it was never a validated API integration and confirmed on-device
    // that it doesn't produce an image - removed rather than leave a silently-broken call
    // in place. snipImagePrompt() in CoPilotPrompts.kt is kept: Phase 6 can reuse it (or a
    // variant) as the actual Ideogram prompt once that pipeline exists.
    coPilotRepository.addMessage(
      bookId,
      CoPilotMessage(
        id = UUID.randomUUID().toString(),
        role = CoPilotMessage.Role.CoPilot,
        text = highlight,
        timestampMs = System.currentTimeMillis(),
        isVisualPriority = true,
        snipChapterId = book.currentChapter.id,
        snipPositionInChapterMs = book.content.positionInChapter,
      ),
    )
    coPilotRepository.emitSnipEvent(SnipEvent.Ready(highlight))
  }

  private fun snipFailed() = coPilotRepository.emitSnipEvent(SnipEvent.Failed)

  private companion object {
    const val FALLBACK_ANSWER = "Sorry, I couldn't get an answer just now."
    const val FALLBACK_NO_TRANSCRIPT = "I don't have a transcript for this book yet, so I can't answer that."
    val ASK_TRANSCRIPT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val AUTO_IDENTIFY_RECENT_WINDOW_MS = 60.seconds.inWholeMilliseconds
    val AUTO_IDENTIFY_CONTEXT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val CATCH_UP_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val SNIP_TRANSCRIPT_WINDOW_MS = 3.minutes.inWholeMilliseconds
  }
}
