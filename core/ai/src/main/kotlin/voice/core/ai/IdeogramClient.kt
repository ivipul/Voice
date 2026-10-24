package voice.core.ai

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import voice.core.data.ApiKeys
import voice.core.data.store.ApiKeysStore
import voice.core.logging.api.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The image shapes Earwig asks Ideogram for. */
enum class ImageShape(val size: String) {
  Square("1024x1024"),
  Portrait("864x1152"),
}

/**
 * Ideogram text-to-image with the listener's key from Settings, for snip pictures and custom covers. A picture costs
 * a few cents.
 */
@SingleIn(AppScope::class)
@Inject
class IdeogramClient(
  @ApiKeysStore
  private val apiKeys: DataStore<ApiKeys>,
) {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

  suspend fun isConfigured(): Boolean = apiKeys.data.first().ideogram.isNotBlank()

  /**
   * The picture as PNG or JPEG bytes. With a [source] image, the prompt is an instruction for changing that image.
   * Throws when there is no key or the request fails.
   */
  suspend fun generate(prompt: String, shape: ImageShape = ImageShape.Square, source: ByteArray? = null): ByteArray {
    val key = apiKeys.data.first().ideogram.trim()
    if (key.isEmpty()) throw MissingApiKeyException("Ideogram")
    return withContext(Dispatchers.IO) {
      download(callForImageUrl(key, generateBody(prompt, shape, source)))
    }
  }

  private fun callForImageUrl(apiKey: String, body: MultipartBody): String {
    val request = Request.Builder()
      .url("$HOST/v2/image/generate/ideogram-4-5")
      .addHeader("Api-Key", apiKey)
      .post(body)
      .build()
    var lastError: Exception? = null
    for (attempt in 0 until MAX_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("IdeogramClient: retrying (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        Thread.sleep(RETRY_BACKOFF_MS * attempt)
      }
      httpClient.newCall(request).execute().use { response ->
        val text = response.body.string()
        if (response.isSuccessful) return parseImageUrl(text)
        val error = IllegalStateException("Ideogram failed: ${response.code} ${text.take(ERROR_BODY_CHARS)}")
        if (response.code !in RETRYABLE_CODES) throw error
        lastError = error
      }
    }
    throw lastError ?: IllegalStateException("Ideogram failed after $MAX_ATTEMPTS attempts")
  }

  // Output URLs expire and refuse default HTTP clients, so fetch at once with a browser User-Agent
  // and without the API key (this is a different host).
  private fun download(url: String): ByteArray {
    val request = Request.Builder().url(url).header("User-Agent", BROWSER_USER_AGENT).build()
    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("Ideogram image download failed: ${response.code}")
      return response.body.bytes()
    }
  }

  internal companion object {
    const val HOST = "https://api.ideogram.ai"
    private const val MAX_ATTEMPTS = 2
    private const val RETRY_BACKOFF_MS = 2000L
    private const val ERROR_BODY_CHARS = 300
    private const val QUALITY = "medium"
    private val RETRYABLE_CODES = setOf(429, 502, 503, 504)
    private const val BROWSER_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun generateBody(prompt: String, shape: ImageShape, source: ByteArray? = null): MultipartBody =
      MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("prompt", prompt)
        .addFormDataPart("size", shape.size)
        .addFormDataPart("quality", QUALITY)
        .apply {
          if (source != null) {
            val type = source.imageMimeType()
            addFormDataPart("images", "source.${type.substringAfter('/')}", source.toRequestBody(type.toMediaType()))
          }
        }
        .build()

    fun parseImageUrl(json: String): String =
      Json.parseToJsonElement(json).jsonObject.getValue("data").jsonArray.first()
        .jsonObject.getValue("url").jsonPrimitive.content
  }
}

/** The MIME type of image bytes, from their magic number. */
fun ByteArray.imageMimeType(): String =
  if (size > 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) "image/jpeg" else "image/png"
