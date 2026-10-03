package voice.features.bookmark.strips

import androidx.datastore.core.DataStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.strips.AvailableStrip
import voice.core.strips.StripFrame
import voice.core.strips.StripManifest
import java.io.File
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class StripViewerViewModelTest {

  private val bookId = BookId("book-1")
  private val first = chapter("first", durationMs = 2_000L)
  private val second = chapter("second", durationMs = 20_000L)
  private val book = mockk<Book> {
    every { chapters } returns listOf(first, second)
  }
  private val strip = AvailableStrip(
    manifest = StripManifest(
      schemaVersion = 1,
      book = 1,
      bookTitlePrefix = "Dungeon Crawler Carl",
      chapter = 1,
      title = "The vanishing",
      summary = "s",
      lastFrameHoldMs = 10_000L,
      frames = listOf(
        StripFrame(1, "f1-final.png", 1_000L),
        StripFrame(2, "f2-final.png", 3_000L),
        StripFrame(3, "f3-final.png", 6_000L),
      ),
    ),
    directory = File("strip"),
  )
  private val live = MutableStateFlow<LivePlaybackState?>(null)
  private val playerController = mockk<PlayerController>(relaxed = true) {
    every { livePlaybackStateFlow(bookId) } returns live
  }
  private val bookRepository = mockk<BookRepository> {
    coEvery { get(bookId) } returns book
  }
  private val currentBookStore = mockk<DataStore<BookId?>> {
    coEvery { updateData(any()) } returns bookId
  }

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = StripViewerViewModel(
    playerController = playerController,
    bookRepository = bookRepository,
    currentBookStore = currentBookStore,
    bookId = bookId,
    strip = strip,
  )

  @Test
  fun startSeeksToFirstFrameInItsChapter() = runTest {
    viewModel().start()
    verify { playerController.setPosition(1_000L, first.id) }
  }

  @Test
  fun seekingToLaterFrameConvertsBookTimeToChapterPosition() = runTest {
    viewModel().seekTo(3_000L)
    verify { playerController.setPosition(1_000L, second.id) }
  }

  @Test
  fun viewStateFollowsLivePlaybackPosition() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(0, awaitUntil { it.frameIndex == 0 }.frameIndex)
      live.value = LivePlaybackState(bookId, second.id, positionMs = 1_500L, isPlaying = true, playbackSpeed = 1f)
      val state = awaitUntil { it.frameIndex == 1 }
      assertEquals(1f, state.progress[0])
      assertEquals(500f / 3_000f, state.progress[1], 0.0001f)
      assertEquals(0f, state.progress[2])
      assertEquals(6_000L, state.nextFrameStartMs)
      assertEquals(1_000L, state.previousFrameStartMs)
      assertFalse(state.closed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun viewStateClosesWhenPlaybackLeavesStripRange() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      live.value = LivePlaybackState(bookId, second.id, positionMs = 3_000L, isPlaying = true, playbackSpeed = 1f)
      val inFrameTwo = awaitUntil { it.frameIndex == 1 }
      assertFalse(inFrameTwo.closed)
      live.value = LivePlaybackState(bookId, second.id, positionMs = 18_000L, isPlaying = true, playbackSpeed = 1f)
      val state = awaitUntil { it.closed }
      assertTrue(state.closed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun lastFrameHasNoNextSeek() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      live.value = LivePlaybackState(bookId, second.id, positionMs = 6_000L, isPlaying = true, playbackSpeed = 1f)
      val state = awaitUntil { it.frameIndex == 2 }
      assertEquals(null, state.nextFrameStartMs)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private suspend fun ReceiveTurbine<StripViewerViewState>.awaitUntil(
    predicate: (StripViewerViewState) -> Boolean,
  ): StripViewerViewState {
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
