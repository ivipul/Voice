package voice.core.ai

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import voice.core.data.ApiKeys
import voice.core.data.store.ApiKeysStore
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One spoken stretch of a transcription, in milliseconds from the start of the audio that was sent. */
data class SpokenLine(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

/**
 * Deepgram speech-to-text with the listener's key from Settings. Earwig sends a book in parts of about twenty minutes,
 * so each request is small enough to retry, and gets back utterances with their times.
 */
@SingleIn(AppScope::class)
@Inject
class DeepgramClient(
  @ApiKeysStore
  private val apiKeys: DataStore<ApiKeys>,
) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(5, TimeUnit.MINUTES)
    .readTimeout(10, TimeUnit.MINUTES)
    .build()

  suspend fun isConfigured(): Boolean = apiKeys.data.first().deepgram.isNotBlank()

  /** Transcribes [audio] of the given MIME type. Throws when there is no key or every attempt fails. */
  suspend fun transcribe(audio: File, mimeType: String): List<SpokenLine> {
    val key = apiKeys.data.first().deepgram.trim()
    if (key.isEmpty()) throw MissingApiKeyException("Deepgram")
    var lastError: Exception? = null
    repeat(MAX_ATTEMPTS) { attempt ->
      if (attempt > 0) delay(RETRY_BACKOFF_MS * attempt)
      try {
        return withContext(Dispatchers.IO) { call(key, audio, mimeType) }
      } catch (e: DeepgramRejected) {
        throw e
      } catch (e: IOException) {
        Logger.w(e, "Deepgram attempt ${attempt + 1} failed")
        lastError = e
      }
    }
    throw lastError ?: IOException("Deepgram failed")
  }

  private fun call(key: String, audio: File, mimeType: String): List<SpokenLine> {
    val request = Request.Builder()
      .url("$HOST/v1/listen?$QUERY")
      .addHeader("Authorization", "Token $key")
      .post(audio.asRequestBody(mimeType.toMediaType()))
      .build()
    httpClient.newCall(request).execute().use { response ->
      val text = response.body.string()
      if (response.isSuccessful) return parseUtterances(text)
      val message = "Deepgram failed: ${response.code} ${text.take(ERROR_BODY_CHARS)}"
      if (response.code in RETRYABLE_CODES) throw IOException(message)
      throw DeepgramRejected(message)
    }
  }

  /** A request Deepgram refused for a reason a retry won't fix, like a wrong key or audio it can't read. */
  class DeepgramRejected(message: String) : IllegalStateException(message)

  internal companion object {
    const val HOST = "https://api.deepgram.com"
    const val QUERY = "model=nova-3&smart_format=true&punctuate=true&utterances=true&utt_split=1.2"
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_BACKOFF_MS = 4_000L
    private const val ERROR_BODY_CHARS = 300
    private val RETRYABLE_CODES = setOf(408, 429, 500, 502, 503, 504)
    private val json = Json { ignoreUnknownKeys = true }

    fun parseUtterances(body: String): List<SpokenLine> = json.decodeFromString<DeepgramResponse>(body)
      .results.utterances
      .filter { it.transcript.isNotBlank() }
      .map { SpokenLine(startMs = (it.start * 1000).toLong(), endMs = (it.end * 1000).toLong(), text = it.transcript.trim()) }
  }
}

@Serializable
private data class DeepgramResponse(val results: DeepgramResults = DeepgramResults())

@Serializable
private data class DeepgramResults(val utterances: List<DeepgramUtterance> = emptyList())

@Serializable
private data class DeepgramUtterance(
  val start: Double,
  val end: Double,
  val transcript: String = "",
)
