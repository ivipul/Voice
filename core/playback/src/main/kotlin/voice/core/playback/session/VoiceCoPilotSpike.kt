package voice.core.playback.session

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import voice.core.logging.api.Logger
import voice.core.playback.BuildConfig
import voice.core.playback.R
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bluetooth voice round-trip spike (Phase 4 groundwork): proves mic -> Gemini -> TTS works
 * end to end on real hardware, triggered from a headset button. Not the real Open Mic
 * Co-Pilot implementation yet (no Jev routing, no transcript injection, no Feed logging).
 */
@Inject
class VoiceCoPilotSpike(
  private val context: Context,
  private val scope: CoroutineScope,
) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
  private val mainHandler = Handler(Looper.getMainLooper())
  private var textToSpeech: TextToSpeech? = null

  @Volatile
  private var isActive = false
  private var activeJob: Job? = null

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

  fun trigger(onFinished: () -> Unit) {
    isActive = true
    toast("Voice spike: listening...")
    activeJob = scope.launch {
      try {
        val heard = listen()
        Logger.d("Voice spike: heard \"$heard\"")
        toast("Heard: $heard")
        val reply = withContext(Dispatchers.IO) { askGemini(heard) }
        Logger.d("Voice spike: Gemini replied \"$reply\"")
        speak(reply) {
          isActive = false
          onFinished()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "Voice spike failed")
        toast("Voice spike failed: ${e.message}")
        isActive = false
        onFinished()
      }
    }
  }

  /**
   * Called from [voice.core.playback.player.VoicePlayer] whenever playback is about to
   * resume (headset play/pause, in-app play/pause button, notification, etc.) — if a voice
   * round trip is in progress, stop it immediately rather than let it keep talking over the
   * resumed book. No-op if nothing is active.
   */
  fun interruptIfActive() {
    if (!isActive) return
    isActive = false
    // If still listening, cancelling the job resumes listen()'s suspendCancellableCoroutine
    // via invokeOnCancellation, which destroys the recognizer for us exactly once.
    activeJob?.cancel()
    activeJob = null
    textToSpeech?.stop()
    toast("Voice spike: cancelled")
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

  private fun askGemini(prompt: String): String {
    val apiKey = BuildConfig.GEMINI_API_KEY
    check(apiKey.isNotBlank()) { "GEMINI_API_KEY not set in ~/.gradle/gradle.properties" }

    val requestJson = buildJsonObject {
      putJsonObject("systemInstruction") {
        putJsonArray("parts") {
          addJsonObject { put("text", SYSTEM_PROMPT) }
        }
      }
      putJsonArray("contents") {
        addJsonObject {
          putJsonArray("parts") {
            addJsonObject { put("text", prompt) }
          }
        }
      }
      // Low thinking level: this is a quick spoken Q&A, not a task needing deep reasoning,
      // so trade reasoning depth for lower latency.
      putJsonObject("generationConfig") {
        putJsonObject("thinkingConfig") {
          put("thinkingLevel", "LOW")
        }
      }
    }
    val request = Request.Builder()
      .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
      .addHeader("x-goog-api-key", apiKey)
      .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
      .build()

    var lastError: Exception? = null
    for (attempt in 0 until MAX_GEMINI_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("Voice spike: retrying Gemini request (attempt ${attempt + 1}/$MAX_GEMINI_ATTEMPTS)")
        Thread.sleep(GEMINI_RETRY_BACKOFF_MS * attempt)
      }
      try {
        httpClient.newCall(request).execute().use { response ->
          val responseBody = response.body.string()
          check(response.isSuccessful) { "Gemini request failed: ${response.code} $responseBody" }
          val json = Json.parseToJsonElement(responseBody).jsonObject
          return json.getValue("candidates").jsonArray[0].jsonObject
            .getValue("content").jsonObject
            .getValue("parts").jsonArray[0].jsonObject
            .getValue("text").jsonPrimitive.content
        }
      } catch (e: IOException) {
        // Network hiccup / timeout - worth a retry.
        lastError = e
      } catch (e: IllegalStateException) {
        // Non-2xx response - only worth retrying if it's a transient overload (503).
        if (e.message?.startsWith("Gemini request failed: 503") != true) throw e
        lastError = e
      }
    }
    throw lastError ?: IllegalStateException("Gemini request failed after $MAX_GEMINI_ATTEMPTS attempts")
  }

  private fun speak(text: String, onDone: () -> Unit) {
    textToSpeech?.shutdown()
    textToSpeech = TextToSpeech(context) { status ->
      val tts = textToSpeech
      if (status == TextToSpeech.SUCCESS && tts != null) {
        tts.setOnUtteranceProgressListener(
          object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
              // Runs on TTS's own binder thread; the Player must only be touched on
              // the main thread, so hop back before resuming playback.
              mainHandler.post(onDone)
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
              mainHandler.post(onDone)
            }
          },
        )
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-copilot-spike")
      } else {
        Logger.w("Voice spike: TTS init failed with status $status")
        mainHandler.post(onDone)
      }
    }
  }

  private fun toast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }

  private companion object {
    const val MAX_GEMINI_ATTEMPTS = 3
    const val GEMINI_RETRY_BACKOFF_MS = 1000L
    const val SYSTEM_PROMPT = "You are a voice assistant answering a spoken question out loud. " +
      "Keep your answer under 15-20 seconds when spoken (roughly 40-50 words), and don't ramble " +
      "or add extra detail beyond what was asked. Respond in plain, natural spoken language only: " +
      "no markdown, no headings, no bullet points, no asterisks, no URLs or links, no code, and no " +
      "text that wouldn't make sense read aloud by a text-to-speech engine."
  }
}
