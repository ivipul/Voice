package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import androidx.datastore.core.DataStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the "listening session" boundary Catch-Me-Up needs: when this session began, and
 * where each book's position stood at that moment (i.e. where the *previous* session left
 * off). A session here is approximated as "since this app process/service started" - there's
 * no explicit session-close event to key off, and that approximation matches how a listener
 * would actually describe "since I started listening again".
 *
 * The snapshot is taken eagerly at construction (this class is [SingleIn AppScope], created
 * once early in the app's life - see [RealCoPilotPipeline] and [voice.core.playback.session
 * .CoPilotEngine], both constructed near app/service startup) rather than lazily on first use,
 * so it reflects the position *before* any listening happened in this session.
 */
@SingleIn(AppScope::class)
@Inject
class CoPilotSessionTracker(
  private val bookRepository: BookRepository,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val sessionStartAtMs = System.currentTimeMillis()
  private val previousSessionEndPositionMsByBook = ConcurrentHashMap<BookId, Long>()

  init {
    scope.launch {
      currentBookStoreId.data.first()?.let { snapshotIfNeeded(it) }
    }
  }

  suspend fun snapshotIfNeeded(bookId: BookId) {
    if (previousSessionEndPositionMsByBook.containsKey(bookId)) return
    val position = bookRepository.get(bookId)?.position ?: return
    previousSessionEndPositionMsByBook.putIfAbsent(bookId, position)
  }

  fun isWithinFirst30SecondsOfSession(): Boolean = System.currentTimeMillis() - sessionStartAtMs <= 30_000L

  fun previousSessionEndPositionMs(bookId: BookId): Long = previousSessionEndPositionMsByBook[bookId] ?: 0L
}
