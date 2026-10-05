package voice.features.playbackScreen.copilot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import voice.core.logging.api.Logger
import java.util.Locale

/** What the recognizer has heard so far: [Partial] while the user is still speaking, then one [Final]. */
sealed interface DictationEvent {
  val text: String

  data class Partial(override val text: String) : DictationEvent
  data class Final(override val text: String) : DictationEvent
}

/**
 * Captures one spoken question via Android's on-device [SpeechRecognizer].
 * Callers are responsible for checking RECORD_AUDIO permission before collecting [dictate].
 */
@SingleIn(AppScope::class)
@Inject
class SpeechInputController(private val context: Context) {

  /**
   * Listens for one utterance, emitting the words as they are recognized. The flow completes when the
   * recognizer stops listening, with or without a [DictationEvent.Final]; cancelling it stops the microphone.
   * Must be collected on the main thread.
   */
  fun dictate(): Flow<DictationEvent> = callbackFlow {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      Logger.w("No speech recognizer available on this device")
      close()
      return@callbackFlow
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(
      DictationListener(
        onPartial = { text -> trySend(DictationEvent.Partial(text)) },
        onFinal = { text ->
          trySend(DictationEvent.Final(text))
          close()
        },
        onStopped = { close() },
      ),
    )
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    recognizer.startListening(intent)
    awaitClose {
      recognizer.cancel()
      recognizer.destroy()
    }
  }
}

private class DictationListener(
  private val onPartial: (String) -> Unit,
  private val onFinal: (String) -> Unit,
  private val onStopped: () -> Unit,
) : RecognitionListener {

  override fun onPartialResults(partialResults: Bundle?) {
    val text = partialResults?.bestGuess()
    if (!text.isNullOrBlank()) onPartial(text)
  }

  override fun onResults(results: Bundle) {
    val text = results.bestGuess()
    if (text.isNullOrBlank()) onStopped() else onFinal(text)
  }

  override fun onError(error: Int) {
    Logger.w("SpeechRecognizer error: $error")
    onStopped()
  }

  override fun onReadyForSpeech(params: Bundle?) = Unit
  override fun onBeginningOfSpeech() = Unit
  override fun onRmsChanged(rmsdB: Float) = Unit
  override fun onBufferReceived(buffer: ByteArray?) = Unit
  override fun onEndOfSpeech() = Unit
  override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private fun Bundle.bestGuess(): String? = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
