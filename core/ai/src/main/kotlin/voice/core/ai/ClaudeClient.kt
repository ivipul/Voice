package voice.core.ai

import androidx.datastore.core.DataStore
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.WebSearchTool20260209
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import voice.core.data.ApiKeys
import voice.core.data.store.ApiKeysStore
import voice.core.logging.api.Logger
import java.io.File
import java.time.Duration
import java.util.Base64

/** The Claude models Earwig uses, one per kind of job. */
object ClaudeModels {

  /** Answers, recaps, snips, book research and idea cards: quick and good at conversation. */
  const val CHAT = "claude-sonnet-5-5"

  /** Writing Extras, where the quality of the generated page matters most. */
  const val BUILDER = "claude-opus-5-5"

  /** Batch scans over a whole transcript, run window by window. */
  const val SCAN = "claude-haiku-5-5"
}

/** Thrown when a Claude feature is used before a key was entered in Settings. */
class MissingApiKeyException(service: String) : IllegalStateException("No $service API key. Add one in Settings.")

/** Thrown when Claude's reply hit its token limit, so it is incomplete. A smaller request may fit. */
class ReplyCutOffException : IllegalStateException("Claude's answer was too long and got cut off")

/**
 * Every Claude call in the app, through the official Anthropic SDK and the listener's own key from Settings.
 * Each call runs on [Dispatchers.IO]; the SDK retries rate limits and server errors itself.
 */
