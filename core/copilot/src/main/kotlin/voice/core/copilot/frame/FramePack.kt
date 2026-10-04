package voice.core.copilot.frame

import android.content.Context
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import voice.core.data.withoutBookNumber
import voice.core.logging.api.Logger
import java.io.File

/** One look of a reference character: where it starts in the book, and its two reference images. */
internal data class FrameLook(
  val seq: Int,
  val firstMs: Long,
  val description: String?,
  val standing: File?,
  val action: File?,
) {
  val hasImages: Boolean get() = standing != null || action != null
  val references: List<File> get() = listOfNotNull(standing, action)
}

internal data class FrameCharacter(val name: String, val slug: String, val looks: List<FrameLook>) {

  /**
   * The look in force at [positionMs] (latest look at or before it, never defaulting to look 1
   * for a later moment). A look with no image on disk falls back to the latest earlier look that
   * has one, then to the earliest that has one.
   */
  fun lookAt(positionMs: Long): FrameLook? {
    val withImages = looks.filter { it.hasImages }.sortedBy { it.firstMs }
    return withImages.lastOrNull { it.firstMs <= positionMs } ?: withImages.firstOrNull()
  }
}

internal class FramePackData(
  val bookTitle: String?,
  val styleImages: List<File>,
  val characters: List<FrameCharacter>,
)

@Serializable
private data class LooksFile(
  @SerialName("book_title") val bookTitle: String? = null,
  val characters: List<LooksFileCharacter> = emptyList(),
)

@Serializable
private data class LooksFileCharacter(val name: String, val slug: String, val looks: List<LooksFileLook> = emptyList())

@Serializable
private data class LooksFileLook(
  val seq: Int,
  @SerialName("first_ms") val firstMs: Long,
  val description: String? = null,
  val standing: String? = null,
  val action: String? = null,
)

/**
 * Reads the frame pack from `<external files dir>/frame-pack` on the phone, put there by
 * `scripts/frame_pack/build_frame_pack.py` (like the X-Ray assets, it is local data, not bundled). The
 * looks apply only when the playing book's title starts with `book_title`:
 *
 *     style/                     the style reference images (png or jpg)
 *     looks.json                 {book_title, characters: [{name, slug, looks: [{seq, first_ms, description, standing, action}]}]}
 *     images/<slug>/look-NNN-standing.png and look-NNN-action.png
 */
@Inject
class FramePack(private val context: Context) {

  private val root: File get() = File(context.getExternalFilesDir(null), PACK_DIR)

  /** Style references are the style lock; without them a frame would not match the strip, so none is drawn. */
  fun isAvailable(): Boolean = styleImages().isNotEmpty()

  internal fun load(bookTitle: String): FramePackData {
    val looks = readLooks()
    // Prefix, so both the embedded title ("Dungeon Crawler Carl") and a file-name title
    // ("Dungeon Crawler Carl - Dungeon Crawler Carl, Book 1") match book 1 but not book 2.
    val matchesBook = looks?.bookTitle == null || withoutBookNumber(bookTitle).startsWith(looks.bookTitle, ignoreCase = true)
    val characters = if (matchesBook) looks?.characters.orEmpty().map(::toCharacter) else emptyList()
    return FramePackData(looks?.bookTitle, styleImages(), characters)
  }

  private fun styleImages(): List<File> =
    File(root, "style").listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS }
      ?.sortedBy { it.name }
      ?.take(MAX_STYLE_IMAGES)
      .orEmpty()

  private fun readLooks(): LooksFile? {
    val file = File(root, "looks.json").takeIf { it.isFile } ?: return null
    return try {
      LOOKS_JSON.decodeFromString(LooksFile.serializer(), file.readText())
    } catch (e: Exception) {
      Logger.w(e, "Could not read frame pack looks at $file")
      null
    }
  }

  private fun toCharacter(c: LooksFileCharacter) = FrameCharacter(
    name = c.name,
    slug = c.slug,
    looks = c.looks.map { look ->
      FrameLook(
        seq = look.seq,
        firstMs = look.firstMs,
        description = look.description,
        standing = look.standing?.let { File(root, it) }?.takeIf { it.isFile },
        action = look.action?.let { File(root, it) }?.takeIf { it.isFile },
      )
    },
  )

  private companion object {
    val LOOKS_JSON = Json { ignoreUnknownKeys = true }
    const val PACK_DIR = "frame-pack"
    const val MAX_STYLE_IMAGES = 3
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg")
  }
}
