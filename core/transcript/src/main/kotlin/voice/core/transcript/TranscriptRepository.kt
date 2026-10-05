package voice.core.transcript

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.data.Book
import voice.core.data.folders.AudiobookFolders
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk
import voice.core.logging.api.Logger

/**
 * Resolves the transcript for a book's current chapter file and slices out preceding
 * windows of it. Transcript JSONs are expected to sit somewhere inside one of the user's
 * configured audiobook folders, matching the audio file's basename (see [transcriptFor]) -
 * walking the whole folder (rather than requiring a direct-sibling match) tolerates books
 * that live in a subfolder of the configured root.
 *
 * Finding (a SAF folder walk), reading and parsing the transcript took about 7 s per lookup on a
 * Pixel and Auto-Identify does two, so the parsed cues are kept in memory per chapter file. A
 * transcript replaced on disk is picked up after the app process restarts.
 */
@SingleIn(AppScope::class)
@Inject
class TranscriptRepository(
  private val documentFileFactory: CachedDocumentFileFactory,
  private val audiobookFolders: AudiobookFolders,
  private val context: Context,
) {

  private val cacheLock = Mutex()
  private val cache = object : LinkedHashMap<String, List<TranscriptCue>>(MAX_CACHED_CHAPTERS, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TranscriptCue>>?) =
      size > MAX_CACHED_CHAPTERS
  }

  suspend fun textForPrecedingWindow(book: Book, windowMs: Long): String? =
    textForWindow(book, endPositionMs = book.content.positionInChapter, windowMs = windowMs)

  suspend fun textForWindow(
    book: Book,
    endPositionMs: Long,
    windowMs: Long,
  ): String? {
    val cues = cuesForCurrentChapter(book) ?: return null
    return cues.textForPrecedingWindow(endPositionMs, windowMs).takeUnless { it.isBlank() }
  }

  private suspend fun cuesForCurrentChapter(book: Book): List<TranscriptCue>? {
    val chapterUri = book.currentChapter.id.toUri()
    return cacheLock.withLock {
      cache[chapterUri.toString()] ?: loadCues(chapterUri)?.also { cache[chapterUri.toString()] = it }
    }
  }

  private suspend fun loadCues(chapterUri: Uri): List<TranscriptCue>? {
    val startedAt = System.currentTimeMillis()
    val audioFile = documentFileFactory.create(chapterUri)
    val targetBaseName = audioFile.nameWithoutExtension()

    val folders = audiobookFolders.all().first().values.flatten()
    for (folder in folders) {
      val match = folder.documentFile.walk().firstOrNull {
        it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true && it.nameWithoutExtension() == targetBaseName
      }
      if (match != null) {
        val foundAt = System.currentTimeMillis()
        return readJson(match.uri)?.let { json ->
          val readAt = System.currentTimeMillis()
          try {
            TranscriptParser.parse(json).also {
              Logger.d(
                "Transcript loaded: find ${foundAt - startedAt} ms, read ${readAt - foundAt} ms, " +
                  "parse ${System.currentTimeMillis() - readAt} ms (${it.size} cues)",
              )
            }
          } catch (e: Exception) {
            Logger.w(e, "Could not parse transcript at ${match.uri}")
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
    Logger.w(e, "Could not read transcript file at $uri")
    null
  }

  private companion object {
    const val MAX_CACHED_CHAPTERS = 2
  }
}
