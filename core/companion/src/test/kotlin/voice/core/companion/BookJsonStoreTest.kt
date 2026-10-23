package voice.core.companion

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import voice.core.data.BookId
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BookJsonStoreTest {

  private val dir: File = createTempDirectory().toFile()
  private val bookId = BookId("content://books/a.m4b")
  private val store = BookJsonStore({ dir }, "profile.json", BookProfile.serializer()) { BookProfile() }

  @Test
  fun clearDeletesTheFileAndGoesBackToTheDefault() = runTest {
    store.set(bookId, BookProfile(title = "Good Energy"))
    val flow = store.flow(bookId)
    assertEquals("Good Energy", flow.first().title)

    store.clear(bookId)

    assertFalse(File(dir, "profile.json").exists())
    assertEquals(BookProfile(), store.get(bookId))
    assertEquals(BookProfile(), flow.first())
  }

  @Test
  fun aClearedBookIsReadFreshByANewStore() = runTest {
    store.set(bookId, BookProfile(title = "Good Energy"))
    store.clear(bookId)

    val reopened = BookJsonStore({ dir }, "profile.json", BookProfile.serializer()) { BookProfile() }

    assertEquals(BookProfile(), reopened.get(bookId))
  }
}
