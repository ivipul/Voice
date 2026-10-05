package voice.features.bookmark

import androidx.datastore.core.DataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotRepository
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.MarkData
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.navigation.Navigator
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkSnipsTest {

  private val chapter = Chapter(
    id = ChapterId("book.m4b"),
    name = "book",
    duration = 600_000L,
    fileLastModified = Instant.EPOCH,
    fileSize = 0,
    markData = listOf(MarkData(startMs = 0L, name = "Chapter 1"), MarkData(startMs = 300_000L, name = "Chapter 2")),
  )

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun snipsBecomeCardsNewestFirstWithTitleTextFrameAndPlace() {
    val older = snip(id = "older", text = "Goblin Ambush\nThe goblins pour out of the vents.", takenAtMs = 1L, positionMs = 30_000L)
    val newer = snip(id = "newer", text = "Carl kicks the door in.", takenAtMs = 2L, positionMs = 341_000L)
      .copy(imagePath = "/frames/newer.png")
    val answer = CoPilotMessage(id = "answer", role = CoPilotMessage.Role.CoPilot, text = "Carl is a crawler.", timestampMs = 3L)

    val cards = listOf(older, answer, newer).snipCards(listOf(chapter))

    assertEquals(
      listOf(
        SnipCardViewState(
          id = "newer",
          title = null,
          text = "Carl kicks the door in.",
          location = "Chapter 2 : 00:41",
          imagePath = "/frames/newer.png",
          drawingImage = false,
          chapterId = chapter.id,
          positionInChapterMs = 341_000L,
        ),
        SnipCardViewState(
          id = "older",
          title = "Goblin Ambush",
          text = "The goblins pour out of the vents.",
          location = "Chapter 1 : 00:30",
          imagePath = null,
          drawingImage = false,
          chapterId = chapter.id,
          positionInChapterMs = 30_000L,
        ),
      ),
      cards,
    )
  }

  @Test
  fun tappingASnipPlaysOnFromItBackOnThePlayer() = runTest {
    val playerController = mockk<PlayerController>(relaxed = true)
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = BookmarkViewModel(
      currentBookStore = MemoryDataStore<BookId?>(null),
      repo = mockk(relaxed = true),
      playStateManager = mockk<PlayStateManager> { every { playState } returns PlayStateManager.PlayState.Playing },
      playerController = playerController,
      navigator = navigator,
      stripRepository = mockk(relaxed = true),
      copilotRepository = CoPilotRepository(),
      bookId = BookId("book"),
    )
    val card = listOf(snip(id = "snip", text = "Moment", takenAtMs = 1L, positionMs = 341_000L)).snipCards(listOf(chapter)).single()

    viewModel.onSnipClick(card)

    verifyOrder {
      playerController.setPosition(341_000L, chapter.id)
      playerController.play()
    }
    verify(exactly = 0) { navigator.goBack() }
  }

  @Test
  fun deletingASnipRemovesOnlyThatSnipFromTheBook() = runTest {
    val bookId = BookId("book")
    val copilotRepository = CoPilotRepository()
    val kept = snip(id = "kept", text = "Kept", takenAtMs = 1L, positionMs = 30_000L)
    val deleted = snip(id = "deleted", text = "Deleted", takenAtMs = 2L, positionMs = 341_000L)
    copilotRepository.addMessage(bookId, kept)
    copilotRepository.addMessage(bookId, deleted)
    val viewModel = BookmarkViewModel(
      currentBookStore = MemoryDataStore<BookId?>(null),
      repo = mockk(relaxed = true),
      playStateManager = mockk(relaxed = true),
      playerController = mockk(relaxed = true),
      navigator = mockk(relaxed = true),
      stripRepository = mockk(relaxed = true),
      copilotRepository = copilotRepository,
      bookId = bookId,
    )
    val card = listOf(kept, deleted).snipCards(listOf(chapter)).first { it.id == "deleted" }

    viewModel.onSnipDelete(card)

    assertEquals(listOf(kept), copilotRepository.allMessagesByBook.value[bookId])
  }

  private fun snip(
    id: String,
    text: String,
    takenAtMs: Long,
    positionMs: Long,
  ) = CoPilotMessage(
    id = id,
    role = CoPilotMessage.Role.CoPilot,
    text = text,
    timestampMs = takenAtMs,
    snipChapterId = chapter.id,
    snipPositionInChapterMs = positionMs,
  )
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {

  private val value = MutableStateFlow(initial)

  override val data: Flow<T> get() = value

  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    return value.updateAndGet { transform(it) }
  }
}
