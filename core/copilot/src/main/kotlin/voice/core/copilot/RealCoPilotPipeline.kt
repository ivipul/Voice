package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import voice.core.ai.ClaudeClient
import voice.core.ai.MissingApiKeyException
import voice.core.companion.BookProfile
import voice.core.companion.CompanionStores
import voice.core.companion.StepState
import voice.core.companion.request.ExtraRequests
import voice.core.data.Book
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
  private val claudeClient: ClaudeClient,
  private val coPilotRepository: CoPilotRepository,
  private val stores: CompanionStores,
  private val snipPictureRunner: SnipPictureRunner,
  private val extraRequests: ExtraRequests,
  private val askRouter: AskRouter,
  private val askContextBuilder: AskContextBuilder,
) : CoPilotPipeline {

  override suspend fun failureMessage(): String =
    if (claudeClient.isConfigured()) GENERIC_FAILURE else NO_KEY

  /** What a spoken or typed action needs: either an answer already known, or the prompts to send to Claude. */
  private sealed interface Plan {
    data class Direct(val answer: String) : Plan
    data class Claude(
      val systemPrompt: String,
      val userPrompt: String,
      val cachedPrefix: String? = null,
      val webSearches: Long = 0,
    ) : Plan
  }

  private suspend fun profileOf(book: Book): BookProfile? = stores.profile.get(book.id).takeIf { it.summary.isNotBlank() }

  /**
   * A yes or no to our last question settles first. Otherwise the router picks where the answer comes from, and
   * only that much of the book goes along: a few minutes, the chapter, or summaries plus matching passages.
   */
  private suspend fun askPlan(bookId: BookId, question: String): Plan {
    val book = bookRepository.get(bookId) ?: return Plan.Direct(FALLBACK_ANSWER)
    extraRequests.replyToPending(bookId, question)?.let { return Plan.Direct(it) }
    val profile = profileOf(book)
    val lastSeconds = transcriptRepository.textForPrecedingWindow(book, ROUTER_RECENT_WINDOW_MS)
    var decision = askRouter.route(book, profile, question, lastSeconds)
    if (decision.route == AskRoute.BuildExtra) {
      val recent = transcriptRepository.textForPrecedingWindow(book, EXTRA_REQUEST_WINDOW_MS)
      extraRequests.request(bookId, question, book.position, recent)?.let { return Plan.Direct(it) }
      decision = AskDecision(AskRoute.Recent)
    }
    val context = askContextBuilder.contextFor(book, decision)
    return Plan.Claude(
      systemPrompt = askSystemPrompt(book, profile, context.webSearch),
      userPrompt = userQuestionPrompt(question, context.excerpts),
      cachedPrefix = context.background,
      webSearches = if (context.webSearch) WEB_SEARCHES else 0,
    )
  }

  private suspend fun autoIdentifyPlan(bookId: BookId): Plan {
    val book = bookRepository.get(bookId) ?: return Plan.Direct(FALLBACK_ANSWER)
    val last60Seconds = transcriptRepository.textForPrecedingWindow(book, AUTO_IDENTIFY_RECENT_WINDOW_MS)
      ?: return Plan.Direct(noTranscriptAnswer(book))
    val last30Minutes = transcriptRepository.textForPrecedingWindow(book, AUTO_IDENTIFY_CONTEXT_WINDOW_MS)
    return Plan.Claude(autoIdentifySystemPrompt(book, profileOf(book)), autoIdentifyPrompt(last30Minutes, last60Seconds))
  }

  private suspend fun catchMeUpPlan(bookId: BookId): Plan {
    val book = bookRepository.get(bookId) ?: return Plan.Direct(FALLBACK_ANSWER)
    val transcript = transcriptRepository.textForPrecedingWindow(book, CATCH_UP_WINDOW_MS)
      ?: return Plan.Direct(noTranscriptAnswer(book))
    return Plan.Claude(catchUpSystemPrompt(book, profileOf(book)), transcriptOnlyPrompt(transcript))
  }

  private suspend fun noTranscriptAnswer(book: Book): String {
    val transcribing = stores.setup.get(book.id).transcription.state == StepState.Running
    return if (transcribing) STILL_TRANSCRIBING else FALLBACK_NO_TRANSCRIPT
  }

  private suspend fun answer(plan: Plan, label: String): String = when (plan) {
    is Plan.Direct -> plan.answer
    is Plan.Claude -> try {
      claudeClient.ask(
        system = plan.systemPrompt,
        prompt = plan.userPrompt,
        cachedPrefix = plan.cachedPrefix,
        webSearches = plan.webSearches,
      )
    } catch (e: MissingApiKeyException) {
      NO_KEY
    } catch (e: Exception) {
      Logger.w(e, "CoPilot $label() failed")
      FALLBACK_ANSWER
    }
  }

  private fun stream(label: String, plan: suspend () -> Plan): Flow<String> = flow {
    Logger.d("CoPilot $label stream: pipeline start")
    if (!claudeClient.isConfigured()) {
      emit(NO_KEY)
      return@flow
    }
    when (val resolved = plan()) {
      is Plan.Direct -> emit(resolved.answer)
      is Plan.Claude -> emitAll(
        claudeClient.askStream(
          system = resolved.systemPrompt,
          prompt = resolved.userPrompt,
          cachedPrefix = resolved.cachedPrefix,
          webSearches = resolved.webSearches,
        ),
      )
    }
  }

  override fun awaitingReply(bookId: BookId): Boolean = extraRequests.awaitingReply(bookId)

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
    val loaded = transcriptRepository.hasTranscript(book)
    Logger.d("CoPilot warm-up done (attempt $attempts), transcript ${if (loaded) "loaded" else "missing"}")
  }

  override suspend fun ask(bookId: BookId, question: String): String = answer(askPlan(bookId, question), "ask")

  override suspend fun autoIdentify(bookId: BookId): String = answer(autoIdentifyPlan(bookId), "autoIdentify")

  override suspend fun catchMeUp(bookId: BookId): String = answer(catchMeUpPlan(bookId), "catchMeUp")

  override fun askStream(bookId: BookId, question: String): Flow<String> = stream("ask") { askPlan(bookId, question) }

  override fun autoIdentifyStream(bookId: BookId): Flow<String> = stream("autoIdentify") { autoIdentifyPlan(bookId) }

  override fun catchMeUpStream(bookId: BookId): Flow<String> = stream("catchMeUp") { catchMeUpPlan(bookId) }

  override suspend fun snip(bookId: BookId) {
    val style = snipPictureRunner.styleFor(bookId)
    coPilotRepository.emitSnipEvent(SnipEvent.Started(drawsPicture = style != null))
    val book = bookRepository.get(bookId) ?: return snipFailed()
    val position = book.content.positionInChapter
    val before = transcriptRepository.textForWindow(book, endPositionMs = position, windowMs = SNIP_BEFORE_MS)
      ?: return snipFailed()
    val after = transcriptRepository.textForWindow(book, endPositionMs = position + SNIP_AFTER_MS, windowMs = SNIP_AFTER_MS)
    val highlight = try {
      claudeClient.ask(snipSystemPrompt(book, profileOf(book)), snipPrompt(before, after))
    } catch (e: Exception) {
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
        isGeneratingImage = style != null,
        snipChapterId = book.currentChapter.id,
        snipPositionInChapterMs = position,
      ),
    )
    coPilotRepository.emitSnipEvent(SnipEvent.Ready(highlight, messageId))
    if (style != null) snipPictureRunner.start(bookId, messageId, highlight, style)
  }

  private fun snipFailed() = coPilotRepository.emitSnipEvent(SnipEvent.Failed)

  private companion object {
    const val WARM_UP_ATTEMPTS = 8
    const val WARM_UP_RETRY_MS = 2_000L
    const val FALLBACK_ANSWER = "Sorry, I couldn't get an answer just now."
    const val FALLBACK_NO_TRANSCRIPT = "I don't have a transcript for this book yet, so I can't answer that."
    const val STILL_TRANSCRIBING = "This book is still being transcribed. I can answer once that part is done."
    const val GENERIC_FAILURE = "Sorry, something went wrong."
    const val NO_KEY = "Add your Claude API key in Settings first, then try again."
    const val WEB_SEARCHES = 2L
    val ROUTER_RECENT_WINDOW_MS = 30.seconds.inWholeMilliseconds
    val AUTO_IDENTIFY_RECENT_WINDOW_MS = 60.seconds.inWholeMilliseconds
    val AUTO_IDENTIFY_CONTEXT_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val CATCH_UP_WINDOW_MS = 30.minutes.inWholeMilliseconds
    val SNIP_BEFORE_MS = 3.minutes.inWholeMilliseconds
    val EXTRA_REQUEST_WINDOW_MS = 3.minutes.inWholeMilliseconds
    val SNIP_AFTER_MS = 30.seconds.inWholeMilliseconds
  }
}
