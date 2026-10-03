package voice.core.xray.card

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Book
import voice.core.data.folders.AudiobookFolders
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk
import voice.core.logging.api.Logger

/**
 * The player cards of one audio file, plus a way to turn the relative image paths inside them into URIs.
 */
class PlayerCardSet(
  private val cards: Map<String, PlayerCardData>,
  private val imageFolder: CachedDocumentFile?,
) {

  fun card(entityId: String): PlayerCardData? = cards[entityId]

  fun imageUri(relativePath: String): Uri? {
    var current = imageFolder ?: return null
    for (segment in relativePath.split('/').filter(String::isNotEmpty)) {
      current = current.children.firstOrNull { it.name == segment } ?: return null
    }
    return current.takeIf { it.isFile }?.uri
  }
}

/**
 * Resolves the player cards for a book's current chapter file. A `<audio file base name>.cards.json` is expected
 * somewhere inside one of the user's configured audiobook folders (same lookup as [voice.core.xray.XRayRepository]),
 * with the card art in a sibling folder called `<audio file base name>.cards`, so the art stays out of git.
 */
@Inject
class PlayerCardRepository(
  private val documentFileFactory: CachedDocumentFileFactory,
  private val audiobookFolders: AudiobookFolders,
  private val context: Context,
) {

  private val cache = mutableMapOf<Uri, PlayerCardSet?>()

  suspend fun cardsFor(book: Book): PlayerCardSet? {
    val chapterUri = book.currentChapter.id.toUri()
    return cache.getOrPut(chapterUri) { load(chapterUri) }
  }

  private suspend fun load(chapterUri: Uri): PlayerCardSet? {
    val baseName = documentFileFactory.create(chapterUri).nameWithoutExtension()
    val manifestName = "$baseName.cards.json"
    val imageFolderName = "$baseName.cards"

    val folders = audiobookFolders.all().first().values.flatten()
    for (folder in folders) {
      var manifestFile: CachedDocumentFile? = null
      var imageFolder: CachedDocumentFile? = null
      for (file in folder.documentFile.walk()) {
        if (manifestFile == null && file.isFile && file.name == manifestName) manifestFile = file
        if (imageFolder == null && file.isDirectory && file.name == imageFolderName) imageFolder = file
        if (manifestFile != null && imageFolder != null) break
      }
      if (manifestFile != null) {
        val manifest = readJson(manifestFile.uri)?.let(::parse) ?: return null
        return PlayerCardSet(
          cards = manifest.cards.associateBy(PlayerCardData::id),
          imageFolder = imageFolder,
        )
      }
    }
    return null
  }

  private fun parse(json: String): PlayerCardManifest? = try {
    PlayerCardParser.parse(json)
  } catch (e: Exception) {
    Logger.w(e, "Could not parse player cards")
    null
  }

  private fun readJson(uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
  } catch (e: Exception) {
    Logger.w(e, "Could not read player cards at $uri")
    null
  }
}
