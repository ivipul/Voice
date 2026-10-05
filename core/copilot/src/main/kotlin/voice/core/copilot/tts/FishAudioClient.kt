package voice.core.copilot.tts

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import voice.core.copilot.BuildConfig
import voice.core.logging.api.Logger
import java.util.concurrent.TimeUnit

/** A non-2xx answer from Fish Audio. */
class FishAudioException(val code: Int, message: String) : IllegalStateException(message)

/**
 * Fish Audio text-to-speech for the co-pilot's spoken answers, using the custom System AI voice
 * model. Returns WAV bytes; any failure throws so the caller can fall back to the phone's TTS.
 * Requests are cancelled together with the calling coroutine. The paid [PAID_MODEL] answers a
 * short sentence in about half the time of the free one, so it is tried first; on a 402 (out of
 * credit) it is skipped for the rest of the process, and on any other failure the free
 * [FREE_MODEL] is tried for that request. The key comes from `fish.apiKey` in
 * `~/.gradle/gradle.properties`, never from the repo.
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

  @Volatile
  private var paidUnavailable = false

  val isConfigured: Boolean get() = BuildConfig.FISH_API_KEY.isNotBlank()

  suspend fun synthesize(text: String): ByteArray {
    val apiKey = BuildConfig.FISH_API_KEY
    check(apiKey.isNotBlank()) { "FISH_API_KEY not set (add fish.apiKey to ~/.gradle/gradle.properties)" }
    val models = if (paidUnavailable) listOf(FREE_MODEL) else listOf(PAID_MODEL, FREE_MODEL)
    var lastError: Exception? = null
    for (model in models) {
      try {
        return fetchAudio(httpClient, buildRequest(apiKey, text, model), RETRY_BACKOFF_MS)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (model == PAID_MODEL) {
          Logger.w(e, "FishAudioClient: $PAID_MODEL failed, trying $FREE_MODEL")
          if ((e as? FishAudioException)?.code == PAYMENT_REQUIRED) paidUnavailable = true
        }
        lastError = e
      }
    }
    throw lastError ?: IllegalStateException("Fish Audio TTS failed")
  }

  internal companion object {
    const val URL = "https://api.fish.audio/v1/tts"
    const val PAID_MODEL = "s2.1-pro"
    const val FREE_MODEL = "s2.1-pro-free"
    const val VOICE_ID = "ad2dc11f1eaf4f6eb1ca4dd9d01ec6eb"
    private const val RETRY_BACKOFF_MS = 500L
    private const val MAX_ATTEMPTS = 2
    private const val ERROR_BODY_CHARS = 200
    private const val PAYMENT_REQUIRED = 402
    private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)

    fun buildRequest(apiKey: String, text: String, model: String = PAID_MODEL): Request {
      val body = buildJsonObject {
        put("text", text)
        put("reference_id", VOICE_ID)
        // WAV, not MP3: Fish's MP3 has no gapless (Xing/LAME) header, so chunk joins would click or gap.
        put("format", "wav")
        put("latency", "balanced")
      }
      return Request.Builder()
        .url(URL)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("model", model)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()
    }

    suspend fun fetchAudio(client: Call.Factory, request: Request, backoffMs: Long): ByteArray {
      var lastError: Exception? = null
      for (attempt in 0 until MAX_ATTEMPTS) {
        if (attempt > 0) {
          Logger.d("FishAudioClient: retrying (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
          delay(backoffMs)
        }
        client.newCall(request).executeAsync().use { response ->
          if (response.isSuccessful) {
            val bytes = response.body.bytes()
            check(bytes.isNotEmpty()) { "Fish Audio returned an empty body" }
            return bytes
          }
          val error = FishAudioException(
            response.code,
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
