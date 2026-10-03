package voice.core.playback.session

import android.speech.SpeechRecognizer

/** What the mic heard. [Silence] is "no question was asked", not a failure. */
internal sealed interface ListenResult {
  data class Heard(val text: String) : ListenResult
  data object Silence : ListenResult
}

internal fun recognizerErrorIsSilence(error: Int): Boolean =
  error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH

internal fun listenResultFor(recognized: List<String>?): ListenResult {
  val text = recognized?.firstOrNull()?.takeIf { it.isNotBlank() }
  return if (text != null) ListenResult.Heard(text) else ListenResult.Silence
}
