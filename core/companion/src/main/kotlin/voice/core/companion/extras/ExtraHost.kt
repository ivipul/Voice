package voice.core.companion.extras

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import voice.core.companion.BookKind
import voice.core.companion.CompanionStores
import voice.core.companion.companionJson
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.chapterPositionOf
import voice.core.transcript.BookFiles
import voice.core.transcript.TranscriptCue
import voice.core.transcript.TranscriptRepository
import voice.core.logging.api.Logger
import java.io.File

/** What `earwig.book()` returns to a page. */
@Serializable
data class ExtraBookInfo(
  val title: String,
  val author: String,
  val kind: String,
  val positionMs: Long,
  val durationMs: Long,
  val summary: String,
)

/**
 * The app side of the bridge a shelf Extra talks to: book facts, the transcript heard so far, and the values the page
 * saves for itself. Asking Claude goes through the player's co-pilot instead, so answers stay spoiler-safe.
 */
@Inject
class ExtraHost(
  private val stores: CompanionStores,
  private val transcriptRepository: TranscriptRepository,
  private val bookFiles: BookFiles,
) {

  private val valuesLock = Mutex()

  suspend fun bookInfo(book: Book, positionMs: Long): String {
    val profile = stores.profile.get(book.id)
    val info = ExtraBookInfo(
      title = profile.title.ifBlank { book.content.name },
      author = profile.author.ifBlank { book.content.author.orEmpty() },
      kind = if (profile.kind == BookKind.Fiction) "fiction" else "nonfiction",
      positionMs = positionMs,
      durationMs = book.duration,
      summary = profile.summary,
    )
    return Json.encodeToString(ExtraBookInfo.serializer(), info)
  }

  /** The last [maxChars] characters of what the listener has heard, read back from [positionMs] in the whole book. */
  suspend fun transcriptSoFar(book: Book, positionMs: Long, maxChars: Int): String {
    val limit = maxChars.coerceIn(0, MAX_TRANSCRIPT_CHARS)
    if (limit == 0) return ""
    val (currentChapter, positionInChapter) = book.chapters.chapterPositionOf(positionMs)
      ?: (book.chapters.last().id to book.chapters.last().duration)
    val upTo = book.chapters.indexOfFirst { it.id == currentChapter }
    val parts = ArrayDeque<String>()
    var length = 0
    for (index in upTo downTo 0) {
      val chapter = book.chapters[index]
      val cues = transcriptRepository.cues(chapter.id).orEmpty()
      val heard = if (index == upTo) cues.heardBy(positionInChapter) else cues
      for (cue in heard.asReversed()) {
        parts.addFirst(cue.text.trim())
        length += cue.text.length + 1
        if (length >= limit) return parts.joinToString(" ").takeLast(limit)
      }
    }
    return parts.joinToString(" ").takeLast(limit)
  }

  suspend fun savedValue(bookId: BookId, extraId: String, key: String): String? = valuesLock.withLock {
    readValues(bookId, extraId)[key]
  }

  suspend fun saveValue(bookId: BookId, extraId: String, key: String, value: String) {
    if (key.length > MAX_KEY_CHARS || value.length > MAX_VALUE_CHARS) {
      Logger.w("Explorable $extraId tried to save a value that is too big")
      return
    }
    valuesLock.withLock {
      val values = readValues(bookId, extraId)
      if (key !in values && values.size >= MAX_KEYS) {
        Logger.w("Explorable $extraId has too many saved values")
        return
      }
      writeValues(bookId, extraId, values + (key to value))
    }
  }

  /** Forgets everything a deleted Extra saved. */
  suspend fun clearValues(bookId: BookId, extraId: String) {
    valuesLock.withLock {
      val _ = withContext(Dispatchers.IO) { valuesFile(bookId, extraId).delete() }
    }
  }

  private suspend fun readValues(bookId: BookId, extraId: String): Map<String, String> = withContext(Dispatchers.IO) {
    val file = valuesFile(bookId, extraId)
    if (!file.isFile) return@withContext emptyMap()
    runCatching { companionJson.decodeFromString(ValuesSerializer, file.readText()) }.getOrElse { emptyMap() }
  }

  private suspend fun writeValues(bookId: BookId, extraId: String, values: Map<String, String>) {
    withContext(Dispatchers.IO) {
      val file = valuesFile(bookId, extraId)
      val pending = File(file.parentFile, "${file.name}.tmp")
      pending.writeText(companionJson.encodeToString(ValuesSerializer, values))
      if (!pending.renameTo(file)) {
        val _ = file.delete()
        check(pending.renameTo(file)) { "Could not save $file" }
      }
    }
  }

  private fun valuesFile(bookId: BookId, extraId: String): File {
    val safeId = extraId.filter { it.isLetterOrDigit() || it == '-' }
    return File(bookFiles.bookDir(bookId), "extra-data/$safeId.json").apply { parentFile?.mkdirs() }
  }

  private companion object {
    const val MAX_TRANSCRIPT_CHARS = 200_000
    const val MAX_KEYS = 200
    const val MAX_KEY_CHARS = 200
    const val MAX_VALUE_CHARS = 50_000
    val ValuesSerializer = MapSerializer(String.serializer(), String.serializer())
  }
}

internal fun List<TranscriptCue>.heardBy(positionMs: Long): List<TranscriptCue> = takeWhile { it.timestampStartMs <= positionMs }
