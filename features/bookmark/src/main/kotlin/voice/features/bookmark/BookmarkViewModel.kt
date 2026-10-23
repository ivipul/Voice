package voice.features.bookmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.companion.CompanionStores
import voice.core.copilot.CoPilotRepository
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.bookPositionOf
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.ui.crawl.toStyle
import voice.navigation.Destination
import voice.navigation.Navigator

@AssistedInject
class BookmarkViewModel(
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  private val repo: BookRepository,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  private val copilotRepository: CoPilotRepository,
  private val stores: CompanionStores,
  @Assisted
  private val bookId: BookId,
) {

  private val scope = MainScope()
  private var chapters by mutableStateOf<List<Chapter>>(emptyList())
  private var savedBookPosition by mutableStateOf(0L)

  @Composable
  fun viewState(): BookmarkViewState {
    LaunchedEffect(bookId) {
      val book = repo.get(bookId)
      if (book != null) {
        chapters = book.chapters
        savedBookPosition = book.position
      }
    }
    val look by remember(bookId) { stores.look.flow(bookId) }.collectAsState(initial = null)
    val shelf by remember(bookId) { stores.shelf.flow(bookId) }.collectAsState(initial = null)
    val livePlayback by remember(bookId) { playerController.livePlaybackStateFlow(bookId) }
      .collectAsState(initial = null)
    val currentBookPosition = livePlayback?.let { chapters.bookPositionOf(it.chapterId, it.positionMs) }
      ?: savedBookPosition
    val messagesByBook by remember { copilotRepository.allMessagesByBook }.collectAsState()
    return BookmarkViewState(
      snips = messagesByBook[bookId].orEmpty().snipCards(chapters),
      extras = shelf?.extras.orEmpty().extraCards(currentBookPosition),
      bookStyle = look?.toStyle(),
    )
  }

  fun onExtraClick(extra: ExtraCardViewState) {
    if (extra.locked) return
    navigator.goTo(Destination.Extra(bookId, extra.id))
  }

  fun onMoreExtrasClick() {
    navigator.goTo(Destination.BookSetup(bookId))
  }

  /** Plays on from where the snip was taken. */
  fun onSnipClick(snip: SnipCardViewState) {
    val wasPlaying = playStateManager.playState == PlayStateManager.PlayState.Playing
    scope.launch {
      currentBookStore.updateData { bookId }
    }
    playerController.setPosition(snip.positionInChapterMs, snip.chapterId)
    if (wasPlaying) {
      playerController.play()
    }
  }

  fun onSnipDelete(snip: SnipCardViewState) {
    copilotRepository.removeMessage(bookId, snip.id)
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): BookmarkViewModel
  }
}
