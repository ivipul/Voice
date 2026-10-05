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
import voice.core.copilot.CoPilotRepository
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.strips.AvailableStrip
import voice.core.strips.StripRepository
import voice.core.strips.bookPositionOf
import voice.core.strips.isUnlockedAt
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.crawlEditionOf
import voice.navigation.Navigator

@AssistedInject
class BookmarkViewModel(
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  private val repo: BookRepository,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  private val stripRepository: StripRepository,
  private val copilotRepository: CoPilotRepository,
  @Assisted
  private val bookId: BookId,
) {

  private val scope = MainScope()
  private var chapters by mutableStateOf<List<Chapter>>(emptyList())
  private var strips by mutableStateOf<List<AvailableStrip>>(emptyList())
  private var savedBookPosition by mutableStateOf(0L)
  private var unlockedStripChapters by mutableStateOf<Set<Int>>(emptySet())
  private var activeStrip by mutableStateOf<AvailableStrip?>(null)
  private var edition by mutableStateOf<CrawlEdition?>(null)

  @Composable
  fun viewState(): BookmarkViewState {
    LaunchedEffect(bookId) {
      val book = repo.get(bookId)
      if (book != null) {
        chapters = book.chapters
        savedBookPosition = book.position
        strips = stripRepository.stripsFor(book.content.name)
        edition = crawlEditionOf(book.content.name)
      }
    }
    val livePlayback by remember(bookId) { playerController.livePlaybackStateFlow(bookId) }
      .collectAsState(initial = null)
    val currentBookPosition = livePlayback?.let { chapters.bookPositionOf(it.chapterId, it.positionMs) }
      ?: savedBookPosition
    LaunchedEffect(currentBookPosition, strips) {
      val reached = strips.filter { it.isUnlockedAt(currentBookPosition) }.map { it.manifest.chapter }
      unlockedStripChapters = unlockedStripChapters + reached
    }
    val messagesByBook by remember { copilotRepository.allMessagesByBook }.collectAsState()
    val (reachedStrips, lockedStrips) = strips.partition { it.manifest.chapter in unlockedStripChapters }
    return BookmarkViewState(
      snips = messagesByBook[bookId].orEmpty().snipCards(chapters),
      strips = reachedStrips,
      lockedStrips = lockedStrips,
      activeStrip = activeStrip,
      edition = edition,
    )
  }

  fun onStripClick(strip: AvailableStrip) {
    activeStrip = strip
  }

  fun onStripClose() {
    activeStrip = null
  }

  /** Plays on from where the snip was taken, back on the player. */
  fun onSnipClick(snip: SnipCardViewState) {
    val wasPlaying = playStateManager.playState == PlayStateManager.PlayState.Playing
    scope.launch {
      currentBookStore.updateData { bookId }
    }
    playerController.setPosition(snip.positionInChapterMs, snip.chapterId)
    if (wasPlaying) {
      playerController.play()
    }
    navigator.goBack()
  }

  fun closeScreen() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): BookmarkViewModel
  }
}
