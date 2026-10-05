package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import voice.core.copilot.frame.FrameRequest
import voice.core.copilot.frame.SnipFrameRunner
import voice.core.data.BookId
import voice.core.data.CoPilotVoiceSettings
import voice.core.data.store.CoPilotVoiceSettingsStore
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.TranscriptRepository
import java.util.UUID
import kotlin.random.Random
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
  @CoPilotVoiceSettingsStore
  private val voiceSettingsStore: DataStore<CoPilotVoiceSettings>,
  private val snipFrameRunner: SnipFrameRunner,
) : CoPilotPipeline {

  internal var random: Random = Random.Default

  private suspend fun currentStyle(budget: WordBudget, flavor: SystemFlavor? = null): AnswerStyle =
    answerStyle(voiceSettingsStore.data.first(), budget, flavor ?: pickSystemFlavor(random))

  private suspend fun fallbackAnswer(): String =
    if (voiceSettingsStore.data.first().useSystemAiVoice) SYSTEM_FALLBACK_ANSWER else FALLBACK_ANSWER

  private suspend fun fallbackNoTranscript(): String =
    if (voiceSettingsStore.data.first().useSystemAiVoice) SYSTEM_FALLBACK_NO_TRANSCRIPT else FALLBACK_NO_TRANSCRIPT

  override suspend fun failureMessage(): String =
    if (voiceSettingsStore.data.first().useSystemAiVoice) SYSTEM_FALLBACK_ANSWER else GENERIC_FAILURE

  /** What a spoken/typed action needs: either an answer already known, or the prompts to send to Gemini. */
  private sealed interface Plan {
    data class Direct(val answer: String) : Plan
    data class Gemini(val systemPrompt: String, val userPrompt: String) : Plan
  }

  private suspend fun askPlan(bookId: BookId, question: String): Plan {
    val book = bookRepository.get(bookId) ?: return Plan.Direct(fallbackAnswer())
    val transcript = if (jevRouter.needsTranscriptContext(question)) {
      transcriptRepository.textForPrecedingWindow(book, ASK_TRANSCRIPT_WINDOW_MS)
    } else {
      null
    }
    return Plan.Gemini(
      askSystemPrompt(book, currentStyle(WordBudget.Answer)),
      userQuestionPrompt(question, transcript),
    )
  }

  private suspend fun autoIdentifyPlan(bookId: BookId): Plan {
    val book = bookRepository.get(bookId) ?: return Plan.Direct(fallbackAnswer())
    val last60Seconds = transcriptRepository.textForPrecedingWindow(book, AUTO_IDENTIFY_RECENT_WINDOW_MS)
      ?: return Plan.Direct(fallbackNoTranscript())
    val last30Minutes = transcriptRepository.textForPrecedingWindow(book, AUTO_IDENTIFY_CONTEXT_WINDOW_MS)
    val task = "Explain what is happening right now in the book, based specifically on the last 60 seconds " +
      "provided below. Use the last 30 minutes of context only to understand who or what is involved - don't " +
      "summarize that older context itself, focus your explanation on the current moment."
    return Plan.Gemini(
      spokenAnswerSystemPrompt(book, task, currentStyle(WordBudget.Answer)),
      autoIdentifyPrompt(last30Minutes, last60Seconds),
    )
  }

  private suspend fun catchMeUpPlan(bookId: BookId): Plan {
    val book = bookRepository.get(bookId) ?: return Plan.Direct(fallbackAnswer())
    val transcript = transcriptRepository.textForPrecedingWindow(book, CATCH_UP_WINDOW_MS)
      ?: return Plan.Direct(fallbackNoTranscript())
    val task = "Give a spoken summary of what happened in the last 30 minutes of the book, shown below. " +
      "Start your answer with the exact words \"In the last 30 minutes,\" and continue directly from there."
    return Plan.Gemini(
      // The recap must open with an exact phrase, so it never takes a banner flavor.
      spokenAnswerSystemPrompt(book, task, currentStyle(WordBudget.Recap, SystemFlavor.Plain)),
      transcriptOnlyPrompt(transcript),
    )
  }

  private suspend fun answer(plan: Plan, label: String): String = when (plan) {
    is Plan.Direct -> plan.answer
    is Plan.Gemini -> runCatching { geminiClient.ask(plan.systemPrompt, plan.userPrompt) }.getOrElse { e ->
      Logger.w(e, "CoPilot $label() failed")
      fallbackAnswer()
    }
  }

  private fun stream(label: String, plan: suspend () -> Plan): Flow<String> = flow {
    Logger.d("CoPilot $label stream: pipeline start")
    when (val resolved = plan()) {
      is Plan.Direct -> emit(resolved.answer)
      is Plan.Gemini -> emitAll(geminiClient.askStream(resolved.systemPrompt, resolved.userPrompt))
    }
  }

  override suspend fun warmUp(bookId: BookId) {
    // At app start the library may not have the book yet, so give it a few tries.
    var book = bookRepository.get(bookId)
    var attempts = 1
    while (book == null && attempts < WARM_UP_ATTEMPTS) {
      delay(WARM_UP_RETRY_MS)
      book = bookRepository.get(bookId)
      attempts++
    }
    if (book == null) {
      Logger.d("CoPilot warm-up: book not available")
      return
    }
    val loaded = transcriptRepository.textForPrecedingWindow(book, 1)
    Logger.d("CoPilot warm-up done (attempt $attempts), transcript ${if (loaded == null) "missing" else "loaded"}")
  }

  override suspend fun ask(bookId: BookId, question: String): String = answer(askPlan(bookId, question), "ask")

  override suspend fun autoIdentify(bookId: BookId): String = answer(autoIdentifyPlan(bookId), "autoIdentify")

  override suspend fun catchMeUp(bookId: BookId): String = answer(catchMeUpPlan(bookId), "catchMeUp")

  override fun askStream(bookId: BookId, question: String): Flow<String> = stream("ask") { askPlan(bookId, question) }

  override fun autoIdentifyStream(bookId: BookId): Flow<String> = stream("autoIdentify") { autoIdentifyPlan(bookId) }

  override fun catchMeUpStream(bookId: BookId): Flow<String> = stream("catchMeUp") { catchMeUpPlan(bookId) }

  override suspend fun snip(bookId: BookId) {
    val drawFrame = snipFrameRunner.isAvailable()
    coPilotRepository.emitSnipEvent(SnipEvent.Started(drawFrame))
    val book = bookRepository.get(bookId) ?: return snipFailed()
    val transcript = transcriptRepository.textForPrecedingWindow(book, SNIP_TRANSCRIPT_WINDOW_MS) ?: return snipFailed()
    val highlight = runCatching {
      geminiClient.ask(
        systemPrompt = snipHighlightSystemPrompt(book, voiceSettingsStore.data.first(), pickSnipFlavor(random)),
        userPrompt = transcriptOnlyPrompt(transcript),
      )
    }.getOrElse { e ->
      Logger.w(e, "CoPilot snip() failed")
      return snipFailed()
    }
    val messageId = UUID.randomUUID().toString()
    coPilotRepository.addMessage(
      bookId,
      CoPilotMessage(
        id = messageId,
        role = CoPilotMessage.Role.CoPilot,
        text = highlight,
        timestampMs = System.currentTimeMillis(),
        isVisualPriority = true,
        isGeneratingImage = drawFrame,
        snipChapterId = book.currentChapter.id,
        snipPositionInChapterMs = book.content.positionInChapter,
      ),
    )
    coPilotRepository.emitSnipEvent(SnipEvent.Ready(highlight, messageId))
    if (drawFrame) {
      snipFrameRunner.start(
        bookId,
        messageId,
        FrameRequest(book = book, highlight = highlight, transcript = transcript, positionMs = book.position),
      )
    }
  }

  private fun snipFailed() = coPilotRepository.emitSnipEvent(SnipEvent.Failed)

  private companion object {
    const val WARM_UP_ATTEMPTS = 8
    const val WARM_UP_RETRY_MS = 2_000L
    const val FALLBACK_ANSWER = "Sorry, I couldn't get an answer just now."
    const val FALLBACK_NO_TRANSCRIPT = "I don't have a transcript for this book yet, so I can't answer that."
    const val GENERIC_FAILURE = "Sorry, something went wrong."
    const val SYSTEM_FALLBACK_ANSWER = "System message. Something went wrong on our end. Try again in a moment, crawler."
    const val SYSTEM_FALLBACK_NO_TRANSCRIPT =
      "Warning! No transcript on file for this book. Nothing to look up. Good luck."
    val ASK_TRANSCRIPT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val AUTO_IDENTIFY_RECENT_WINDOW_MS = 60.seconds.inWholeMilliseconds
    val AUTO_IDENTIFY_CONTEXT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val CATCH_UP_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val SNIP_TRANSCRIPT_WINDOW_MS = 3.minutes.inWholeMilliseconds
  }
}
