package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
}
