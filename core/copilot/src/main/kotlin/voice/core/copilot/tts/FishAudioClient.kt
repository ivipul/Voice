package voice.core.copilot.tts

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import voice.core.copilot.BuildConfig
import voice.core.logging.api.Logger
import java.util.concurrent.TimeUnit

/**
 * Fish Audio text-to-speech for the co-pilot's spoken answers, using the custom System AI voice
 * model on the free developer tier. Returns MP3 bytes; any failure throws so the caller can fall
 * back to the phone's TTS. The key comes from `fish.apiKey` in `~/.gradle/gradle.properties`,
 * never from the repo.
 */
@SingleIn(AppScope::class)
@Inject
class FishAudioClient {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

  val isConfigured: Boolean get() = BuildConfig.FISH_API_KEY.isNotBlank()

  suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.FISH_API_KEY
    check(apiKey.isNotBlank()) { "FISH_API_KEY not set (add fish.apiKey to ~/.gradle/gradle.properties)" }
    fetchAudio(httpClient, buildRequest(apiKey, text), RETRY_BACKOFF_MS)
  }

  internal companion object {
    const val URL = "https://api.fish.audio/v1/tts"
    const val MODEL = "s2.1-pro-free"
    const val VOICE_ID = "ad2dc11f1eaf4f6eb1ca4dd9d01ec6eb"
    private const val RETRY_BACKOFF_MS = 1000L
    private const val MAX_ATTEMPTS = 2
    private const val ERROR_BODY_CHARS = 200
    private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)

    fun buildRequest(apiKey: String, text: String): Request {
      val body = buildJsonObject {
        put("text", text)
        put("reference_id", VOICE_ID)
        put("format", "mp3")
        put("latency", "balanced")
      }
      return Request.Builder()
        .url(URL)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("model", MODEL)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()
    }

    fun fetchAudio(client: Call.Factory, request: Request, backoffMs: Long): ByteArray {
      var lastError: Exception? = null
      for (attempt in 0 until MAX_ATTEMPTS) {
        if (attempt > 0) {
          Logger.d("FishAudioClient: retrying (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
          Thread.sleep(backoffMs)
        }
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val bytes = response.body.bytes()
            check(bytes.isNotEmpty()) { "Fish Audio returned an empty body" }
            return bytes
          }
          val error = IllegalStateException(
            "Fish Audio TTS failed: ${response.code} ${response.body.string().take(ERROR_BODY_CHARS)}",
          )
          if (response.code !in RETRYABLE_CODES) throw error
          lastError = error
        }
      }
      throw lastError ?: IllegalStateException("Fish Audio TTS failed")
    }
  }
}
