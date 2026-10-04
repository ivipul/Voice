package voice.core.xray

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.Book
import voice.core.data.folders.AudiobookFolders
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.core.logging.api.Logger

/**
 * Resolves the X-Ray manifest (entity/character chip timeline + info cards) for a book's
 * current chapter file. A manifest is expected to sit somewhere inside one of the user's
 * configured audiobook folders, named `<audio file base name>.xray.json` (mirrors
 * [voice.core.transcript.TranscriptRepository]'s lookup, but with a distinct suffix so the
 * two don't collide on the same base name).
 */
@SingleIn(AppScope::class)
@Inject
class XRayRepository(
  private val documentFileFactory: CachedDocumentFileFactory,
  private val audiobookFolders: AudiobookFolders,
  private val context: Context,
) {

  private val manifestCache = SingleFlightCache<Uri, XRayManifest>()

  suspend fun manifestFor(book: Book): XRayManifest? {
    val chapterUri = book.currentChapter.id.toUri()
    return manifestCache.get(chapterUri) { loadManifest(chapterUri) }
  }

  private suspend fun loadManifest(chapterUri: Uri): XRayManifest? {
    val audioFile = documentFileFactory.create(chapterUri)
    val targetFileName = "${audioFile.nameWithoutExtension()}.xray.json"

    val folders = audiobookFolders.all().first().values.flatten()
    for (folder in folders) {
      val match = folder.documentFile.breadthFirst().firstOrNull {
        it.isFile && it.name == targetFileName
      }
      if (match != null) {
        return readJson(match.uri)?.let { json ->
          try {
            XRayParser.parse(json)
          } catch (e: Exception) {
            Logger.w(e, "Could not parse X-Ray manifest at ${match.uri}")
            null
          }
        }
      }
    }
    return null
  }

  private fun readJson(uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
  } catch (e: Exception) {
    Logger.w(e, "Could not read X-Ray manifest at $uri")
    null
  }
}
