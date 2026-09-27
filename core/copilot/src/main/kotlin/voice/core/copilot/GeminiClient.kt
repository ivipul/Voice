package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
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
    askBlocking(systemPrompt, userPrompt)
  }

  /**
   * Best-effort single-image generation for Snip & Synthesize's comic-panel capture: unlike
   * [ask], failures here don't throw - image generation is a nice-to-have on top of the text
   * highlight, not something that should ever break Snip itself. Returns null on any failure
   * (bad response shape, network error, no image part in the response, etc).
   */
  suspend fun generateImage(prompt: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching { generateImageBlocking(prompt) }.getOrElse { e ->
      Logger.w(e, "GeminiClient: image generation failed")
      null
    }
  }

  private fun generateImageBlocking(prompt: String): ByteArray? {
    val apiKey = BuildConfig.GEMINI_API_KEY
    check(apiKey.isNotBlank()) { "GEMINI_API_KEY not set in ~/.gradle/gradle.properties" }

    val requestJson = buildJsonObject {
      putJsonArray("contents") {
        addJsonObject {
          putJsonArray("parts") {
            addJsonObject { put("text", prompt) }
          }
        }
      }
      putJsonObject("generationConfig") {
        putJsonArray("responseModalities") {
          add("IMAGE")
        }
      }
    }
    val request = Request.Builder()
      .url("https://generativelanguage.googleapis.com/v1beta/models/$IMAGE_MODEL:generateContent")
      .addHeader("x-goog-api-key", apiKey)
      .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
      .build()

    var lastError: Exception? = null
    for (attempt in 0 until MAX_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("GeminiClient: retrying image request (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        Thread.sleep(RETRY_BACKOFF_MS * attempt)
      }
      try {
        httpClient.newCall(request).execute().use { response ->
          val responseBody = response.body.string()
          check(response.isSuccessful) { "Gemini image request failed: ${response.code} $responseBody" }
          val parts = Json.parseToJsonElement(responseBody).jsonObject
            .getValue("candidates").jsonArray[0].jsonObject
            .getValue("content").jsonObject
            .getValue("parts").jsonArray
          val base64 = parts.firstNotNullOfOrNull { part ->
            part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
          } ?: return null
          return Base64.getDecoder().decode(base64)
        }
      } catch (e: IOException) {
        lastError = e
      } catch (e: IllegalStateException) {
        if (e.message?.startsWith("Gemini image request failed: 503") != true) throw e
        lastError = e
      }
    }
    throw lastError ?: IllegalStateException("Gemini image request failed after $MAX_ATTEMPTS attempts")
  }

  private fun askBlocking(systemPrompt: String, userPrompt: String): String {
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
            addJsonObject { put("text", userPrompt) }
          }
        }
      }
      // Low thinking level: these are quick spoken/spot answers, not tasks needing deep
      // reasoning, so trade reasoning depth for lower latency.
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
    for (attempt in 0 until MAX_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("GeminiClient: retrying request (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        Thread.sleep(RETRY_BACKOFF_MS * attempt)
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
    throw lastError ?: IllegalStateException("Gemini request failed after $MAX_ATTEMPTS attempts")
  }

  private companion object {
    const val MAX_ATTEMPTS = 3
    const val RETRY_BACKOFF_MS = 1000L

    // Not yet verified against a live call - this environment has no network access to test
    // against. Google's current image-generation-capable Gemini model at time of writing;
    // if this 404s or the response shape doesn't match on-device, that's the first thing to
    // check and adjust.
    const val IMAGE_MODEL = "gemini-2.5-flash-image"
  }
}
