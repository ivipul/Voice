package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * System 2 of the Smart Router pipeline: Gemini, called with an explicit system prompt and
 * user prompt. Generalized from the Phase 0 voice round-trip spike so every co-pilot action
 * (Open Mic, Auto-Identify, Snip & Synthesize, Catch-Me-Up, and the Feed's typed Ask) shares
 * one HTTP client, retry policy, and low-thinking config.
 */
@SingleIn(AppScope::class)
@Inject
class GeminiClient {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  suspend fun ask(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.IO) {
    askBlocking(systemPrompt, userPrompt, images = emptyList(), json = false)
  }

  /**
   * Same call as [ask] but the reply is constrained to JSON, and [images] (PNG or JPEG bytes)
   * are sent alongside the text. Used by the frame pipeline for structured creative fields and
   * for the bounding-box vision step.
   */
  suspend fun askJson(systemPrompt: String, userPrompt: String, images: List<ByteArray> = emptyList()): String =
    withContext(Dispatchers.IO) {
      askBlocking(systemPrompt, userPrompt, images, json = true)
    }

  /**
   * The same request as [ask] (same model, prompts and low-thinking config) streamed as server-sent
   * events, emitted as text deltas as Gemini writes them so spoken answers can start before the
   * whole answer exists. Throws on any failure, including mid-stream; there are no retries because
   * the caller may already have spoken what was emitted.
   */
  fun askStream(systemPrompt: String, userPrompt: String): Flow<String> = channelFlow {
    val apiKey = BuildConfig.GEMINI_API_KEY
    check(apiKey.isNotBlank()) { "GEMINI_API_KEY not set in ~/.gradle/gradle.properties" }
    val request = Request.Builder()
      .url(STREAM_URL)
      .addHeader("x-goog-api-key", apiKey)
      .post(requestJson(systemPrompt, userPrompt, emptyList(), json = false).toString().toRequestBody("application/json".toMediaType()))
      .build()
    val call = httpClient.newCall(request)
    // Reading the body blocks, so cancelling the collector has to cancel the call itself.
    val canceller = launch {
      try {
        awaitCancellation()
      } finally {
        call.cancel()
      }
    }
    try {
      withContext(Dispatchers.IO) {
        val sentAt = System.currentTimeMillis()
        Logger.d("GeminiClient: stream request sent (prompt ${userPrompt.length} chars)")
        call.execute().use { response ->
          if (!response.isSuccessful) throw IllegalStateException("Gemini stream failed: ${response.code} ${response.body.string().take(200)}")
          Logger.d("GeminiClient: stream headers after ${System.currentTimeMillis() - sentAt} ms")
          val source = response.body.source()
          var first = true
          while (true) {
            val line = source.readUtf8Line() ?: break
            val delta = parseSseDelta(line)?.takeIf { it.isNotEmpty() } ?: continue
            if (first) {
              first = false
              Logger.d("GeminiClient: first text after ${System.currentTimeMillis() - sentAt} ms")
            }
            send(delta)
          }
          Logger.d("GeminiClient: stream complete after ${System.currentTimeMillis() - sentAt} ms")
        }
      }
    } finally {
      canceller.cancel()
    }
  }

  /** Opens (or reuses) the connection to Gemini so the first real request skips the TLS handshake. */
  suspend fun warmUp() {
    try {
      val startedAt = System.currentTimeMillis()
      withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(HOST).head().build()).execute().close()
      }
      Logger.d("GeminiClient: connection warmed in ${System.currentTimeMillis() - startedAt} ms")
    } catch (e: IOException) {
      Logger.d("GeminiClient: warm-up failed (${e.javaClass.simpleName})")
    }
  }

  private fun askBlocking(systemPrompt: String, userPrompt: String, images: List<ByteArray>, json: Boolean): String {
    val apiKey = BuildConfig.GEMINI_API_KEY
    check(apiKey.isNotBlank()) { "GEMINI_API_KEY not set in ~/.gradle/gradle.properties" }

    val requestJson = requestJson(systemPrompt, userPrompt, images, json)
    val request = Request.Builder()
      .url("$HOST/v1beta/models/gemini-3.5-flash-lite:generateContent")
      .addHeader("x-goog-api-key", apiKey)
      .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
      .build()

    var lastError: Exception? = null
    for (attempt in 0 until MAX_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("GeminiClient: retrying request (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        Thread.sleep(RETRY_BACKOFF_MS * attempt)
      }
      try {
        val sentAt = System.currentTimeMillis()
        httpClient.newCall(request).execute().use { response ->
          val responseBody = response.body.string()
          Logger.d("GeminiClient: ${response.code} in ${System.currentTimeMillis() - sentAt} ms (prompt ${userPrompt.length} chars)")
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
    throw lastError ?: IllegalStateException("Gemini request failed after $MAX_ATTEMPTS attempts")
  }

  private fun requestJson(systemPrompt: String, userPrompt: String, images: List<ByteArray>, json: Boolean) =
    buildJsonObject {
      putJsonObject("systemInstruction") {
        putJsonArray("parts") {
          addJsonObject { put("text", systemPrompt) }
        }
      }
      putJsonArray("contents") {
        addJsonObject {
          putJsonArray("parts") {
            images.forEach { image ->
              addJsonObject {
                putJsonObject("inlineData") {
                  put("mimeType", image.mimeType())
                  put("data", Base64.getEncoder().encodeToString(image))
                }
              }
            }
            addJsonObject { put("text", userPrompt) }
          }
        }
      }
      // Low thinking level: these are quick spoken/spot answers, not tasks needing deep
      // reasoning, so trade reasoning depth for lower latency.
      putJsonObject("generationConfig") {
        if (json) put("responseMimeType", "application/json")
        putJsonObject("thinkingConfig") {
          put("thinkingLevel", "LOW")
        }
      }
    }

  internal companion object {
    const val HOST = "https://generativelanguage.googleapis.com"
    const val STREAM_URL = "$HOST/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent?alt=sse"
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_BACKOFF_MS = 1000L

    /** The text of one `data: {...}` line of a streamGenerateContent SSE response, or null for any other line. */
    fun parseSseDelta(line: String): String? {
      if (!line.startsWith("data:")) return null
      val payload = line.removePrefix("data:").trim()
      if (payload.isEmpty() || payload == "[DONE]") return null
      val parts = Json.parseToJsonElement(payload).jsonObject["candidates"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
      return parts.joinToString("") { part ->
        val obj = part.jsonObject
        if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) "" else obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
      }
    }
  }
}

private fun ByteArray.mimeType(): String =
  if (size > 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) "image/jpeg" else "image/png"
