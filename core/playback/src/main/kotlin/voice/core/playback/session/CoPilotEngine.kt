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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.data.BookId
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.playback.R
import voice.core.playback.di.PlaybackScope
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
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var textToSpeech: TextToSpeech? = null

  @Volatile
  private var isActive = false
  private var activeJob: Job? = null

  private val audioManager = context.getSystemService(AudioManager::class.java)
  private var audioFocusRequest: AudioFocusRequest? = null

  private val soundPool = SoundPool.Builder()
    .setMaxStreams(2)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
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
    soundPool.setOnLoadCompleteListener { _, sampleId, status ->
      if (status == 0) loadedSoundIds += sampleId
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
    activeJob = scope.launch {
      try {
        val bookId = currentBookStoreId.data.first()
        if (bookId == null) {
          finish(onFinished)
          return@launch
        }
        val answer = when (mode) {
          CoPilotMode.OpenMic -> runOpenMic(bookId)
          CoPilotMode.AutoIdentify -> copilotPipeline.autoIdentify(bookId)
          CoPilotMode.CatchMeUp -> copilotPipeline.catchMeUp(bookId)
        }
        speak(answer) { finish(onFinished) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "CoPilotEngine: $mode failed")
        speak("Sorry, something went wrong.") { finish(onFinished) }
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

  private suspend fun runOpenMic(bookId: BookId): String {
    val heard = listen()
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
    val answer = copilotPipeline.ask(bookId, heard)
    copilotRepository.addMessage(
      bookId,
      CoPilotMessage(
        id = UUID.randomUUID().toString(),
        role = CoPilotMessage.Role.CoPilot,
        text = answer,
        timestampMs = System.currentTimeMillis(),
      ),
    )
    return answer
  }

  private fun finish(onFinished: () -> Unit) {
    isActive = false
    abandonAudioFocus()
    onFinished()
  }

  private fun requestAudioFocus() {
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
    abandonAudioFocus()
  }

  private suspend fun listen(): String = suspendCancellableCoroutine { cont ->
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }
    recognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onResults(results: Bundle) {
          val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
          recognizer.destroy()
          if (text != null) {
            cont.resume(text)
          } else {
            cont.resumeWithException(IllegalStateException("No speech recognized"))
          }
        }

        override fun onError(error: Int) {
          recognizer.destroy()
          cont.resumeWithException(IllegalStateException("SpeechRecognizer error $error"))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
          // Recognizer stopped listening and is about to hand back results / start
          // processing what it heard.
          playCue(endListeningSoundId)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      },
    )
    cont.invokeOnCancellation { recognizer.destroy() }
    playCue(startListeningSoundId)
    recognizer.startListening(intent)
  }

  private fun speak(text: String, onDone: () -> Unit) {
    textToSpeech?.shutdown()
    textToSpeech = TextToSpeech(context) { status ->
      val tts = textToSpeech
      if (status == TextToSpeech.SUCCESS && tts != null) {
        tts.setOnUtteranceProgressListener(
          object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
              Logger.d("CoPilotEngine: TTS onStart")
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
  }
}
