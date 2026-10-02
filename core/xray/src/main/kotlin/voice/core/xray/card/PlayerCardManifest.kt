package voice.core.xray.card

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The contents of a `<audio file base name>.cards.json` file. Cards are keyed by the same entity ids as
 * [voice.core.xray.XRayManifest.entities].
 */
@Serializable
data class PlayerCardManifest(
  val cards: List<PlayerCardData>,
)

@Serializable
data class PlayerCardData(
  val id: String,
  @SerialName("crawler_id") val crawlerId: String? = null,
  val entries: List<PlayerCardEntry>,
)

/**
 * One field value becoming true at [ms] (book-relative). A `null` [value] for a [PlayerCardFields.SKILL] or
 * [PlayerCardFields.GEAR] entry means the skill is lost or the slot emptied.
 *
 * - [PlayerCardFields.LEVEL], [PlayerCardFields.FLOOR]: [value] is the number, floor may carry its name in [note].
 * - `STAT_*`: [value] is the score, [modifier] its bonus, e.g. `+3`.
 * - [PlayerCardFields.SKILL]: [key] is the skill name, [value] the rank.
 * - [PlayerCardFields.GEAR]: [key] is the slot, [value] the item name.
 * - [PlayerCardFields.LOOK]: [image] is a path relative to the `<base>.cards` folder, [note] the description.
 */
@Serializable
data class PlayerCardEntry(
  val ms: Long,
  val field: String,
  val key: String? = null,
  val value: String? = null,
  val modifier: String? = null,
  val image: String? = null,
  val note: String? = null,
)

object PlayerCardFields {
  const val CALL_SIGN = "CALL_SIGN"
  const val CLASS = "CLASS"
  const val RACE = "RACE"
  const val LEVEL = "LEVEL"
  const val FLOOR = "FLOOR"
  const val STAT_STR = "STAT_STR"
  const val STAT_DEX = "STAT_DEX"
  const val STAT_CON = "STAT_CON"
  const val STAT_INT = "STAT_INT"
  const val STAT_CHA = "STAT_CHA"
  const val SKILL = "SKILL"
  const val GEAR = "GEAR"
  const val LOOK = "LOOK"
}
