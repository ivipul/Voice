package voice.core.companion

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A small builder for the JSON Schemas Claude's structured output takes. Every property is required and no others
 * are allowed, which is what structured output expects.
 */
object Schema {

  fun obj(vararg properties: Pair<String, JsonObject>): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", JsonObject(properties.toMap()))
    put("required", buildJsonArray { properties.forEach { add(JsonPrimitive(it.first)) } })
    put("additionalProperties", false)
  }

  fun string(description: String? = null): JsonObject = typed("string", description)

  fun boolean(description: String? = null): JsonObject = typed("boolean", description)

  fun integer(description: String? = null): JsonObject = typed("integer", description)

  fun enum(values: List<String>, description: String? = null): JsonObject = buildJsonObject {
    put("type", "string")
    put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    if (description != null) put("description", description)
  }

  fun array(items: JsonObject, description: String? = null): JsonObject = buildJsonObject {
    put("type", "array")
    put("items", items)
    if (description != null) put("description", description)
  }

  /** A string that may also be null. */
  fun nullableString(description: String? = null): JsonObject = buildJsonObject {
    put("type", buildJsonArray { add(JsonPrimitive("string")); add(JsonPrimitive("null")) })
    if (description != null) put("description", description)
  }

  private fun typed(type: String, description: String?): JsonObject = buildJsonObject {
    put("type", type)
    if (description != null) put("description", description)
  }
}
