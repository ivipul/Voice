package voice.core.playback.session

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.datastore.core.DataStore
import androidx.media3.common.AudioAttributes as MediaAudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.copilot.GeminiClient
import voice.core.copilot.tts.FishAudioClient
import voice.core.copilot.tts.SpeechChunker
import voice.core.data.BookId
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.playback.R
import voice.core.playback.di.PlaybackScope
import java.io.File
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
 * Owns the pause -> (listen ->) Jev/Gemini -> speak -> resume round trip shared by every
 * Bluetooth-triggered co-pilot mode. The Gemini/transcript/Jev logic itself lives in
 * [CoPilotPipeline] (core:copilot) so the Feed's typed Ask flow can share it; this class is
 * only responsible for the Android-side mechanics: SpeechRecognizer, TextToSpeech, audio
 * focus, and being interruptible mid-flight (see [interruptIfActive]).
 *
 * Originally a spike proving mic -> Gemini -> TTS worked on hardware (see git history for
 * `VoiceCoPilotSpike`); generalized here to cover Open Mic, Auto-Identify, and Catch-Me-Up.
 * Snip & Synthesize doesn't pause playback or speak, so it isn't one of these modes - it
 * calls into [CoPilotPipeline.snip] directly from the UI layer instead.
 */
@Inject
@SingleIn(PlaybackScope::class)
class CoPilotEngine(
  private val context: Context,
  private val scope: CoroutineScope,
  private val copilotPipeline: CoPilotPipeline,
  private val copilotRepository: CoPilotRepository,
  private val geminiClient: GeminiClient,
  private val fishAudioClient: FishAudioClient,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var textToSpeech: TextToSpeech? = null
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
  private var fishPlayer: ExoPlayer? = null
  private val fishFiles = mutableListOf<File>()
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
        // Open the Gemini and Fish connections too, so the first spoken answer skips the TLS handshakes.
        launch { geminiClient.warmUp() }
        launch { fishAudioClient.warmUp() }
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
      textToSpeech?.stop()
      releaseFishPlayer()
      playCue(endListeningSoundId)
      finish(onFinished)
    }
  }

  private fun onAudioStarted() {
    audioStarted = true
    watchdog?.cancel()
    stopWaitingLoop(fade = true)
  }

  private fun releaseFishPlayerOnMain() {
    mainHandler.post { releaseFishPlayer() }
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
    scope.launch {
      geminiClient.warmUp()
      fishAudioClient.warmUp()
    }
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
            startWaiting(onFinished)
            speakAnswer(
              deltas = copilotPipeline.askStream(bookId, heard),
              nonStreaming = { copilotPipeline.ask(bookId, heard) },
              onAnswer = { recordAnswer(bookId, it) },
            )
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
        releaseFishPlayerOnMain()
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
    // stop() alone isn't reliably cutting audio immediately on-device; shutdown() tears
    // down the engine connection outright. A fresh TextToSpeech is created next speak() call.
    textToSpeech?.stop()
    textToSpeech?.shutdown()
    textToSpeech = null
    speakGeneration++
    watchdog?.cancel()
    mark("interrupted")
    stopWaitingLoop(fade = false)
    releaseFishPlayer()
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
   * Speaks in the Fish Audio System AI voice when it's configured and reachable, otherwise (no
   * key, offline, out of credit, playback error) falls back to the phone's TextToSpeech. Text
   * deltas go through [SpeechChunker]; each sentence chunk is requested the moment it is complete
   * (all concurrently), chunk 1 plays as soon as it arrives and the rest are queued behind it in
   * order, waiting if one isn't ready. If a chunk fails, the rest of the answer is spoken by the
   * phone TTS from that point. Returns false only when the stream failed before any chunk was
   * produced, so the caller can fall back to the non-streaming path.
   */
  private suspend fun speakStream(deltas: Flow<String>, onAnswer: suspend (String) -> Unit): Boolean {
    if (!fishAudioClient.isConfigured) return speakStreamWithPhoneTts(deltas, onAnswer)
    val generation = ++speakGeneration
    val full = StringBuilder()
    val chunker = SpeechChunker()
    val chunkTexts = mutableListOf<String>()
    val queue = Channel<Deferred<File?>>(Channel.UNLIMITED)
    val fetches = mutableListOf<Deferred<File?>>()
    var streamError: Exception? = null
    var failedAt: Int? = null
    try {
      coroutineScope {
        fun submit(chunk: String) {
          chunkTexts += chunk
          val number = chunkTexts.size
          mark("chunk $number sent to Fish (${chunk.length} chars)")
          val fetch = async(Dispatchers.IO) { fetchChunk(chunk, number) }
          fetches += fetch
          queue.trySend(fetch)
        }
        val playback = async { playQueue(queue, generation) }
        try {
          deltas.collect { delta ->
            if (full.isEmpty()) mark("first token")
            full.append(delta)
            chunker.feed(delta).forEach(::submit)
          }
          chunker.finish().forEach(::submit)
          mark("answer complete")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Logger.w(e, "CoPilotEngine: answer stream failed")
          flightLog.log("answer stream failed: ${e.javaClass.simpleName} ${e.message?.take(120)}")
          streamError = e
          if (chunkTexts.isNotEmpty()) chunker.finish().forEach(::submit)
        }
        queue.close()
        if (streamError == null || chunkTexts.isNotEmpty()) onAnswer(full.toString())
        failedAt = playback.await()
      }
    } finally {
      fetches.forEach { it.cancel() }
      queue.cancel()
    }
    if (streamError != null && chunkTexts.isEmpty()) {
      withMain { if (generation == speakGeneration) releaseFishPlayer() }
      return false
    }
    withMain { if (generation == speakGeneration) releaseFishPlayer() }
    val remaining = failedAt?.let { chunkTexts.drop(it).joinToString(" ") }.orEmpty()
    if (remaining.isNotBlank()) {
      Logger.w("CoPilotEngine: speaking the rest with phone TTS (${chunkTexts.size - failedAt!!} of ${chunkTexts.size} chunks)")
      awaitPhoneTts(remaining)
    } else {
      endOfSpeechPause()
    }
    return true
  }

  /** No Fish key: collect the whole answer, then speak it with the phone TTS as before. */
  private suspend fun speakStreamWithPhoneTts(deltas: Flow<String>, onAnswer: suspend (String) -> Unit): Boolean {
    val full = StringBuilder()
    try {
      deltas.collect { full.append(it) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Logger.w(e, "CoPilotEngine: answer stream failed")
      if (full.isEmpty()) return false
    }
    mark("answer complete")
    onAnswer(full.toString())
    awaitPhoneTts(full.toString())
    return true
  }

  private suspend fun fetchChunk(chunk: String, number: Int): File? {
    val started = System.currentTimeMillis()
    val file = File.createTempFile("fish-tts", ".wav", context.cacheDir)
    return try {
      fishAudioClient.synthesizeTo(chunk, file)
      Logger.d("CoPilotEngine: Fish chunk $number (${chunk.length} chars) fetched in ${System.currentTimeMillis() - started} ms")
      file
    } catch (e: CancellationException) {
      file.delete()
      throw e
    } catch (e: Throwable) {
      // Includes running out of memory: the chunk is then spoken by the phone TTS instead.
      file.delete()
      Logger.w(e, "CoPilotEngine: Fish chunk $number failed")
      flightLog.log("Fish chunk $number failed: ${e.javaClass.simpleName} ${e.message?.take(120)}")
      null
    }
  }

  /**
   * Plays the fetched chunks in order as they arrive. Returns the index of the first chunk that
   * was not played (so the rest can be spoken another way), or null if every chunk played.
   */
  private suspend fun playQueue(queue: Channel<Deferred<File?>>, generation: Int): Int? {
    // Build the player while the first chunk is still being fetched; creating it is not free.
    withMain { if (generation == speakGeneration && fishPlayer == null) fishPlayer = createFishPlayer() }
    var index = 0
    for (fetch in queue) {
      val file = fetch.await()
      if (file == null || !enqueueFishChunk(file)) {
        if (index == 0) return 0
        // Let what is already queued finish before the rest is spoken another way.
        return if (awaitFishPlaybackEnd()) index else fishPlayedIndex
      }
      index++
    }
    if (index == 0) return null
    return if (awaitFishPlaybackEnd()) null else fishPlayedIndex
  }

  private suspend fun awaitPhoneTts(text: String) = suspendCancellableCoroutine { cont ->
    mainHandler.post { speakWithPhoneTts(text) { if (cont.isActive) cont.resume(Unit) } }
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

  private var fishFinished: CompletableDeferred<Boolean>? = null
  private var fishAllQueued = false
  private var fishPlayedIndex = 0

  /** Adds one clip to the speech playlist (creating the player on the first one); false if playback can't start. */
  private suspend fun enqueueFishChunk(file: File): Boolean = withMain {
    try {
      val player = fishPlayer ?: createFishPlayer().also { fishPlayer = it }
      fishFiles += file
      player.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.mediaItemCount - 1, 0)
      if (player.playbackState == Player.STATE_IDLE) player.prepare()
      player.playWhenReady = true
      true
    } catch (e: Exception) {
      Logger.w(e, "CoPilotEngine: Fish playback setup failed")
      false
    }
  }

  private fun createFishPlayer(): ExoPlayer {
    val finished = CompletableDeferred<Boolean>()
    fishFinished = finished
    fishAllQueued = false
    fishPlayedIndex = 0
    val player = ExoPlayer.Builder(context)
      .setAudioAttributes(
        MediaAudioAttributes.Builder()
          .setUsage(C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
          .build(),
        // The engine already holds audio focus for the whole round trip.
        false,
      )
      .build()
    var logged = false
    player.addListener(
      object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
          if (state == Player.STATE_READY && !logged) {
            logged = true
            mark("first audio playing")
            onAudioStarted()
          }
          if (state == Player.STATE_ENDED && fishAllQueued) finished.complete(true)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          fishPlayedIndex = player.currentMediaItemIndex
        }

        override fun onPlayerError(error: PlaybackException) {
          Logger.w(error, "CoPilotEngine: Fish playback error")
          fishPlayedIndex = player.currentMediaItemIndex
          finished.complete(false)
        }
      },
    )
    return player
  }

  /** Suspends until the queued chunks have all played (true) or playback failed (false). */
  private suspend fun awaitFishPlaybackEnd(): Boolean {
    val finished = fishFinished ?: return false
    withMain {
      fishAllQueued = true
      if (fishPlayer?.playbackState == Player.STATE_ENDED) finished.complete(true)
    }
    return finished.await()
  }

  private fun releaseFishPlayer() {
    fishPlayer?.release()
    fishPlayer = null
    fishFinished = null
    fishFiles.forEach { it.delete() }
    fishFiles.clear()
  }

  private fun speakWithPhoneTts(text: String, onDone: () -> Unit) {
    textToSpeech?.shutdown()
    textToSpeech = TextToSpeech(context) { status ->
      val tts = textToSpeech
      if (status == TextToSpeech.SUCCESS && tts != null) {
        // On Android Auto the spoken answer played on the phone speaker only while the book was paused,
        // so tag it (and the cues) as guidance audio, which Android Auto can route to the car.
        tts.setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        )
        tts.setOnUtteranceProgressListener(
          object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
              Logger.d("CoPilotEngine: TTS onStart")
              mainHandler.post {
                mark("phone TTS speaking")
                onAudioStarted()
              }
            }

            override fun onDone(utteranceId: String?) {
              Logger.d("CoPilotEngine: TTS onDone")
              // Runs on TTS's own binder thread; the Player must only be touched on
              // the main thread, so hop back before resuming playback.
              mainHandler.post { finishSpeaking(onDone) }
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
              Logger.d("CoPilotEngine: TTS onError")
              mainHandler.post { finishSpeaking(onDone) }
            }
          },
        )
        Logger.d("CoPilotEngine: TTS speaking ${text.length} chars")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-copilot")
      } else {
        Logger.w("CoPilotEngine: TTS init failed with status $status")
        mainHandler.post(onDone)
      }
    }
  }

  private fun finishSpeaking(onDone: () -> Unit) {
    // Signal that the spoken response is over, then give it a beat before resuming
    // the book so the cue doesn't get talked over by playback starting immediately.
    playCue(endListeningSoundId)
    mainHandler.postDelayed(onDone, RESPONSE_END_PAUSE_MS)
  }

  private companion object {
    const val RESPONSE_END_PAUSE_MS = 500L
    const val NO_SPEECH_TIMEOUT_MS = 5_000L
    const val NO_AUDIO_TIMEOUT_MS = 30_000L
  }
}
