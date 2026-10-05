package voice.core.transcript

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

object TranscriptParser {

  private val json = Json { ignoreUnknownKeys = true }

  fun parse(jsonContent: String): List<TranscriptCue> = json.decodeFromString(jsonContent)

  /** Decodes while reading, so a multi-megabyte transcript is never held as one string. */
  @OptIn(ExperimentalSerializationApi::class)
  fun parse(stream: InputStream): List<TranscriptCue> = json.decodeFromStream(stream)
}
