package voice.core.copilot

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.StoredCoPilotMessage
import voice.core.data.repo.internals.dao.CoPilotMessageDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoPilotRepositoryTest {

  private val book = BookId("book-1")
  private val otherBook = BookId("book-2")

  private class FakeDao(private val gate: CompletableDeferred<Unit>? = null) : CoPilotMessageDao() {
    val rows = mutableMapOf<String, StoredCoPilotMessage>()

    override suspend fun all(): List<StoredCoPilotMessage> {
      gate?.await()
      return rows.values.sortedBy { it.orderIndex }
    }

    override suspend fun upsert(message: StoredCoPilotMessage) {
      rows[message.id] = message
    }
  }

  private fun TestScope.repository(
    dao: CoPilotMessageDao,
    imageExists: (String) -> Boolean = { true },
  ) = CoPilotRepository(
    dao,
    CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
    imageExists,
  )

  private fun message(
    id: String,
    role: CoPilotMessage.Role = CoPilotMessage.Role.CoPilot,
    text: String = "text $id",
    timestampMs: Long = 0L,
  ) = CoPilotMessage(id = id, role = role, text = text, timestampMs = timestampMs)

  private fun CoPilotRepository.messages(bookId: BookId = book) = allMessagesByBook.value[bookId].orEmpty()

  @Test
  fun `history is restored in order after a restart`() = runTest {
    val dao = FakeDao()
    val first = repository(dao)
    first.addMessage(book, message("q1", CoPilotMessage.Role.User, timestampMs = 1L))
    first.addMessage(book, message("a1", timestampMs = 2L))
    first.addMessage(otherBook, message("o1", timestampMs = 3L))
    first.addMessage(book, message("q2", CoPilotMessage.Role.User, timestampMs = 4L))

    val restarted = repository(dao)

    assertEquals(listOf("q1", "a1", "q2"), restarted.messages().map { it.id })
    assertEquals(listOf("o1"), restarted.messages(otherBook).map { it.id })
    assertEquals(CoPilotMessage.Role.User, restarted.messages().first().role)
    assertEquals(first.messages(), restarted.messages())
  }

  @Test
  fun `a snip keeps its place in the book and its image after a restart`() = runTest {
    val dao = FakeDao()
    val first = repository(dao)
    first.addMessage(
      book,
      CoPilotMessage(
        id = "s1",
        role = CoPilotMessage.Role.CoPilot,
        text = "a snipped moment",
        timestampMs = 5L,
        isVisualPriority = true,
        isGeneratingImage = true,
        snipChapterId = ChapterId("file-3"),
        snipPositionInChapterMs = 123_000L,
      ),
    )
    first.updateMessage(book, "s1") { it.copy(imagePath = "/frames/s1.png", isGeneratingImage = false) }

    val restored = repository(dao).messages().single()

    assertEquals("/frames/s1.png", restored.imagePath)
    assertEquals(ChapterId("file-3"), restored.snipChapterId)
    assertEquals(123_000L, restored.snipPositionInChapterMs)
    assertTrue(restored.isVisualPriority)
    assertEquals("a snipped moment", restored.text)
  }

  @Test
  fun `updating a message keeps its place in the order`() = runTest {
    val dao = FakeDao()
    val first = repository(dao)
    first.addMessage(book, message("m1"))
    first.addMessage(book, message("m2"))
    first.updateMessage(book, "m1") { it.copy(text = "edited") }

    val restored = repository(dao).messages()

    assertEquals(listOf("m1", "m2"), restored.map { it.id })
    assertEquals("edited", restored.first().text)
  }

  @Test
  fun `a drawing that was in progress is not still drawing after a restart`() = runTest {
    val dao = FakeDao()
    repository(dao).addMessage(book, message("s1").copy(isGeneratingImage = true))

    val restored = repository(dao).messages().single()

    assertFalse(restored.isGeneratingImage)
    assertNull(restored.imagePath)
  }

  @Test
  fun `a message whose image file is gone is shown without the image`() = runTest {
    val dao = FakeDao()
    val first = repository(dao)
    first.addMessage(book, message("m1").copy(imagePath = "/frames/gone.png"))
    first.addMessage(book, message("m2").copy(imagePath = "/frames/here.png"))

    val restored = repository(dao, imageExists = { it == "/frames/here.png" }).messages()

    assertNull(restored[0].imagePath)
    assertEquals("/frames/here.png", restored[1].imagePath)
    assertEquals("text m1", restored[0].text)
  }

  @Test
  fun `messages added while the history loads come after it`() = runTest {
    val dao = FakeDao()
    repository(dao).apply {
      addMessage(book, message("old1"))
      addMessage(book, message("old2"))
    }
    val gate = CompletableDeferred<Unit>()
    val slowDao = FakeDao(gate).also { it.rows += dao.rows }

    val restarted = repository(slowDao)
    restarted.addMessage(book, message("new1"))
    assertEquals(listOf("new1"), restarted.messages().map { it.id })

    gate.complete(Unit)

    assertEquals(listOf("old1", "old2", "new1"), restarted.messages().map { it.id })
    // The new message was saved after the old ones, so the next restart keeps that order.
    assertEquals(listOf("old1", "old2", "new1"), repository(slowDao).messages().map { it.id })
  }

  @Test
  fun `new messages after a restart continue the order`() = runTest {
    val dao = FakeDao()
    repository(dao).addMessage(book, message("m1"))

    val second = repository(dao)
    second.addMessage(book, message("m2"))

    assertEquals(listOf("m1", "m2"), repository(dao).messages().map { it.id })
  }

  @Test
  fun `an empty history is just empty`() = runTest {
    val restarted = repository(FakeDao())
    assertTrue(restarted.allMessagesByBook.value.isEmpty())
  }
}
