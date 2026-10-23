package voice.core.playback.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred

/**
 * The phone's own text-to-speech, fed sentence by sentence: pieces can be added while the answer is still being
 * written, and they play back to back. Pieces added before the engine is ready wait for it. [onAudible] runs once,
 * when the first words are heard.
 */
class PhoneSpeech(
  context: Context,
  private val onAudible: () -> Unit = {},
) {

  private val lock = Any()
  private val pending = mutableListOf<String>()
  private val done = CompletableDeferred<Unit>()
  private var ready = false
  private var failed = false
  private var finished = false
  private var queued = 0
  private var completed = 0
  private var nextId = 0
  private var audible = false

  // The engine calls back on the main thread once it is bound, after this constructor has returned.
  private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> onInit(status) }

  private fun onInit(status: Int) {
    synchronized(lock) {
      if (status != TextToSpeech.SUCCESS) {
        failed = true
        done.complete(Unit)
        return
      }
      // Guidance audio follows the media volume and Android Auto routes it to the car.
      tts.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
      )
      tts.setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            val first = synchronized(lock) { !audible.also { audible = true } }
            if (first) onAudible()
          }

          override fun onDone(utteranceId: String?) = onPieceFinished()

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) = onPieceFinished()
        },
      )
      ready = true
      pending.forEach(::speakNow)
      pending.clear()
    }
  }

  /** Adds [text] to the end of what is being spoken. */
  fun add(text: String) {
    if (text.isBlank()) return
    synchronized(lock) {
      if (failed || finished) return
      queued++
      if (ready) speakNow(text) else pending += text
    }
  }

  /** No more pieces will come; [awaitDone] returns once the queued ones have been spoken. */
  fun finish() {
    synchronized(lock) {
      finished = true
      completeIfDone()
    }
  }

  suspend fun awaitDone() = done.await()

  /** Stops speaking at once and frees the engine. */
  fun release() {
    synchronized(lock) {
      failed = true
      done.complete(Unit)
    }
    tts.stop()
    tts.shutdown()
  }

  private fun speakNow(text: String) {
    tts.speak(text, TextToSpeech.QUEUE_ADD, null, "piece-${nextId++}")
  }

  private fun onPieceFinished() {
    synchronized(lock) {
      completed++
      completeIfDone()
    }
  }

  private fun completeIfDone() {
    if (finished && completed >= queued) done.complete(Unit)
  }
}