@SingleIn(AppScope::class)
@Inject
class ClaudeClient(
  @ApiKeysStore
  private val apiKeys: DataStore<ApiKeys>,
) {

  private val clientLock = Mutex()
  private var cached: Pair<String, AnthropicClient>? = null

  suspend fun isConfigured(): Boolean = apiKeys.data.first().claude.isNotBlank()

  private suspend fun client(): AnthropicClient {
    val key = apiKeys.data.first().claude.trim()
    if (key.isEmpty()) throw MissingApiKeyException("Claude")
    return clientLock.withLock {
      cached?.takeIf { it.first == key }?.second ?: AnthropicOkHttpClient.builder()
        .apiKey(key)
        .timeout(Duration.ofMinutes(5))
        .build()
        .also { cached = key to it }
    }
  }

  /**
   * One answer to [prompt], as plain text. [cachedPrefix] is a large, rarely changing part of the instructions (like
   * chapter summaries) that goes first and is cached, so repeated questions read it at a fraction of the price.
   * [webSearches] above zero lets Claude search the web that many times first.
   */
  suspend fun ask(
    system: String,
    prompt: String,
    model: String = ClaudeModels.CHAT,
    maxTokens: Long = DEFAULT_MAX_TOKENS,
    effort: OutputConfig.Effort = OutputConfig.Effort.LOW,
    cachedPrefix: String? = null,
    webSearches: Long = 0,
  ): String = call {
    client().messages().create(chatParams(system, prompt, model, maxTokens, effort, cachedPrefix, webSearches)).text()
  }

  /**
   * The same request as [ask], streamed as text deltas so speech and the chat bubble can start before the whole
   * answer exists. Throws on failure, including mid-stream.
   */
  fun askStream(
    system: String,
    prompt: String,
    model: String = ClaudeModels.CHAT,
    maxTokens: Long = DEFAULT_MAX_TOKENS,
    cachedPrefix: String? = null,
    webSearches: Long = 0,
  ): Flow<String> = channelFlow {
    val params = chatParams(system, prompt, model, maxTokens, OutputConfig.Effort.LOW, cachedPrefix, webSearches)
    val client = client()
    withContext(Dispatchers.IO) {
      client.messages().createStreaming(params).use { response ->
        // Reading the stream blocks, so cancelling the collector has to close it.
        val closer = launch {
          try {
            awaitCancellation()
          } finally {
            response.close()
          }
        }
        try {
          response.stream()
            .flatMap { event -> event.contentBlockDelta().stream() }
            .flatMap { delta -> delta.delta().text().stream() }
            .forEach { text -> trySendBlocking(text.text()) }
        } finally {
          closer.cancel()
        }
      }
    }
  }

  private fun chatParams(
    system: String,
    prompt: String,
    model: String,
    maxTokens: Long,
    effort: OutputConfig.Effort,
    cachedPrefix: String?,
    webSearches: Long,
  ): MessageCreateParams {
    val builder = MessageCreateParams.builder()
      .model(model)
      .maxTokens(maxTokens)
      .addUserMessage(prompt)
      .outputConfig(OutputConfig.builder().effort(effort).build())
    if (cachedPrefix.isNullOrBlank()) {
      builder.system(system)
    } else {
      builder.systemOfTextBlockParams(
        listOf(
          TextBlockParam.builder().text(cachedPrefix).cacheControl(CacheControlEphemeral.builder().build()).build(),
          TextBlockParam.builder().text(system).build(),
        ),
      )
    }
    if (webSearches > 0) builder.addTool(WebSearchTool20260209.builder().maxUses(webSearches).build())
    return builder.build()
  }

  /**
   * A long piece of writing, like a whole Extra page: streamed under the hood so a reply that takes minutes never
   * hits a request timeout, and returned whole. Throws when the reply was cut off at [maxTokens].
   */
  suspend fun write(
    system: String,
    prompt: String,
    model: String = ClaudeModels.BUILDER,
    maxTokens: Long = WRITE_MAX_TOKENS,
    effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
  ): String = call {
    val params = MessageCreateParams.builder()
      .model(model)
      .maxTokens(maxTokens)
      .system(system)
      .addUserMessage(prompt)
      .outputConfig(OutputConfig.builder().effort(effort).build())
      .build()
    val text = StringBuilder()
    var stopReason: StopReason? = null
    client().messages().createStreaming(params).use { response ->
      response.stream().forEach { event ->
        event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent { text.append(it.text()) }
        event.messageDelta().flatMap { it.delta().stopReason() }.ifPresent { stopReason = it }
      }
    }
    when (stopReason) {
      StopReason.MAX_TOKENS -> throw IllegalStateException("Claude's answer was too long and got cut off")
      StopReason.REFUSAL -> throw IllegalStateException("Claude declined this request")
      else -> Unit
    }
    text.toString().trim().also { check(it.isNotEmpty()) { "Claude returned no text" } }
  }

  /**
   * An answer constrained to [schema] (a JSON Schema object), returned as the raw JSON text. Callers decode it with
   * kotlinx.serialization. An [image] (PNG or JPEG bytes) goes before the prompt for Claude to look at.
   */
  suspend fun askJson(
    system: String,
    prompt: String,
    schema: JsonObject,
    model: String = ClaudeModels.CHAT,
    maxTokens: Long = JSON_MAX_TOKENS,
    effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
    image: ByteArray? = null,
  ): String = call {
    val format = JsonOutputFormat.builder()
      .schema(
        JsonOutputFormat.Schema.builder()
          .putAllAdditionalProperties(schema.mapValues { (_, value) -> value.toJsonValue() })
          .build(),
      )
      .build()
    val params = MessageCreateParams.builder()
      .model(model)
      .maxTokens(maxTokens)
      .system(system)
      .let { if (image == null) it.addUserMessage(prompt) else it.addUserMessageOfContentBlockParams(imageAndText(image, prompt)) }
      .outputConfig(OutputConfig.builder().effort(effort).format(format).build())
      .build()
    val message = client().messages().create(params)
    if (message.stopReason().orElse(null) == StopReason.MAX_TOKENS) throw ReplyCutOffException()
    message.text()
  }

  private fun imageAndText(image: ByteArray, prompt: String): List<ContentBlockParam> {
    val source = Base64ImageSource.builder()
      .data(Base64.getEncoder().encodeToString(image))
      .mediaType(Base64ImageSource.MediaType.of(image.imageMimeType()))
      .build()
    return listOf(ContentBlockParam.ofImage(source), ContentBlockParam.ofText(prompt))
  }

  /**
   * An answer about a PDF (Claude reads its text and its pages as images), constrained to [schema] and returned as
   * raw JSON. Streamed under the hood, since reading a long document can take minutes.
   */
  suspend fun askJsonAboutPdf(
    system: String,
    prompt: String,
    pdf: File,
    schema: JsonObject,
    model: String = ClaudeModels.CHAT,
    maxTokens: Long = PDF_MAX_TOKENS,
  ): String = call {
    check(pdf.length() <= MAX_PDF_BYTES) { "The PDF is too big for Claude to read (${pdf.length() / 1_000_000} MB)" }
    val data = Base64.getEncoder().encodeToString(pdf.readBytes())
    val format = JsonOutputFormat.builder()
      .schema(
        JsonOutputFormat.Schema.builder()
          .putAllAdditionalProperties(schema.mapValues { (_, value) -> value.toJsonValue() })
          .build(),
      )
      .build()
    val params = MessageCreateParams.builder()
      .model(model)
      .maxTokens(maxTokens)
      .system(system)
      .addUserMessageOfContentBlockParams(
        listOf(
          ContentBlockParam.ofDocument(DocumentBlockParam.builder().base64Source(data).build()),
          ContentBlockParam.ofText(prompt),
        ),
      )
      .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).format(format).build())
      .build()
    val text = StringBuilder()
    var stopReason: StopReason? = null
    client().messages().createStreaming(params).use { response ->
      response.stream().forEach { event ->
        event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent { text.append(it.text()) }
        event.messageDelta().flatMap { it.delta().stopReason() }.ifPresent { stopReason = it }
      }
    }
    when (stopReason) {
      StopReason.MAX_TOKENS -> throw IllegalStateException("Claude's notes on the PDF were too long and got cut off")
      StopReason.REFUSAL -> throw IllegalStateException("Claude declined to read this PDF")
      else -> Unit
    }
    text.toString().trim().also { check(it.isNotEmpty()) { "Claude returned no text" } }
  }

  /**
   * An answer that may use Claude's web search first, for looking a book up online. Returns Claude's final text
   * (citations dropped). Long searches pause the turn; those are continued a few times.
   */
  suspend fun research(
    system: String,
    prompt: String,
    maxSearches: Long = 5,
    model: String = ClaudeModels.CHAT,
  ): String = call {
    val base = MessageCreateParams.builder()
      .model(model)
      .maxTokens(RESEARCH_MAX_TOKENS)
      .system(system)
      .addTool(WebSearchTool20260209.builder().maxUses(maxSearches).build())
      .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
      .addUserMessage(prompt)
    var params = base.build()
    var message = client().messages().create(params)
    var continuations = 0
    while (message.stopReason().orElse(null) == StopReason.PAUSE_TURN && continuations < MAX_CONTINUATIONS) {
      params = params.toBuilder().addMessage(message).build()
      message = client().messages().create(params)
      continuations++
    }
    message.text()
  }

  /** Opens the connection ahead of the first real request, so a spoken answer skips the TLS handshake. */
  suspend fun warmUp() {
    if (!isConfigured()) return
    runCatching {
      withContext(Dispatchers.IO) { client().models().list() }
    }.onFailure { Logger.d("ClaudeClient: warm-up failed (${it.javaClass.simpleName})") }
  }

  private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    try {
      block()
    } catch (e: UnauthorizedException) {
      throw IllegalStateException("Claude rejected the API key. Check it in Settings.", e)
    } catch (e: AnthropicServiceException) {
      Logger.w(e, "ClaudeClient: request failed with ${e.statusCode()}")
      throw e
    }
  }

  private companion object {
    const val DEFAULT_MAX_TOKENS = 2_048L
    const val JSON_MAX_TOKENS = 16_000L
    const val WRITE_MAX_TOKENS = 32_000L
    const val RESEARCH_MAX_TOKENS = 8_000L
    const val MAX_CONTINUATIONS = 3
    const val PDF_MAX_TOKENS = 32_000L
    const val MAX_PDF_BYTES = 30_000_000L
  }
}

/** All the text blocks of a reply, joined. A refusal or an empty reply is an error, not an empty answer. */
internal fun Message.text(): String {
  if (stopReason().orElse(null) == StopReason.REFUSAL) throw IllegalStateException("Claude declined this request")
  val text = content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("").trim()
  check(text.isNotEmpty()) { "Claude returned no text (stop reason ${stopReason().orElse(null)})" }
  return text
}

internal fun JsonElement.toJsonValue(): JsonValue = when (this) {
  is JsonNull -> JsonValue.from(null)
  is JsonObject -> JsonValue.from(mapValues { (_, value) -> value.toJsonValue() })
  is JsonArray -> JsonValue.from(map { it.toJsonValue() })
  is JsonPrimitive -> when {
    isString -> JsonValue.from(content)
    booleanOrNull != null -> JsonValue.from(booleanOrNull)
    longOrNull != null -> JsonValue.from(longOrNull)
    else -> JsonValue.from(doubleOrNull)
  }
}
