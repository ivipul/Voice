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
  private val sessionTracker: CoPilotSessionTracker,
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
    sessionTracker.snapshotIfNeeded(bookId)

    return if (sessionTracker.isWithinFirst30SecondsOfSession()) {
      val transcript = transcriptRepository.textForWindow(
        book = book,
        endPositionMs = sessionTracker.previousSessionEndPositionMs(bookId),
        windowMs = CATCH_UP_PREVIOUS_SESSION_WINDOW_MS,
      ) ?: return FALLBACK_NO_TRANSCRIPT
      val task = "Give a 3-sentence spoken recap of what happened in the final 5 minutes of the user's " +
        "previous listening session, shown below."
      runCatching {
        geminiClient.ask(spokenAnswerSystemPrompt(book, task), transcriptOnlyPrompt(transcript))
      }.getOrElse { e ->
        Logger.w(e, "CoPilot catchMeUp() (previous session) failed")
        FALLBACK_ANSWER
      }
    } else {
      val transcript = transcriptRepository.textForPrecedingWindow(book, CATCH_UP_CURRENT_SESSION_WINDOW_MS)
        ?: return FALLBACK_NO_TRANSCRIPT
      val task = "Give a spoken summary of what happened in the last 30 minutes of the book, shown below."
      runCatching {
        geminiClient.ask(spokenAnswerSystemPrompt(book, task), transcriptOnlyPrompt(transcript))
      }.getOrElse { e ->
        Logger.w(e, "CoPilot catchMeUp() (current session) failed")
        FALLBACK_ANSWER
      }
    }
  }

  override suspend fun snip(bookId: BookId) {
    val book = bookRepository.get(bookId) ?: return
    val transcript = transcriptRepository.textForPrecedingWindow(book, SNIP_TRANSCRIPT_WINDOW_MS) ?: return
    val summary = runCatching {
      geminiClient.ask(
        systemPrompt = snipSystemPrompt(book),
        userPrompt = transcriptOnlyPrompt(transcript),
      )
    }.getOrElse { e ->
      Logger.w(e, "CoPilot snip() failed")
      return
    }
    coPilotRepository.addMessage(
      bookId,
      CoPilotMessage(
        id = UUID.randomUUID().toString(),
        role = CoPilotMessage.Role.CoPilot,
        text = summary,
        timestampMs = System.currentTimeMillis(),
        isVisualPriority = true,
      ),
    )
  }

  private companion object {
    const val FALLBACK_ANSWER = "Sorry, I couldn't get an answer just now."
    const val FALLBACK_NO_TRANSCRIPT = "I don't have a transcript for this book yet, so I can't answer that."
    val ASK_TRANSCRIPT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val AUTO_IDENTIFY_RECENT_WINDOW_MS = 60.seconds.inWholeMilliseconds
    val AUTO_IDENTIFY_CONTEXT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val CATCH_UP_PREVIOUS_SESSION_WINDOW_MS = 5.minutes.inWholeMilliseconds
    val CATCH_UP_CURRENT_SESSION_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val SNIP_TRANSCRIPT_WINDOW_MS = 3.minutes.inWholeMilliseconds
  }
}
