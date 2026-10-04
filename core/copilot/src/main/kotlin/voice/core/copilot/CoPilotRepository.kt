package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.StoredCoPilotMessage
import voice.core.data.repo.internals.dao.CoPilotMessageDao
import voice.core.logging.api.Logger
import java.io.File

/**
 * Holds the co-pilot chat history for the Feed overlay, keyed by book. The history is kept in memory for the
 * screens and written through to the app database, so it survives app updates and process death.
 */
@SingleIn(AppScope::class)
class CoPilotRepository internal constructor(
  private val dao: CoPilotMessageDao,
  private val scope: CoroutineScope,
  private val imageExists: (String) -> Boolean,
) {

  @Inject
  constructor(dao: CoPilotMessageDao) : this(dao, CoroutineScope(SupervisorJob() + Dispatchers.IO), { File(it).isFile })

  /** A repository that does not save anything, for tests of code that only reads or adds messages. */
  constructor() : this(NoHistoryDao, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { true })

  private val messagesByBook = MutableStateFlow<Map<BookId, List<CoPilotMessage>>>(emptyMap())

  private val writes = Channel<Pair<BookId, CoPilotMessage>>(Channel.UNLIMITED)
  private val orders = mutableMapOf<String, Long>()
  private var lastOrder = -1L

  private val loadJob = scope.launch { load() }

  init {
    scope.launch {
      loadJob.join()
      for ((bookId, message) in writes) {
        runCatching { dao.upsert(message.toStored(bookId, orders.getOrPut(message.id) { ++lastOrder })) }
          .onFailure { Logger.w(it, "Could not save co-pilot message ${message.id}") }
      }
    }
  }

  private suspend fun load() {
    val stored = runCatching { dao.all() }
      .onFailure { Logger.w(it, "Could not load the co-pilot history") }
      .getOrDefault(emptyList())
    stored.forEach { orders[it.id] = it.orderIndex }
    lastOrder = stored.maxOfOrNull { it.orderIndex } ?: -1L
    val loaded = stored.groupBy({ it.bookId }, { it.toMessage(imageExists) })
    // Anything added while the history was loading came after it.
    messagesByBook.update { added ->
      (loaded.keys + added.keys).associateWith { bookId ->
        val saved = loaded[bookId].orEmpty()
        val savedIds = saved.mapTo(mutableSetOf()) { it.id }
        saved + added[bookId].orEmpty().filter { it.id !in savedIds }
      }
    }
  }

  fun addMessage(bookId: BookId, message: CoPilotMessage) {
    messagesByBook.update { all -> all + (bookId to (all[bookId].orEmpty() + message)) }
    writes.trySend(bookId to message)
  }

  fun updateMessage(bookId: BookId, messageId: String, transform: (CoPilotMessage) -> CoPilotMessage) {
    var updated: CoPilotMessage? = null
    messagesByBook.update { all ->
      val messages = all[bookId] ?: return@update all
      all + (bookId to messages.map { if (it.id == messageId) transform(it).also { new -> updated = new } else it })
    }
    updated?.let { writes.trySend(bookId to it) }
  }

  val allMessagesByBook: StateFlow<Map<BookId, List<CoPilotMessage>>> get() = messagesByBook.asStateFlow()

  // No replay: a snip that happens while no screen is collecting (app in the background or screen
  // off) is dropped instead of showing up later.
  private val snipEventFlow = MutableSharedFlow<SnipEvent>(extraBufferCapacity = SNIP_EVENT_BUFFER)

  val snipEvents: SharedFlow<SnipEvent> get() = snipEventFlow.asSharedFlow()

  fun emitSnipEvent(event: SnipEvent) {
    snipEventFlow.tryEmit(event)
  }

  private companion object {
    const val SNIP_EVENT_BUFFER = 8
  }
}

private fun CoPilotMessage.toStored(bookId: BookId, orderIndex: Long) = StoredCoPilotMessage(
  id = id,
  bookId = bookId,
  role = role.name,
  text = text,
  timestampMs = timestampMs,
  isVisualPriority = isVisualPriority,
  imagePath = imagePath,
  snipChapterId = snipChapterId,
  snipPositionInChapterMs = snipPositionInChapterMs,
  orderIndex = orderIndex,
)

/** A drawing that was in progress when the app stopped never finishes, and an image file may have been removed. */
private fun StoredCoPilotMessage.toMessage(imageExists: (String) -> Boolean) = CoPilotMessage(
  id = id,
  role = CoPilotMessage.Role.entries.firstOrNull { it.name == role } ?: CoPilotMessage.Role.CoPilot,
  text = text,
  timestampMs = timestampMs,
  isVisualPriority = isVisualPriority,
  imagePath = imagePath?.takeIf(imageExists),
  isGeneratingImage = false,
  snipChapterId = snipChapterId,
  snipPositionInChapterMs = snipPositionInChapterMs,
)

private object NoHistoryDao : CoPilotMessageDao() {
  override suspend fun all(): List<StoredCoPilotMessage> = emptyList()
  override suspend fun upsert(message: StoredCoPilotMessage) = Unit
}
