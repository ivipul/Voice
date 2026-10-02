package voice.core.xray.card

import kotlinx.serialization.json.Json

object PlayerCardParser {

  private val json = Json { ignoreUnknownKeys = true }

  fun parse(jsonContent: String): PlayerCardManifest = json.decodeFromString(jsonContent)
}
