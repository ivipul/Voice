package voice.core.copilot.frame

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
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
import voice.core.copilot.BuildConfig
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Ideogram 4.5 REST client for the two calls a one-frame job needs (base plate, masked precise
 * edit), with the parameters from the storyboard `generation-settings.json`: 736x1312, quality
 * medium. Each call costs $0.06. The key comes from `ideogram.apiKey` in
 * `~/.gradle/gradle.properties`, never from the repo.
 */
@SingleIn(AppScope::class)
@Inject
class IdeogramClient {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

  val isConfigured: Boolean get() = BuildConfig.IDEOGRAM_API_KEY.isNotBlank()

  internal suspend fun generatePlate(prompt: String, styleImages: List<File>): ByteArray = withContext(Dispatchers.IO) {
    val body = plateBody(prompt, styleImages.map { it.readBytes() })
    download(callForImageUrl("generate/ideogram-4-5", body))
  }

  internal suspend fun preciseEdit(
    prompt: String,
    image: ByteArray,
    mask: ByteArray,
    references: List<File>,
  ): ByteArray = withContext(Dispatchers.IO) {
    val body = editBody(prompt, image, mask, references.map { it.readBytes() })
    download(callForImageUrl("precise-edit/ideogram-4-5", body))
  }

  private fun callForImageUrl(endpoint: String, body: MultipartBody): String {
    val apiKey = BuildConfig.IDEOGRAM_API_KEY
    check(apiKey.isNotBlank()) { "IDEOGRAM_API_KEY not set (add ideogram.apiKey to ~/.gradle/gradle.properties)" }
    val request = Request.Builder()
      .url("$HOST/v2/image/$endpoint")
      .addHeader("Api-Key", apiKey)
      .post(body)
      .build()
    var lastError: Exception? = null
    for (attempt in 0 until MAX_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("IdeogramClient: retrying $endpoint (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        Thread.sleep(RETRY_BACKOFF_MS * attempt)
      }
      httpClient.newCall(request).execute().use { response ->
        val text = response.body.string()
        if (response.isSuccessful) return parseImageUrl(text)
        val error = IllegalStateException("Ideogram $endpoint failed: ${response.code} ${text.take(ERROR_BODY_CHARS)}")
        if (response.code !in RETRYABLE_CODES) throw error
        lastError = error
      }
    }
    throw lastError ?: IllegalStateException("Ideogram $endpoint failed after $MAX_ATTEMPTS attempts")
  }

  // Output URLs expire and 403 default HTTP clients, so fetch at once with a browser User-Agent
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
    private val RETRYABLE_CODES = setOf(429, 502, 503, 504)
    private const val BROWSER_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun plateBody(prompt: String, styleImages: List<ByteArray>): MultipartBody =
      MultipartBody.Builder().setType(MultipartBody.FORM).apply {
        addFormDataPart("prompt", prompt)
        addFormDataPart("size", "${FRAME_WIDTH}x$FRAME_HEIGHT")
        addFormDataPart("quality", QUALITY)
        styleImages.forEachIndexed { i, bytes -> addImage("images", "style-$i", bytes) }
      }.build()

    fun editBody(prompt: String, image: ByteArray, mask: ByteArray, references: List<ByteArray>): MultipartBody =
      MultipartBody.Builder().setType(MultipartBody.FORM).apply {
        addFormDataPart("prompt", prompt)
        addFormDataPart("quality", QUALITY)
        addImage("image", "frame", image)
        addImage("mask", "mask", mask)
        references.forEachIndexed { i, bytes -> addImage("reference_images", "reference-$i", bytes) }
      }.build()

    fun parseImageUrl(json: String): String =
      Json.parseToJsonElement(json).jsonObject.getValue("data").jsonArray.first()
        .jsonObject.getValue("url").jsonPrimitive.content

    private const val QUALITY = "medium"

    private fun MultipartBody.Builder.addImage(field: String, name: String, bytes: ByteArray) {
      val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
      val (extension, type) = if (isJpeg) "jpg" to "image/jpeg" else "png" to "image/png"
      addFormDataPart(field, "$name.$extension", bytes.toRequestBody(type.toMediaType()))
    }
  }
}
