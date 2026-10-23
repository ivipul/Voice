package voice.core.playback.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import voice.core.copilot.tts.SpeechChunker

/** The message being read aloud. It is not [audible] yet while its first words are still being prepared. */
data class SpokenMessage(
  val id: String,
  val audible: Boolean,
)

/**
 * Reads one written co-pilot message aloud on request, in the phone's own voice. It holds transient audio focus
 * while it speaks, so a playing book pauses and then carries on, and it stops as soon as anything else takes the
 * focus. Call it from the main thread.
 */
@SingleIn(AppScope::class)
@Inject
class MessageSpeaker(
  private val context: Context,
) {

  private val scope = MainScope()
  private val audioManager = context.getSystemService(AudioManager::class.java)
  private var current: Utterance? = null
  private var focusRequest: AudioFocusRequest? = null

  val speaking: StateFlow<SpokenMessage?>
    field = MutableStateFlow<SpokenMessage?>(null)

  /** Starts reading [text] aloud as the message [id], replacing whatever was being read. */
  fun speak(id: String, text: String) {
    stop()
    val utterance = Utterance(id)
    current = utterance
    speaking.value = SpokenMessage(id, audible = false)
    requestAudioFocus()
    utterance.job = scope.launch {
      try {
        val speech = PhoneSpeech(context, onAudible = { onAudible(utterance) })
        utterance.speech = speech
        SpeechChunker.split(text).forEach(speech::add)
        speech.finish()
        speech.awaitDone()
      } finally {
        utterance.release()
        if (current === utterance) {
          current = null
          speaking.value = null
          abandonAudioFocus()
        }
      }
    }
  }

  fun stop() {
    val utterance = current ?: return
    current = null
    speaking.value = null
    utterance.job?.cancel()
    utterance.release()
    abandonAudioFocus()
  }

  private fun onAudible(utterance: Utterance) {
    scope.launch {
      if (current === utterance) speaking.value = SpokenMessage(utterance.id, audible = true)
    }
  }

  private fun requestAudioFocus() {
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
      )
      .setOnAudioFocusChangeListener { change ->
        if (change < 0) scope.launch { stop() }
      }
      .build()
    focusRequest = request
    audioManager.requestAudioFocus(request)
  }

  private fun abandonAudioFocus() {
    focusRequest?.let(audioManager::abandonAudioFocusRequest)
    focusRequest = null
  }

  /** One message being read: everything it holds is let go together, whether it finished or was stopped. */
  private class Utterance(val id: String) {
    var job: Job? = null
    var speech: PhoneSpeech? = null

    fun release() {
      speech?.release()
      speech = null
    }
  }
}
