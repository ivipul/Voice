package voice.core.xray

import kotlinx.serialization.json.Json

object XRayParser {

  private val json = Json { ignoreUnknownKeys = true }

  fun parse(jsonContent: String): XRayManifest = json.decodeFromString(jsonContent)
}
