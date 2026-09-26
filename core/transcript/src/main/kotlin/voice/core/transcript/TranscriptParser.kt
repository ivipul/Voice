package voice.core.transcript

import kotlinx.serialization.json.Json

object TranscriptParser {

  private val json = Json { ignoreUnknownKeys = true }

  fun parse(jsonContent: String): List<TranscriptCue> = json.decodeFromString(jsonContent)
}
