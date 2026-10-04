package voice.core.strips

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import voice.core.data.leadingBookNumber
import voice.core.data.withoutBookNumber

@Serializable
data class StripManifest(
  @SerialName("schema_version") val schemaVersion: Int,
  val book: Int,
  @SerialName("book_title_prefix") val bookTitlePrefix: String,
  val chapter: Int,
  val title: String,
  val summary: String,
  @SerialName("hero_frames") val heroFrames: List<Int> = emptyList(),
  @SerialName("last_frame_hold_ms") val lastFrameHoldMs: Long,
  /** The composed full strip, shown as a final frame after the last comic frame; null when there is none. */
  @SerialName("strip_image") val stripImage: String? = null,
  val frames: List<StripFrame>,
)

@Serializable
data class StripFrame(
  val frame: Int,
  val image: String,
  @SerialName("start_ms") val startMs: Long,
)

fun StripManifest.heroFrame(): StripFrame {
  val heroNumber = heroFrames.firstOrNull()
  return frames.firstOrNull { it.frame == heroNumber } ?: frames.first()
}

fun StripManifest.matchesBook(bookTitle: String): Boolean {
  if (!withoutBookNumber(bookTitle).startsWith(bookTitlePrefix, ignoreCase = true)) return false
  val bookNumber = leadingBookNumber(bookTitle)
    ?: BOOK_NUMBER.find(bookTitle)?.groupValues?.get(1)?.toIntOrNull()
    ?: return true
  return bookNumber == book
}

private val BOOK_NUMBER = Regex("""Book (\d+)""")
