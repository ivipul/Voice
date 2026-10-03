package voice.features.bookmark.strips

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.story.StoryPlayer
import voice.core.strips.AvailableStrip
import voice.core.strips.StripFrame
import voice.core.strips.StripManifest
import java.io.File
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StripViewerViewModelTest {

  private val bookId = BookId("book-1")
  private val first = chapter("first", durationMs = 2_000L)
  private val second = chapter("second", durationMs = 20_000L)
  private val chapters = listOf(first, second)
  private val book = mockk<Book> {
    every { this@mockk.chapters } returns this@StripViewerViewModelTest.chapters
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

  private val stripWithImage = strip.copy(manifest = strip.manifest.copy(stripImage = "ch01-strip.png"))
  private val story = MutableStateFlow<StoryPlayer.State?>(null)
  private val storyPlayer = mockk<StoryPlayer>(relaxed = true) {
    every { state } returns story
    // The real player's position follows a seek straight away.
    every { seekTo(any()) } answers { story.value = StoryPlayer.State(firstArg(), isPlaying = true) }
  }
  private val playerController = mockk<PlayerController>(relaxed = true)
  private val playStateManager = mockk<PlayStateManager> {
    every { playState } returns PlayStateManager.PlayState.Paused
  }
  private val bookRepository = mockk<BookRepository> {
    coEvery { get(bookId) } returns book
  }

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(strip: AvailableStrip = this.strip) = StripViewerViewModel(
    storyPlayer = storyPlayer,
    playerController = playerController,
    playStateManager = playStateManager,
    bookRepository = bookRepository,
    bookId = bookId,
    strip = strip,
  )

  private fun bookIsPlaying() {
    every { playStateManager.playState } returns PlayStateManager.PlayState.Playing
  }

  @Test
  fun startPausesTheBookThenStartsTheStoryAtTheFirstFrame() = runTest {
    viewModel().start()
    verifyOrder {
      playerController.pause()
      storyPlayer.start(chapters, 1_000L)
    }
  }

  @Test
  fun startNeverMovesTheBooksPosition() = runTest {
    viewModel().start()
    verify(exactly = 0) { playerController.setPosition(any(), any()) }
    verify(exactly = 0) { playerController.play() }
  }

  @Test
  fun closingStopsTheStoryAndResumesABookThatWasPlaying() = runTest {
    bookIsPlaying()
    val viewModel = viewModel()
    viewModel.start()
    viewModel.close()
    verifyOrder {
      storyPlayer.stop()
      playerController.play()
    }
  }

  @Test
  fun closingLeavesAPausedBookPaused() = runTest {
    val viewModel = viewModel()
    viewModel.start()
    viewModel.close()
    verify { storyPlayer.stop() }
    verify(exactly = 0) { playerController.play() }
  }

  @Test
  fun closingTwiceResumesTheBookOnce() = runTest {
    bookIsPlaying()
    val viewModel = viewModel()
    viewModel.start()
    viewModel.close()
    viewModel.close()
    verify(exactly = 1) { storyPlayer.stop() }
    verify(exactly = 1) { playerController.play() }
  }

  @Test
  fun closingBeforeTheStoryStartedLeavesTheBookAlone() = runTest {
    bookIsPlaying()
    viewModel().close()
    verify(exactly = 0) { playerController.play() }
    verify(exactly = 0) { playerController.pause() }
  }

  @Test
  fun framesFollowTheStoryPosition() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(0, awaitUntil { it.frameIndex == 0 }.frameIndex)
      story.value = StoryPlayer.State(bookMs = 3_500L, isPlaying = true)
      val state = awaitUntil { it.frameIndex == 1 }
      assertEquals(1f, state.progress[0])
      assertEquals(500f / 3_000f, state.progress[1], 0.0001f)
      assertEquals(0f, state.progress[2])
      assertFalse(state.closed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun viewStateClosesWhenThePositionLeavesTheStripRange() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 3_000L, isPlaying = true)
      assertFalse(awaitUntil { it.frameIndex == 1 }.closed)
      story.value = StoryPlayer.State(bookMs = 18_000L, isPlaying = true)
      assertTrue(awaitUntil { it.closed }.closed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun tappingNextOnFrameTwoAdvancesToFrameThreeNotBackToFrameTwo() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 3_500L, isPlaying = true)
      awaitFrame(1)
      assertTrue(viewModel.onNext())
      verify { storyPlayer.seekTo(6_000L) }
      verify(exactly = 0) { storyPlayer.seekTo(3_000L) }
      assertEquals(2, awaitUntil { it.frameIndex == 2 }.frameIndex)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun repeatedNextTapsStepThroughEveryFrameThenClose() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 1_000L, isPlaying = true)
      awaitFrame(0)
      assertTrue(viewModel.onNext())
      awaitFrame(1)
      assertTrue(viewModel.onNext())
      awaitFrame(2)
      assertFalse(viewModel.onNext())
      verifyOrder {
        storyPlayer.seekTo(3_000L)
        storyPlayer.seekTo(6_000L)
      }
      verify(exactly = 2) { storyPlayer.seekTo(any()) }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun nextTapsAdvanceEvenBeforeThePositionHasCaughtUp() = runTest {
    every { storyPlayer.seekTo(any()) } returns Unit
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 1_000L, isPlaying = true)
      awaitFrame(0)
      assertTrue(viewModel.onNext())
      assertTrue(viewModel.onNext())
      verifyOrder {
        storyPlayer.seekTo(3_000L)
        storyPlayer.seekTo(6_000L)
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun previousGoesBackOneFrameAndRestartsTheFirst() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 6_500L, isPlaying = true)
      awaitFrame(2)
      viewModel.onPrevious()
      awaitFrame(1)
      viewModel.onPrevious()
      awaitFrame(0)
      viewModel.onPrevious()
      verifyOrder {
        storyPlayer.seekTo(3_000L)
        storyPlayer.seekTo(1_000L)
        storyPlayer.seekTo(1_000L)
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun navigationAndPauseOnlyTouchTheStoryAudio() = runTest {
    val viewModel = viewModel()
    viewModel.start()
    assertTrue(viewModel.onNext())
    viewModel.onPrevious()
    viewModel.togglePlaying()
    verify { storyPlayer.togglePlaying() }
    verify(exactly = 0) { playerController.setPosition(any(), any()) }
    verify(exactly = 0) { playerController.play() }
    verify(exactly = 0) { playerController.playPause() }
  }

  @Test
  fun viewStateReportsWhetherTheStoryIsPlaying() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 3_000L, isPlaying = false)
      assertFalse(awaitUntil { it.frameIndex == 1 }.isPlaying)
      story.value = StoryPlayer.State(bookMs = 3_100L, isPlaying = true)
      assertTrue(awaitUntil { it.isPlaying }.isPlaying)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun lastFrameHasNoNextFrame() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 6_000L, isPlaying = true)
      awaitFrame(2)
      assertFalse(viewModel.onNext())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun theFullStripIsAnExtraFinalFrameWithItsOwnProgressSegment() = runTest {
    val viewModel = viewModel(stripWithImage)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 16_000L + 3_500L, isPlaying = true)
      val state = awaitUntil { it.frameIndex == 3 }
      assertEquals(4, state.progress.size)
      assertEquals(listOf(1f, 1f, 1f), state.progress.take(3))
      assertEquals(3_500f / 7_000f, state.progress[3], 0.0001f)
      assertFalse(state.closed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun theStoryClosesSevenSecondsAfterTheFullStripAppears() = runTest {
    val viewModel = viewModel(stripWithImage)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 16_000L, isPlaying = true)
      assertFalse(awaitUntil { it.frameIndex == 3 }.closed)
      story.value = StoryPlayer.State(bookMs = 22_900L, isPlaying = true)
      assertFalse(awaitUntil { it.progress[3] > 0.9f }.closed)
      story.value = StoryPlayer.State(bookMs = 23_000L, isPlaying = true)
      assertTrue(awaitUntil { it.closed }.closed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aPausedStoryStaysOnTheFullStripWithoutClosing() = runTest {
    val viewModel = viewModel(stripWithImage)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 20_000L, isPlaying = false)
      val paused = awaitUntil { it.frameIndex == 3 }
      assertFalse(paused.isPlaying)
      assertFalse(paused.closed)
      story.value = StoryPlayer.State(bookMs = 20_000L, isPlaying = false)
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun nextFromTheLastComicFrameShowsTheFullStripAndNextOnItClosesTheStory() = runTest {
    val viewModel = viewModel(stripWithImage)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 6_500L, isPlaying = true)
      awaitFrame(2)
      assertTrue(viewModel.onNext())
      verify { storyPlayer.seekTo(16_000L) }
      awaitFrame(3)
      assertFalse(viewModel.onNext())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun backFromTheFullStripGoesToTheLastComicFrame() = runTest {
    val viewModel = viewModel(stripWithImage)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 17_000L, isPlaying = true)
      awaitFrame(3)
      viewModel.onPrevious()
      verify { storyPlayer.seekTo(6_000L) }
      awaitFrame(2)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun withoutAStripImageTheLastFrameStillHasNoNext() = runTest {
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      story.value = StoryPlayer.State(bookMs = 6_500L, isPlaying = true)
      val state = awaitUntil { it.frameIndex == 2 }
      assertEquals(3, state.progress.size)
      assertFalse(viewModel.onNext())
      cancelAndIgnoreRemainingEvents()
    }
  }

  private suspend fun ReceiveTurbine<StripViewerViewState>.awaitFrame(index: Int) {
    awaitUntil { it.frameIndex == index }.also { assertEquals(index, it.frameIndex) }
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
