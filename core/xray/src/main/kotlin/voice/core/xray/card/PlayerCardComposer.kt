package voice.core.xray.card

enum class CardStat(val abbreviation: String, val field: String) {
  Strength("STR", PlayerCardFields.STAT_STR),
  Dexterity("DEX", PlayerCardFields.STAT_DEX),
  Constitution("CON", PlayerCardFields.STAT_CON),
  Intelligence("INT", PlayerCardFields.STAT_INT),
  Charisma("CHA", PlayerCardFields.STAT_CHA),
}

/**
 * A card as it is known at one playback position. Everything not yet revealed is `null`, empty or a locked [Stat].
 */
data class ComposedPlayerCard(
  val id: String,
  val crawlerId: String?,
  val epithet: String?,
  val callSign: String?,
  val race: String?,
  val level: Int?,
  val floor: Int?,
  val floorName: String?,
  val stats: List<Stat>,
  val abilities: List<Ability>,
  val loadout: List<GearItem>,
  val look: Look?,
) {

  data class Stat(val stat: CardStat, val value: Int?, val modifier: String?) {
    val locked: Boolean get() = value == null
  }

  data class Ability(val name: String, val rank: Int?)

  data class GearItem(val slot: String, val name: String)

  data class Look(val image: String?, val description: String?)
}

/**
 * Takes the latest entry per field (per key for skills and gear) at or before [positionMs]. Gear and skills
 * persist until a later entry replaces or clears them. Without a look at or before [positionMs], the earliest
 * look is used so the art window is never empty.
 */
fun PlayerCardData.composeAt(positionMs: Long): ComposedPlayerCard {
  val ordered = entries.sortedBy(PlayerCardEntry::ms)
  val latest = LinkedHashMap<Pair<String, String?>, PlayerCardEntry>()
  ordered.takeWhile { it.ms <= positionMs }.forEach { entry ->
    latest[entry.field to entry.key?.trim()?.lowercase()] = entry
  }

  fun value(field: String): String? = latest[field to null]?.value?.trim()?.takeIf(String::isNotEmpty)

  val classValue = value(PlayerCardFields.CLASS)
  val callSign = value(PlayerCardFields.CALL_SIGN)
  val floorEntry = latest[PlayerCardFields.FLOOR to null]
  val look = latest[PlayerCardFields.LOOK to null]
    ?: ordered.firstOrNull { it.field == PlayerCardFields.LOOK && it.image != null }

  return ComposedPlayerCard(
    id = id,
    crawlerId = crawlerId,
    epithet = classValue ?: callSign,
    callSign = callSign.takeIf { classValue != null },
    race = value(PlayerCardFields.RACE),
    level = value(PlayerCardFields.LEVEL)?.toIntOrNull(),
    floor = floorEntry?.value?.trim()?.toIntOrNull(),
    floorName = floorEntry?.note?.trim()?.takeIf(String::isNotEmpty),
    stats = CardStat.entries.map { stat ->
      val entry = latest[stat.field to null]
      ComposedPlayerCard.Stat(
        stat = stat,
        value = entry?.value?.trim()?.toIntOrNull(),
        modifier = entry?.modifier?.trim()?.takeIf(String::isNotEmpty),
      )
    },
    abilities = latest.values
      .filter { it.field == PlayerCardFields.SKILL && !it.value.isNullOrBlank() && !it.key.isNullOrBlank() }
      .sortedWith(compareByDescending<PlayerCardEntry> { it.value?.trim()?.toIntOrNull() ?: 0 }.thenByDescending { it.ms })
      .map { ComposedPlayerCard.Ability(name = it.key!!.trim(), rank = it.value!!.trim().toIntOrNull()) },
    loadout = latest.values
      .filter { it.field == PlayerCardFields.GEAR && !it.value.isNullOrBlank() && !it.key.isNullOrBlank() }
      .map { ComposedPlayerCard.GearItem(slot = it.key!!.trim(), name = it.value!!.trim()) },
    look = look?.let { ComposedPlayerCard.Look(image = it.image, description = it.note) },
  )
}

private val gameStatFields = setOf(
  PlayerCardFields.LEVEL,
  PlayerCardFields.FLOOR,
  PlayerCardFields.STAT_STR,
  PlayerCardFields.STAT_DEX,
  PlayerCardFields.STAT_CON,
  PlayerCardFields.STAT_INT,
  PlayerCardFields.STAT_CHA,
  PlayerCardFields.SKILL,
  PlayerCardFields.GEAR,
)

/**
 * Whether this card ever has level, floor, stats, skills or gear. A card without any (a monster, an NPC) is shown as
 * a plain look-only card instead of a sheet of locked stats.
 */
val PlayerCardData.hasGameStats: Boolean get() = entries.any { it.field in gameStatFields }
