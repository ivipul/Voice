package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import voice.core.data.BookId

/**
 * Holds the co-pilot chat history for the Feed overlay, keyed by book.
 * In-memory only for now: history does not survive a process death.
 */
@SingleIn(AppScope::class)
@Inject
class CoPilotRepository {

  private val messagesByBook = MutableStateFlow<Map<BookId, List<CoPilotMessage>>>(emptyMap())

  fun addMessage(bookId: BookId, message: CoPilotMessage) {
    messagesByBook.value = messagesByBook.value.toMutableMap().apply {
      put(bookId, (get(bookId).orEmpty() + message))
    }
  }

  fun updateMessage(bookId: BookId, messageId: String, transform: (CoPilotMessage) -> CoPilotMessage) {
    messagesByBook.update { all ->
      val messages = all[bookId] ?: return@update all
      all + (bookId to messages.map { if (it.id == messageId) transform(it) else it })
    }
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
