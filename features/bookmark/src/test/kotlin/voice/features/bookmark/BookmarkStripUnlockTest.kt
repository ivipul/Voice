package voice.features.bookmark

import androidx.datastore.core.DataStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import voice.core.copilot.CoPilotRepository
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.strips.AvailableStrip
import voice.core.strips.StripFrame
import voice.core.strips.StripManifest
import voice.core.strips.StripRepository
import voice.navigation.Navigator
import java.io.File
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkStripUnlockTest {

  private val bookId = BookId("book-1")
  private val first = chapter("first", durationMs = 2_000L)
  private val second = chapter("second", durationMs = 20_000L)
  private val savedPosition = MutableStateFlow(0L)
  private val book = mockk<Book> {
    every { chapters } returns listOf(first, second)
    every { content } returns mockk<BookContent>(relaxed = true) { every { name } returns "Dungeon Crawler Carl - Dungeon Crawler Carl, Book 1" }
    every { position } answers { savedPosition.value }
  }
  private val live = MutableStateFlow<LivePlaybackState?>(null)
  private val strip = AvailableStrip(
    manifest = StripManifest(
      schemaVersion = 1,
      book = 1,
      bookTitlePrefix = "Dungeon Crawler Carl",
      chapter = 1,
      title = "The vanishing",
      summary = "s",
      lastFrameHoldMs = 10_000L,
      frames = listOf(StripFrame(1, "f1-final.png", 3_000L)),
    ),
    directory = File("strip"),
  )

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(): BookmarkViewModel {
    val repo = mockk<BookRepository> { coEvery { get(bookId) } returns book }
    val stripRepository = mockk<StripRepository> { coEvery { stripsFor(any()) } returns listOf(strip) }
    val playerController = mockk<PlayerController>(relaxed = true) {
      every { livePlaybackStateFlow(bookId) } returns live
    }
    return BookmarkViewModel(
      currentBookStore = mockk<DataStore<BookId?>>(relaxed = true),
      repo = repo,
      playStateManager = mockk<PlayStateManager>(relaxed = true),
      playerController = playerController,
      navigator = mockk<Navigator>(relaxed = true),
      stripRepository = stripRepository,
      copilotRepository = CoPilotRepository(),
      bookId = bookId,
    )
  }

  @Test
  fun stripAppearsOnlyWhenPlaybackCrossesItsStart() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertTrue(awaitItem().strips.isEmpty())
      assertEquals(listOf(1), awaitUntil { it.lockedStrips.isNotEmpty() }.lockedStrips.map { it.manifest.chapter })

      live.value = LivePlaybackState(bookId, first.id, positionMs = 2_999L, isPlaying = true, playbackSpeed = 1f)
      expectNoEvents()

      live.value = LivePlaybackState(bookId, first.id, positionMs = 3_000L, isPlaying = true, playbackSpeed = 1f)
      val unlocked = awaitUntil { it.strips.isNotEmpty() }
      assertEquals(listOf(1), unlocked.strips.map { it.manifest.chapter })
      assertTrue(unlocked.lockedStrips.isEmpty())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun unlockedStripStaysVisibleAfterSeekingBackBeforeItsStart() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      live.value = LivePlaybackState(bookId, second.id, positionMs = 1_000L, isPlaying = true, playbackSpeed = 1f)
      assertEquals(1, awaitUntil { it.strips.isNotEmpty() }.strips.size)
      live.value = LivePlaybackState(bookId, first.id, positionMs = 0L, isPlaying = true, playbackSpeed = 1f)
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  private suspend fun app.cash.turbine.ReceiveTurbine<voice.features.bookmark.BookmarkViewState>.awaitUntil(
    predicate: (voice.features.bookmark.BookmarkViewState) -> Boolean,
  ): voice.features.bookmark.BookmarkViewState {
    while (true) {
      val item = awaitItem()
      if (predicate(item)) return item
    }
  }
}

private fun chapter(
  name: String,
  durationMs: Long,
): Chapter = Chapter(
  id = ChapterId(name),
  name = name,
  duration = durationMs,
  fileLastModified = Instant.EPOCH,
  fileSize = 0,
  markData = emptyList(),
)
