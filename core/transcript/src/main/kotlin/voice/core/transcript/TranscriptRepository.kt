package voice.core.transcript

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.data.Book
import voice.core.data.folders.AudiobookFolders
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk
import voice.core.logging.api.Logger
import java.io.FileNotFoundException

/**
 * Resolves the transcript for a book's current chapter file and slices out preceding
 * windows of it. Transcript JSONs are expected to sit somewhere inside one of the user's
 * configured audiobook folders, matching the audio file's basename (see [transcriptFor]) -
 * walking the whole folder (rather than requiring a direct-sibling match) tolerates books
 * that live in a subfolder of the configured root.
 *
 * Finding (a SAF folder walk), reading and parsing the transcript took about 7 s per lookup on a
 * Pixel and Auto-Identify does two, so the parsed cues of the current chapter file are kept in
 * memory. A transcript replaced on disk is picked up after the app process restarts.
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

  private val uriCache by lazy { context.getSharedPreferences("transcript_uris", Context.MODE_PRIVATE) }

  /**
   * Finds, reads and parses the transcript on [Dispatchers.IO]: on the main thread this froze
   * the app for 6-10 s (and far longer when memory was tight), which also stalled everything the
   * co-pilot does while waiting. Tried in order: the JSON sitting next to the audio file (no
   * folder walk), the location remembered from an earlier walk, then a walk of the audiobook
   * folders. Running out of memory counts as "no transcript" instead of crashing the app.
   */
  private suspend fun loadCues(chapterUri: Uri): List<TranscriptCue>? = withContext(Dispatchers.IO) {
    val startedAt = System.currentTimeMillis()
    try {
      siblingJson(chapterUri)?.let { sibling ->
        parse(sibling)?.let { cues ->
          Logger.d("Transcript loaded from the sibling file in ${System.currentTimeMillis() - startedAt} ms (${cues.size} cues)")
          return@withContext cues
        }
      }
      uriCache.getString(chapterUri.toString(), null)?.let { saved ->
        parse(Uri.parse(saved))?.let { cues ->
          Logger.d("Transcript loaded from remembered location in ${System.currentTimeMillis() - startedAt} ms (${cues.size} cues)")
          return@withContext cues
        }
      }
      val audioFile = documentFileFactory.create(chapterUri)
      val targetBaseName = audioFile.nameWithoutExtension()

      val folders = audiobookFolders.all().first().values.flatten()
      for (folder in folders) {
        val match = folder.documentFile.walk().firstOrNull {
          it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true && it.nameWithoutExtension() == targetBaseName
        }
        if (match != null) {
          val foundAt = System.currentTimeMillis()
          return@withContext parse(match.uri)?.also {
            uriCache.edit().putString(chapterUri.toString(), match.uri.toString()).apply()
            Logger.d(
              "Transcript loaded: find ${foundAt - startedAt} ms, read and parse " +
                "${System.currentTimeMillis() - foundAt} ms (${it.size} cues)",
            )
          }
        }
      }
      null
    } catch (e: OutOfMemoryError) {
      Logger.w(e, "Out of memory loading the transcript for $chapterUri")
      null
    }
  }

  /** `<same folder>/<same name>.json` for providers whose document ids are paths (local storage). */
  private fun siblingJson(chapterUri: Uri): Uri? = try {
    val documentId = DocumentsContract.getDocumentId(chapterUri)
    if (documentId.substringAfterLast('/').contains('.')) {
      DocumentsContract.buildDocumentUriUsingTree(chapterUri, documentId.substringBeforeLast('.') + ".json")
    } else {
      null
    }
  } catch (e: IllegalArgumentException) {
    null
  }

  /** Parses straight from the stream, so the 2-4 MB file is never held in memory as one string. */
  private fun parse(uri: Uri): List<TranscriptCue>? = try {
    context.contentResolver.openInputStream(uri)?.use { TranscriptParser.parse(it) }
  } catch (e: Exception) {
    // The sibling guess simply not existing is expected; anything else is worth a line.
    if (e !is FileNotFoundException) Logger.w(e, "Could not load transcript at $uri")
    null
  }

  private companion object {
    const val MAX_CACHED_CHAPTERS = 1
  }
}
