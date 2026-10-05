package voice.features.playbackScreen.deck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.repo.BookRepository
import voice.core.ui.crawl.crawlEditionOf
import voice.core.xray.XRayRepository
import voice.core.xray.card.PlayerCardRepository
import voice.core.xray.card.PlayerCardSet
import voice.core.xray.card.composeAt
import voice.navigation.Navigator

data class DeckViewState(
  val slots: List<DeckSlot>,
  val loading: Boolean,
) {
  val collected: Int get() = slots.count { it is DeckSlot.Unlocked }
}

@Inject
class DeckViewModel(
  private val bookRepository: BookRepository,
  private val xrayRepository: XRayRepository,
  private val playerCardRepository: PlayerCardRepository,
  private val navigator: Navigator,
  private val dispatcherProvider: DispatcherProvider,
) {

  var viewState by mutableStateOf(DeckViewState(slots = emptyList(), loading = true))
    private set

  /** Reads every book's X-Ray and card files; finding them in the audiobook folders is slow, so the books go in parallel. */
  suspend fun load() {
    val slots = withContext(dispatcherProvider.io) {
      val loaded = coroutineScope {
        bookRepository.all().map { book -> async { loadBook(book) } }.awaitAll()
      }.filterNotNull()
      val cardSets = loaded.associate { (book, cardSet) -> book.key to cardSet }
      buildDeck(loaded.map { it.first }).map { slot ->
        if (slot is DeckSlot.Unlocked) slot.withImages(cardSets[slot.bookKey]) else slot
      }
    }
    viewState = DeckViewState(slots = slots, loading = false)
  }

  private suspend fun loadBook(book: Book): Pair<DeckBook, PlayerCardSet>? {
    val cardSet = playerCardRepository.cardsFor(book) ?: return null
    val deckBook = DeckBook(
      key = book.id.value,
      number = crawlEditionOf(book.content.name)?.number ?: Int.MAX_VALUE,
      positionMs = book.content.positionInChapter,
      manifest = xrayRepository.manifestFor(book),
      cards = cardSet.allCards(),
    )
    return deckBook to cardSet
  }

  private fun DeckSlot.Unlocked.withImages(cardSet: PlayerCardSet?): DeckSlot.Unlocked {
    val path = data.composeAt(positionMs).look?.image ?: return this
    val uri = cardSet?.imageUri(path) ?: return this
    return copy(imageUris = mapOf(path to uri.toString()))
  }

  fun onClose() {
    navigator.goBack()
  }
}
