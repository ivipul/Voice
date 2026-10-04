package voice.core.data.repo.internals

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.StoredCoPilotMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CoPilotMessageDaoTest {

  private fun message(
    id: String,
    orderIndex: Long,
    imagePath: String? = null,
    snipChapterId: ChapterId? = null,
    snipPositionInChapterMs: Long? = null,
  ) = StoredCoPilotMessage(
    id = id,
    bookId = BookId("book"),
    role = "CoPilot",
    text = "text $id",
    timestampMs = 10L,
    isVisualPriority = snipChapterId != null,
    imagePath = imagePath,
    snipChapterId = snipChapterId,
    snipPositionInChapterMs = snipPositionInChapterMs,
    orderIndex = orderIndex,
  )

  private fun database() = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext(),
    AppDb::class.java,
  ).build()

  @Test
  fun `messages come back in order with every field`() = runTest {
    val db = database()
    val dao = db.coPilotMessageDao()
    assertTrue(dao.all().isEmpty())

    val snip = message("s1", orderIndex = 2, "/frames/s1.png", ChapterId("file-3"), 123_000L)
    dao.upsert(snip)
    dao.upsert(message("m1", orderIndex = 0))
    dao.upsert(message("m2", orderIndex = 1))

    assertEquals(listOf("m1", "m2", "s1"), dao.all().map { it.id })
    assertEquals(snip, dao.all().last())
    db.close()
  }

  @Test
  fun `upserting an existing message replaces it in place`() = runTest {
    val db = database()
    val dao = db.coPilotMessageDao()
    dao.upsert(message("m1", orderIndex = 0))
    dao.upsert(message("m2", orderIndex = 1))

    dao.upsert(message("m1", orderIndex = 0, imagePath = "/frames/m1.png"))

    assertEquals(listOf("m1", "m2"), dao.all().map { it.id })
    assertEquals("/frames/m1.png", dao.all().first().imagePath)
    db.close()
  }
}
