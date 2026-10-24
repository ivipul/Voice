package voice.features.extras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.companion.CompanionStores
import voice.core.companion.extras.ExtraBuilder
import voice.core.companion.extras.ExtraHost
import voice.core.companion.extras.ExtraTheme
import voice.core.companion.extras.wrapExtraPage
import voice.core.copilot.CoPilotPipeline
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.bookPositionOf
import voice.core.data.chapterPositionOf
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.playback.PlayerController
import voice.core.ui.crawl.BookStyle
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.toStyle
import voice.navigation.Navigator

@Immutable
data class ExtraViewState(
  val title: String,
  /** The page with the sandbox policy, the book's colors and the bridge already in its head. */
  val page: String,
  val bookStyle: BookStyle,
  val lastError: String?,
  val fixing: Boolean,
  val fixError: String?,
  /** What the listener is typing into the Ask for changes box, or null while it is closed. */
  val changeRequest: String?,
)

/**
 * Shows one shelf Extra and answers its calls into the app. The page only sees the book up to the listener's
 * position: the transcript it gets stops there, and questions go through the same spoiler-safe Ask as the player.
 */
@AssistedInject
class ExtraViewModel(
  private val repo: BookRepository,
  private val stores: CompanionStores,
  private val host: ExtraHost,
  private val builder: ExtraBuilder,
  private val pipeline: CoPilotPipeline,
  private val playerController: PlayerController,
  private val navigator: Navigator,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @Assisted
  private val bookId: BookId,
  @Assisted
  private val extraId: String,
) {

  private val scope = MainScope()
  private var fixing by mutableStateOf(false)
  private var fixError by mutableStateOf<String?>(null)
  private var changeRequest by mutableStateOf<String?>(null)
  private var closed = false

  @Composable
  fun viewState(): ExtraViewState? {
    val shelfState by remember(bookId) { stores.shelf.flow(bookId) }.collectAsState(initial = null)
    val lookState by remember(bookId) { stores.look.flow(bookId) }.collectAsState(initial = null)
    val shelf = shelfState ?: return null
    val look = lookState ?: return null
    val extra = shelf.extras.firstOrNull { it.id == extraId }
    if (extra == null) {
      LaunchedEffect(Unit) { close() }
      return null
    }
    val style = look.toStyle()
    val page = remember(extra.html, style) { wrapExtraPage(extra.html, style.palette.toExtraTheme(look.displayFont)) }
    return ExtraViewState(
      title = extra.title,
      page = page,
      bookStyle = style,
      lastError = extra.lastError,
      fixing = fixing,
      fixError = fixError,
      changeRequest = changeRequest,
    )
  }

  /** The listener's position in the whole book: live from the player when it plays this book, else the saved one. */
  suspend fun positionMs(book: Book): Long {
    val live = playerController.livePlaybackState(bookId)
    return live?.let { book.chapters.bookPositionOf(it.chapterId, it.positionMs) } ?: book.position
  }

  suspend fun book(): Book? = repo.get(bookId)

  suspend fun bookInfo(): String {
    val book = book() ?: return "{}"
    return host.bookInfo(book, positionMs(book))
  }

  suspend fun transcript(maxChars: Int): String {
    val book = book() ?: return ""
    return host.transcriptSoFar(book, positionMs(book), maxChars)
  }

  suspend fun ask(question: String): String = pipeline.ask(bookId, question.take(MAX_QUESTION_CHARS))

  suspend fun savedValue(key: String): String? = host.savedValue(bookId, extraId, key)

  suspend fun saveValue(key: String, value: String) = host.saveValue(bookId, extraId, key, value)

  fun onSeek(positionMs: Long) {
    scope.launch {
      val book = book() ?: return@launch
      val (chapterId, positionInChapter) = book.chapters.chapterPositionOf(positionMs) ?: return@launch
      currentBookStore.updateData { bookId }
      playerController.setPosition(positionInChapter, chapterId)
      close()
    }
  }

  fun onPageError(error: String) {
    Logger.w("Explorable $extraId: $error")
    scope.launch { builder.reportError(bookId, extraId, error.take(MAX_ERROR_CHARS)) }
  }

  fun onAskForChanges() {
    if (!fixing) changeRequest = ""
  }

  fun onChangeRequestChange(text: String) {
    changeRequest = text
  }

  fun onChangeRequestDismiss() {
    changeRequest = null
  }

  fun onChangeRequestSend() {
    val request = changeRequest ?: return
    if (fixing) return
    changeRequest = null
    fixing = true
    fixError = null
    scope.launch {
      try {
        val error = stores.shelf.get(bookId).extras.firstOrNull { it.id == extraId }?.lastError
        withContext(Dispatchers.Default) { builder.change(bookId, extraId, request.take(MAX_REQUEST_CHARS), error) }
      } catch (e: Exception) {
        Logger.w(e, "Fixing Explorable $extraId failed")
        fixError = e.message ?: e.javaClass.simpleName
      } finally {
        fixing = false
      }
    }
  }

  fun onDismissError() {
    scope.launch { builder.reportError(bookId, extraId, null) }
  }

  fun onDelete() {
    scope.launch {
      builder.delete(bookId, extraId)
      host.clearValues(bookId, extraId)
      close()
    }
  }

  fun close() {
    if (closed) return
    closed = true
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(
      bookId: BookId,
      extraId: String,
    ): ExtraViewModel
  }

  private companion object {
    const val MAX_QUESTION_CHARS = 2_000
    const val MAX_ERROR_CHARS = 1_000
    const val MAX_REQUEST_CHARS = 2_000
  }
}

internal fun CrawlPalette.toExtraTheme(displayFont: String): ExtraTheme = ExtraTheme(
  background = background.toArgb(),
  card = lerp(background, Color.White, 0.55F).toArgb(),
  ink = content.toArgb(),
  muted = lerp(content, background, 0.45F).toArgb(),
  accent = highlight.toArgb(),
  accentInk = onHighlight.toArgb(),
  displayFont = cssDisplayFont(displayFont),
  bodyFont = "system-ui,-apple-system,Roboto,sans-serif",
)

/** The page can't load the app's fonts, so each display face maps to the closest one Android ships. */
internal fun cssDisplayFont(key: String): String = when (key) {
  "serif" -> "'Noto Serif',Georgia,serif"
  "classic" -> "'Noto Serif',Georgia,serif"
  "condensed" -> "'Roboto Condensed','Arial Narrow',sans-serif-condensed,sans-serif"
  else -> "Impact,'Roboto Condensed','Arial Narrow',sans-serif"
}
