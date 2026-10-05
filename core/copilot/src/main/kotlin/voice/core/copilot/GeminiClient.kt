package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
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

  private fun askBlocking(systemPrompt: String, userPrompt: String, images: List<ByteArray>, json: Boolean): String {
    val apiKey = BuildConfig.GEMINI_API_KEY
    check(apiKey.isNotBlank()) { "GEMINI_API_KEY not set in ~/.gradle/gradle.properties" }

    val requestJson = buildJsonObject {
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
    val request = Request.Builder()
      .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
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

  private companion object {
    const val MAX_ATTEMPTS = 3
    const val RETRY_BACKOFF_MS = 1000L
  }
}

private fun ByteArray.mimeType(): String =
  if (size > 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) "image/jpeg" else "image/png"
