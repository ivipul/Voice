package voice.core.playback.session

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import voice.core.ai.ClaudeClient
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.copilot.tts.SpeechChunker
import voice.core.data.BookId
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.playback.R
import voice.core.playback.di.PlaybackScope
import voice.core.playback.speech.PhoneSpeech
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Which Bluetooth-triggered co-pilot action is running. */
sealed interface CoPilotMode {
  data object OpenMic : CoPilotMode
  data object AutoIdentify : CoPilotMode
  data object CatchMeUp : CoPilotMode
}

/**
 * Owns the pause -> (listen ->) Claude -> speak -> resume round trip shared by every
 * headset-triggered co-pilot mode. The Claude/transcript logic itself lives in
 * [CoPilotPipeline] (core:copilot) so the chat's typed questions can share it; this class is
 * only responsible for the Android-side mechanics: SpeechRecognizer, TextToSpeech, audio
 * focus, and being interruptible mid-flight (see [interruptIfActive]).
 *
 * Covers Open Mic, Auto-Identify, and Catch-Me-Up.
 * Snip doesn't pause playback or speak, so it isn't one of these modes - it
 * calls into [CoPilotPipeline.snip] directly from the UI layer instead.
 */
@Inject
@SingleIn(PlaybackScope::class)
class CoPilotEngine(
  private val context: Context,
  private val scope: CoroutineScope,
  private val copilotPipeline: CoPilotPipeline,
  private val copilotRepository: CoPilotRepository,
  private val claudeClient: ClaudeClient,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var phoneSpeech: PhoneSpeech? = null
  // Bumped on every speak() and interrupt so work finishing on another thread can tell it was
  // superseded (the job itself has already completed by the time its audio is played).
  private var speakGeneration = 0

  private var clockStart = 0L

  /** Logs `TIMING +ms event`, with ms counted from the button press (logcat also stamps every line). */
  private fun mark(event: String) {
    if (event.startsWith("trigger")) clockStart = System.currentTimeMillis()
    val line = "+${System.currentTimeMillis() - clockStart} ms $event"
    Logger.d("CoPilotEngine TIMING $line")
    flightLog.log(line)
  }

  private val flightLog = CoPilotFlightLog(context)

  // Set once the answer is audible; until then the watchdog may give up on the flow.
  private var audioStarted = false
  private var watchdog: Job? = null
  private var noSpeechTimeout: Runnable? = null

  @Volatile
  private var isActive = false
  private var activeJob: Job? = null

  private val audioManager = context.getSystemService(AudioManager::class.java)
  private var audioFocusRequest: AudioFocusRequest? = null

  // Every co-pilot sound is guidance audio: it follows the media volume, and Android Auto routes it to
  // the car. USAGE_ASSISTANT plays on the assistant stream, which the phone can mute on its own and
  // which an app cannot unmute, so the whole answer was silent.
  private val soundPool = SoundPool.Builder()
    .setMaxStreams(2)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build(),
    )
    .build()
  private val loadedSoundIds = mutableSetOf<Int>()
  private val startListeningSoundId = soundPool.load(context, R.raw.voice_spike_start_listening, 1)
  private val endListeningSoundId = soundPool.load(context, R.raw.voice_spike_end_listening, 1)

  // Auto-Identify has no listening phase to cue (it never calls listen()), so it gets its own
  // sound at the moment it starts instead - a two-note ascending "recognizing" ping, distinct
  // from Open Mic's single-tone start/end blips. Snip never pauses or speaks, so without this
  // it would be completely silent; its cue plays once the capture finishes, as a quiet "got it"
  // confirmation layered over the still-playing book.
  private val autoIdentifyStartSoundId = soundPool.load(context, R.raw.copilot_auto_identify, 1)
  private val snipCapturedSoundId = soundPool.load(context, R.raw.copilot_snip, 1)

  init {
    // The transcript takes seconds to find and parse; do it now so the first button press doesn't wait.
    scope.launch {
      try {
        val bookId = currentBookStoreId.data.first()
        Logger.d("CoPilotEngine: warm-up for book $bookId")
        // Open the Claude connection too, so the first spoken answer skips the TLS handshake.
        launch { claudeClient.warmUp() }
        if (bookId != null) copilotPipeline.warmUp(bookId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        Logger.w(e, "CoPilotEngine: transcript warm-up failed")
      }
    }
    soundPool.setOnLoadCompleteListener { _, sampleId, status ->
      if (status == 0) loadedSoundIds += sampleId
    }
  }

  private val waitingLoop = WaitingLoop(ExoWaitingLoopPlayback(context, mainHandler))
  private val waitingLoopRunning get() = waitingLoop.isRunning

  /** The flow is now waiting for its answer: play the loop and arm the no-audio watchdog. */
  private fun startWaiting(onFinished: () -> Unit) {
    audioStarted = false
    startWaitingLoop()
    watchdog?.cancel()
    watchdog = scope.launch {
      delay(NO_AUDIO_TIMEOUT_MS)
      if (!this@CoPilotEngine.isActive || audioStarted) return@launch
      // Never leave the listener in silence with the book paused: give up and resume it.
      Logger.w("CoPilotEngine: no audio after $NO_AUDIO_TIMEOUT_MS ms, giving up")
      mark("watchdog: no audio, resuming the book")
      activeJob?.cancel()
      activeJob = null
      speakGeneration++
      phoneSpeech?.release()
      phoneSpeech = null
      playCue(endListeningSoundId)
      finish(onFinished)
    }
  }

  private fun onAudioStarted() {
    audioStarted = true
    watchdog?.cancel()
    stopWaitingLoop(fade = true)
  }

  private fun releaseSpeechOnMain() {
    mainHandler.post {
      phoneSpeech?.release()
      phoneSpeech = null
    }
  }

  // Posted, never called inline, so a start queued by the flow can't run after a later stop.
  private fun startWaitingLoop() {
    mark("waiting loop start")
    mainHandler.post { waitingLoop.start() }
  }

  private fun stopWaitingLoop(fade: Boolean) {
    mainHandler.post {
      if (waitingLoopRunning) mark(if (fade) "waiting loop fade-out" else "waiting loop stop")
      if (fade) waitingLoop.stopWithFade() else waitingLoop.stopNow()
    }
  }

  private fun playCue(soundId: Int) {
    if (soundId in loadedSoundIds) {
      soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
  }

  fun trigger(mode: CoPilotMode, onFinished: () -> Unit) {
    Logger.d("CoPilotEngine: trigger($mode) on instance ${System.identityHashCode(this)}")
    isActive = true
    // Hold audio focus for the whole listening/thinking/speaking round trip. Without this,
    // TextToSpeech's own transient focus requests cause ExoPlayer's built-in audio-focus
    // handling to auto-resume the book internally (bypassing VoicePlayer entirely, since
    // it's driven by the raw player, not our wrapper) the moment TTS momentarily lets go of
    // focus between utterances - which fights with us keeping the book paused.
    requestAudioFocus()
    if (mode == CoPilotMode.AutoIdentify) {
      playCue(autoIdentifyStartSoundId)
    }
    mark("trigger($mode)")
    // Open the connections now so the first real request skips the TLS handshake.
    scope.launch { claudeClient.warmUp() }
    activeJob = scope.launch {
      try {
        val bookId = currentBookStoreId.data.first()
        if (bookId == null) {
          finish(onFinished)
          return@launch
        }
        if (mode != CoPilotMode.OpenMic) startWaiting(onFinished)
        when (mode) {
          CoPilotMode.OpenMic -> {
            val heard = listenForQuestion(bookId)
            if (heard == null) {
              finish(onFinished)
              return@launch
            }
            var question: String = heard
            var round = 0
            while (true) {
              startWaiting(onFinished)
              val asked = question
              speakAnswer(
                deltas = copilotPipeline.askStream(bookId, asked),
                nonStreaming = { copilotPipeline.ask(bookId, asked) },
                onAnswer = { recordAnswer(bookId, it) },
              )
              // The answer asked something back (a requested Extra that repeats one, or an alternative): hear the reply.
              if (++round > MAX_FOLLOW_UPS || !copilotPipeline.awaitingReply(bookId)) break
              question = listenForQuestion(bookId) ?: break
            }
          }
          CoPilotMode.AutoIdentify -> speakAnswer(
            deltas = copilotPipeline.autoIdentifyStream(bookId),
            nonStreaming = { copilotPipeline.autoIdentify(bookId) },
          )
          CoPilotMode.CatchMeUp -> speakAnswer(
            deltas = copilotPipeline.catchMeUpStream(bookId),
            nonStreaming = { copilotPipeline.catchMeUp(bookId) },
          )
        }
        finish(onFinished)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "CoPilotEngine: $mode failed")
        mark("failed: ${e.javaClass.simpleName} ${e.message?.take(120)}")
        val message = runCatching { copilotPipeline.failureMessage() }
          .getOrDefault("Sorry, something went wrong.")
        try {
          speakAnswer(flowOf(message), { message })
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          Logger.w(e, "CoPilotEngine: could not speak the failure message")
        }
        finish(onFinished)
      } catch (e: Throwable) {
        // Out of memory and the like: don't try to speak, just hand the book back instead of
        // crashing the app (after a crash the headset buttons do nothing at all).
        Logger.w(e, "CoPilotEngine: $mode failed hard")
        mark("failed hard: ${e.javaClass.simpleName}")
        releaseSpeechOnMain()
        finish(onFinished)
      }
    }
  }

  /**
   * Snip & Synthesize: unlike [trigger]'s modes, this doesn't pause playback or speak - it's a
   * silent background capture, so it doesn't touch [isActive]/audio focus at all.
   */
  fun snip() {
    scope.launch {
      val bookId = currentBookStoreId.data.first() ?: return@launch
      copilotPipeline.snip(bookId)
      playCue(snipCapturedSoundId)
    }
  }

  /** Returns null when the listener said nothing, so there is nothing to answer or speak. */
  private suspend fun listenForQuestion(bookId: BookId): String? {
    val heard = when (val result = listen()) {
      is ListenResult.Heard -> result.text
      ListenResult.Silence -> {
        Logger.d("CoPilotEngine: no speech heard, resuming")
        return null
      }
    }
    mark("recognizer result")
    Logger.d("CoPilotEngine: heard \"$heard\"")
    copilotRepository.addMessage(
      bookId,
      CoPilotMessage(
        id = UUID.randomUUID().toString(),
        role = CoPilotMessage.Role.User,
        text = heard,
        timestampMs = System.currentTimeMillis(),
      ),
    )
    return heard
  }

  private suspend fun recordAnswer(bookId: BookId, answer: String) {
    copilotRepository.addMessage(
      bookId,
      CoPilotMessage(
        id = UUID.randomUUID().toString(),
        role = CoPilotMessage.Role.CoPilot,
        text = answer,
        timestampMs = System.currentTimeMillis(),
      ),
    )
  }

  private fun finish(onFinished: () -> Unit) {
    watchdog?.cancel()
    stopWaitingLoop(fade = false)
    mark("finished")
    isActive = false
    abandonAudioFocus()
    onFinished()
  }

  private fun requestAudioFocus() {
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
      )
      .build()
    audioFocusRequest = request
    audioManager.requestAudioFocus(request)
  }

  private fun abandonAudioFocus() {
    audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    audioFocusRequest = null
  }

  /**
   * Called from [voice.core.playback.player.VoicePlayer] whenever playback is about to
   * resume (headset play/pause, in-app play/pause button, notification, etc.), and from
   * [LibrarySessionCallback] whenever a different mapped button starts its own action -
   * every mapped press interrupts whichever mode is currently active before doing its own
   * thing, never queuing behind it. No-op if nothing is active.
   */
  fun interruptIfActive() {
    Logger.d(
      "CoPilotEngine: interruptIfActive on instance ${System.identityHashCode(this)}, isActive=$isActive",
    )
    if (!isActive) return
    isActive = false
    // If still listening, cancelling the job resumes listen()'s suspendCancellableCoroutine
    // via invokeOnCancellation, which destroys the recognizer for us exactly once.
    activeJob?.cancel()
    activeJob = null
    // stop() alone isn't reliably cutting audio immediately on-device; release() shuts the
    // engine down outright. A fresh one is created for the next answer.
    phoneSpeech?.release()
    phoneSpeech = null
    speakGeneration++
    watchdog?.cancel()
    mark("interrupted")
    stopWaitingLoop(fade = false)
    abandonAudioFocus()
  }

  private suspend fun listen(): ListenResult = suspendCancellableCoroutine { cont ->
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }
    var settled = false
    var endCuePlayed = false
    fun settleSilently() {
      if (settled) return
      settled = true
      cancelNoSpeechTimeout()
      recognizer.destroy()
      if (!endCuePlayed) playCue(endListeningSoundId)
      cont.resume(ListenResult.Silence)
    }
    recognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onResults(results: Bundle) {
          if (settled) return
          settled = true
          cancelNoSpeechTimeout()
          recognizer.destroy()
          cont.resume(listenResultFor(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)))
        }

        override fun onError(error: Int) {
          if (recognizerErrorIsSilence(error)) {
            settleSilently()
            return
          }
          if (settled) return
          settled = true
          cancelNoSpeechTimeout()
          recognizer.destroy()
          cont.resumeWithException(IllegalStateException("SpeechRecognizer error $error"))
        }

        override fun onReadyForSpeech(params: Bundle?) {
          // The recognizer's own no-speech timeout varies by device and engine, so cap the
          // wait explicitly.
          cancelNoSpeechTimeout()
          val timeout = Runnable { settleSilently() }
          noSpeechTimeout = timeout
          mainHandler.postDelayed(timeout, NO_SPEECH_TIMEOUT_MS)
        }

        override fun onBeginningOfSpeech() = cancelNoSpeechTimeout()
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
          // Recognizer stopped listening and is about to hand back results / start
          // processing what it heard.
          endCuePlayed = true
          playCue(endListeningSoundId)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      },
    )
    cont.invokeOnCancellation {
      cancelNoSpeechTimeout()
      recognizer.destroy()
    }
    playCue(startListeningSoundId)
    recognizer.startListening(intent)
  }

  private fun cancelNoSpeechTimeout() {
    noSpeechTimeout?.let(mainHandler::removeCallbacks)
    noSpeechTimeout = null
  }

  /**
   * Speaks an answer that arrives as streamed text. If the stream fails before anything was
   * spoken, [nonStreaming] fetches the whole answer and that is spoken instead.
   */
  private suspend fun speakAnswer(
    deltas: Flow<String>,
    nonStreaming: suspend () -> String,
    onAnswer: suspend (String) -> Unit = {},
  ) {
    if (speakStream(deltas, onAnswer)) return
    Logger.w("CoPilotEngine: stream failed before any speech, using the non-streaming answer")
    val answer = nonStreaming()
    mark("non-streaming answer complete")
    onAnswer(answer)
    if (!speakStream(flowOf(answer), {})) Logger.w("CoPilotEngine: nothing to speak")
  }

  /**
   * Speaks with the phone's voice as the answer is written: text deltas go through [SpeechChunker], and each
   * sentence is queued on the phone TTS the moment it is complete, so the first one starts while the rest is still
   * being generated. Returns false only when the stream failed before anything was produced, so the caller can
   * fall back to the non-streaming path.
   */
  private suspend fun speakStream(deltas: Flow<String>, onAnswer: suspend (String) -> Unit): Boolean {
    val generation = ++speakGeneration
    val full = StringBuilder()
    val chunker = SpeechChunker()
    val speech = withMain {
      phoneSpeech?.release()
      PhoneSpeech(context, onAudible = {
        mainHandler.post {
          mark("phone TTS speaking")
          onAudioStarted()
        }
      }).also { phoneSpeech = it }
    }
    try {
      try {
        deltas.collect { delta ->
          if (full.isEmpty()) mark("first token")
          full.append(delta)
          chunker.feed(delta).forEach(speech::add)
        }
        chunker.finish().forEach(speech::add)
        mark("answer complete")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "CoPilotEngine: answer stream failed")
        flightLog.log("answer stream failed: ${e.javaClass.simpleName} ${e.message?.take(120)}")
        if (full.isEmpty()) return false
        chunker.finish().forEach(speech::add)
      }
      onAnswer(full.toString())
      speech.finish()
      speech.awaitDone()
    } finally {
      withMain {
        if (generation == speakGeneration) {
          speech.release()
          if (phoneSpeech === speech) phoneSpeech = null
        }
      }
    }
    endOfSpeechPause()
    return true
  }

  private suspend fun endOfSpeechPause() {
    // Signal that the spoken response is over, then give it a beat before resuming
    // the book so the cue doesn't get talked over by playback starting immediately.
    playCue(endListeningSoundId)
    delay(RESPONSE_END_PAUSE_MS)
  }

  private suspend fun <T> withMain(block: () -> T): T = suspendCancellableCoroutine { cont ->
    mainHandler.post {
      try {
        cont.resume(block())
      } catch (e: Exception) {
        cont.resumeWithException(e)
      }
    }
  }

  private companion object {
    const val RESPONSE_END_PAUSE_MS = 500L
    const val NO_SPEECH_TIMEOUT_MS = 5_000L
    const val NO_AUDIO_TIMEOUT_MS = 30_000L
    const val MAX_FOLLOW_UPS = 2
  }
}
