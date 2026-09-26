package voice.features.playbackScreen.copilot

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import voice.core.logging.api.Logger
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Captures one spoken question via Android's on-device [SpeechRecognizer].
 * Callers are responsible for checking RECORD_AUDIO permission before calling [listen].
 */
@SingleIn(AppScope::class)
@Inject
class SpeechInputController(private val context: Context) {

  suspend fun listen(): Result<String> {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      return Result.failure(IllegalStateException("No speech recognizer available on this device"))
    }
    return suspendCancellableCoroutine { continuation ->
      val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
      recognizer.setRecognitionListener(
        SimpleRecognitionListener(
          onResult = { text ->
            if (continuation.isActive) continuation.resume(Result.success(text))
            recognizer.destroy()
          },
          onError = { message ->
            if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(message)))
            recognizer.destroy()
          },
        ),
      )
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
      }
      continuation.invokeOnCancellation {
        recognizer.cancel()
        recognizer.destroy()
      }
      recognizer.startListening(intent)
    }
  }
}

private class SimpleRecognitionListener(
  private val onResult: (String) -> Unit,
  private val onError: (String) -> Unit,
) : android.speech.RecognitionListener {
  override fun onResults(results: android.os.Bundle) {
    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    if (text.isNullOrBlank()) {
      onError("No speech recognized")
    } else {
      onResult(text)
    }
  }

  override fun onError(error: Int) {
    Logger.w("SpeechRecognizer error: $error")
    onError("Speech recognition failed ($error)")
  }

  override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
  override fun onBeginningOfSpeech() = Unit
  override fun onRmsChanged(rmsdB: Float) = Unit
  override fun onBufferReceived(buffer: ByteArray?) = Unit
  override fun onEndOfSpeech() = Unit
  override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
  override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
}
