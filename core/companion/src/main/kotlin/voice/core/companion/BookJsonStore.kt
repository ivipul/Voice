package voice.core.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import voice.core.data.BookId
import voice.core.logging.api.Logger
import java.io.File

internal val companionJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
  explicitNulls = false
  coerceInputValues = true
}

/**
 * One JSON file per book in the book's folder, read once and kept in memory, so screens can observe it and
 * background jobs can update it. Writes go to a temporary file first, so a crash never leaves half a file.
 */
class BookJsonStore<T : Any>(
  private val dirFor: (BookId) -> File,
  private val fileName: String,
  private val serializer: KSerializer<T>,
  private val default: () -> T,
) {

  private val lock = Mutex()
  private val values = MutableStateFlow<Map<BookId, T>>(emptyMap())

  fun flow(bookId: BookId): Flow<T> = values
    .onStart { ensureLoaded(bookId) }
    .map { it[bookId] ?: default() }
    .distinctUntilChanged()

  suspend fun get(bookId: BookId): T {
    ensureLoaded(bookId)
    return values.value[bookId] ?: default()
  }

  /** Applies [transform] to the current value and saves the result; returns it. */
  @IgnorableReturnValue
  suspend fun update(bookId: BookId, transform: (T) -> T): T = lock.withLock {
    val current = values.value[bookId] ?: read(bookId)
    val updated = transform(current)
    if (updated != current) {
      write(bookId, updated)
    }
    values.update { it + (bookId to updated) }
    updated
  }

  @IgnorableReturnValue
  suspend fun set(bookId: BookId, value: T): T = update(bookId) { value }

  private suspend fun ensureLoaded(bookId: BookId) {
    if (bookId in values.value) return
    lock.withLock {
      if (bookId in values.value) return
      val loaded = read(bookId)
      values.update { it + (bookId to loaded) }
    }
  }

  private suspend fun read(bookId: BookId): T = withContext(Dispatchers.IO) {
    val file = File(dirFor(bookId), fileName)
    if (!file.isFile) return@withContext default()
    try {
      companionJson.decodeFromString(serializer, file.readText())
    } catch (e: Exception) {
      Logger.w(e, "Could not read $file, starting fresh")
      default()
    }
  }

  private suspend fun write(bookId: BookId, value: T) = withContext(Dispatchers.IO) {
    val file = File(dirFor(bookId), fileName)
    val temp = File(file.parentFile, "$fileName.tmp")
    temp.writeText(companionJson.encodeToString(serializer, value))
    if (!temp.renameTo(file)) {
      file.delete()
      check(temp.renameTo(file)) { "Could not save $file" }
    }
  }
}
