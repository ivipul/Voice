package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import voice.core.logging.api.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real System 1 of the Smart Router pipeline: TypeSafe's Jev model, called through OpenRouter's
 * alpha Decisions endpoint. This is a different wire format from [GeminiClient]'s chat
 * completions call - a typed `noul` question with `criteria` describing what true/false mean,
 * evaluated against a `state`, not a system/user message pair. Confirmed working against the
 * live endpoint before this was wired in as the bound [JevRouter].
 */
@ContributesBinding(AppScope::class)
@Inject
class RealJevRouter : JevRouter {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  override suspend fun needsTranscriptContext(question: String): Boolean = withContext(Dispatchers.IO) {
    askBlocking(question) > NOUL_THRESHOLD
  }

  private fun askBlocking(question: String): Double {
    val apiKey = BuildConfig.OPENROUTER_API_KEY
    check(apiKey.isNotBlank()) { "OPENROUTER_API_KEY not set in ~/.gradle/gradle.properties" }

    val requestJson = buildJsonObject {
      put("model", "typesafe/jev-1.13")
      put("state", "User query in an audiobook co-pilot app: \"$question\"")
      putJsonObject("questions") {
        putJsonObject("needs_recent_context") {
          put("type", "noul")
          put(
            "instructions",
            "Does answering this user query require recent (last 30 minutes) audiobook " +
              "transcript/listening context, as opposed to general lore or definitions?",
          )
          putJsonObject("criteria") {
            put(
              "true",
              "The query references specific recent events, characters just introduced, or " +
                "things that just happened in the last 30 minutes of listening.",
            )
            put(
              "false",
              "The query is about general lore, definitions, or established world facts that " +
                "do not depend on the last 30 minutes of listening.",
            )
          }
        }
      }
    }
    val request = Request.Builder()
      .url("https://openrouter.ai/api/alpha/decisions")
      .addHeader("Authorization", "Bearer $apiKey")
      .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
      .build()

    var lastError: Exception? = null
    for (attempt in 0 until MAX_ATTEMPTS) {
      if (attempt > 0) {
        Logger.d("RealJevRouter: retrying request (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        Thread.sleep(RETRY_BACKOFF_MS * attempt)
      }
      try {
        httpClient.newCall(request).execute().use { response ->
          val responseBody = response.body.string()
          check(response.isSuccessful) { "Jev request failed: ${response.code} $responseBody" }
          val json = Json.parseToJsonElement(responseBody).jsonObject
          return json.getValue("answers").jsonObject
            .getValue("needs_recent_context").jsonObject
            .getValue("noul").jsonPrimitive.double
        }
      } catch (e: IOException) {
        // Network hiccup / timeout - worth a retry.
        lastError = e
      } catch (e: IllegalStateException) {
        // Non-2xx response - only worth retrying if it's a transient overload (503).
        if (e.message?.startsWith("Jev request failed: 503") != true) throw e
        lastError = e
      }
    }
    throw lastError ?: IllegalStateException("Jev request failed after $MAX_ATTEMPTS attempts")
  }

  private companion object {
    const val MAX_ATTEMPTS = 3
    const val RETRY_BACKOFF_MS = 1000L
    const val NOUL_THRESHOLD = 0.5
  }
}
