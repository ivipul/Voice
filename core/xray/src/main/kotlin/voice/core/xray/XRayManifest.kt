package voice.core.xray

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XRayManifest(
  val entities: List<XRayEntityInfo>,
  val timeline: List<XRayEntityCue>,
)

@Serializable
data class XRayEntityInfo(
  val id: String,
  val title: String,
  val description: String,
  val image: String? = null,
)

@Serializable
data class XRayEntityCue(
  val entity: String,
  @SerialName("start_ms") val startMs: Long,
  @SerialName("end_ms") val endMs: Long,
)

/**
 * Entities whose cue range contains [positionMs], in the order they first appear in [XRayManifest.entities].
 */
fun XRayManifest.activeEntities(positionMs: Long): List<XRayEntityInfo> {
  val activeIds = timeline.filter { positionMs in it.startMs..it.endMs }.map(XRayEntityCue::entity).toSet()
  return entities.filter { it.id in activeIds }
}
