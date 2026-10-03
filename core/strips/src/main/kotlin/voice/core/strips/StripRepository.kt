package voice.core.strips

import android.content.Context
import android.graphics.BitmapFactory
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import voice.core.logging.api.Logger
import java.io.File

data class AvailableStrip(
  val manifest: StripManifest,
  val directory: File,
  /** Width over height of the gallery image, or null when it could not be read. */
  val coverAspect: Float? = null,
) {
  fun imageFile(frame: StripFrame): File = File(directory, frame.image)

  fun stripImageFile(): File? = manifest.stripImage?.let { File(directory, it) }

  /** The gallery image: the full strip when there is one, else the hero frame. */
  fun coverFile(): File = stripImageFile() ?: imageFile(manifest.heroFrame())
}

@Inject
class StripRepository(private val context: Context) {

  suspend fun stripsFor(bookTitle: String): List<AvailableStrip> = withContext(Dispatchers.IO) {
    val root = context.getExternalFilesDir(STRIPS_DIR) ?: return@withContext emptyList()
    root.walkTopDown()
      .filter { it.isFile && it.name == MANIFEST_NAME }
      .mapNotNull(::readStrip)
      .filter { it.manifest.matchesBook(bookTitle) }
      .filter { strip -> strip.manifest.frames.all { strip.imageFile(it).isFile } }
      .sortedBy { it.manifest.chapter }
      .toList()
  }

  private fun readStrip(file: File): AvailableStrip? {
    val directory = file.parentFile ?: return null
    return try {
      val manifest = json.decodeFromString<StripManifest>(file.readText())
      if (manifest.frames.isEmpty()) return null
      val available = manifest.stripImage
        ?.takeUnless { File(directory, it).isFile }
        ?.let { manifest.copy(stripImage = null) }
        ?: manifest
      AvailableStrip(available, directory).let { it.copy(coverAspect = imageAspect(it.coverFile())) }
    } catch (e: Exception) {
      Logger.w(e, "Could not read strip manifest at $file")
      null
    }
  }

  private fun imageAspect(file: File): Float? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else null
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
    const val STRIPS_DIR = "strips"
    const val MANIFEST_NAME = "strip.json"
  }
}
