package voice.core.strips

import android.content.Context
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import voice.core.logging.api.Logger
import java.io.File

data class AvailableStrip(
  val manifest: StripManifest,
  val directory: File,
) {
  fun imageFile(frame: StripFrame): File = File(directory, frame.image)
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
      AvailableStrip(manifest, directory)
    } catch (e: Exception) {
      Logger.w(e, "Could not read strip manifest at $file")
      null
    }
  }

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
    const val STRIPS_DIR = "strips"
    const val MANIFEST_NAME = "strip.json"
  }
}
