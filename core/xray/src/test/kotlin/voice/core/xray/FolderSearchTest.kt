package voice.core.xray

import android.net.Uri
import io.mockk.mockk
import voice.core.documentfile.CachedDocumentFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderSearchTest {

  private class FakeFile(
    override val name: String?,
    private val kids: List<FakeFile>? = null,
  ) : CachedDocumentFile {
    var listings = 0
      private set

    override val children: List<CachedDocumentFile>
      get() {
        listings++
        return kids.orEmpty()
      }
    override val isDirectory: Boolean get() = kids != null
    override val isFile: Boolean get() = kids == null
    override val length: Long get() = 0L
    override val lastModified: Long get() = 0L
    override val uri: Uri = mockk()
  }

  private fun file(name: String) = FakeFile(name)
  private fun folder(name: String, vararg kids: FakeFile) = FakeFile(name, kids.toList())

  @Test
  fun `a folder's own files come before anything in its subfolders`() {
    val root = folder("root", folder("a", file("deep.txt")), file("top.txt"), folder("b"))

    assertEquals(listOf("a", "top.txt", "b", "deep.txt"), root.breadthFirst().map { it.name }.toList())
  }

  @Test
  fun `card art folders are listed but never opened`() {
    val art = folder("Book 1.cards", file("carl.png"), folder("looks", file("x.png")))
    val other = folder("Series", file("Book 2.m4b"))
    val root = folder("root", art, other)

    val names = root.breadthFirst().map { it.name }.toList()

    assertEquals(listOf("Book 1.cards", "Series", "Book 2.m4b"), names)
    assertEquals(0, art.listings)
  }

  @Test
  fun `a manifest next to its art folder is found without opening a single subfolder`() {
    val art = folder("Book 1.cards", file("carl.png"))
    val unrelated = folder("Unrelated", file("x.txt"))
    val root = folder("root", unrelated, file("Book 1.cards.json"), art)

    val manifest = root.breadthFirst().first { it.isFile && it.name == "Book 1.cards.json" }

    assertEquals("Book 1.cards.json", manifest.name)
    assertEquals(0, unrelated.listings)
    assertEquals(0, art.listings)
  }

  @Test
  fun `a manifest in a subfolder is still found`() {
    val root = folder("root", folder("Series", folder("Book", file("Book.xray.json"))))

    assertEquals("Book.xray.json", root.breadthFirst().firstOrNull { it.name == "Book.xray.json" }?.name)
  }

  @Test
  fun `a missing manifest is searched for in every folder except the art folders`() {
    val art = folder("Book 1.cards", file("a.png"))
    val series = folder("Series", file("a.m4b"))
    val root = folder("root", art, series)

    assertNull(root.breadthFirst().firstOrNull { it.name == "missing.json" })
    assertEquals(1, series.listings)
    assertEquals(0, art.listings)
  }

  @Test
  fun `only directories ending in dot cards count as art folders`() {
    assertTrue(folder("Book 1.cards").isCardArtFolder())
    assertFalse(file("Book 1.cards").isCardArtFolder())
    assertFalse(folder("Book 1.cards.backup").isCardArtFolder())
    assertFalse(folder("cardsfolder").isCardArtFolder())
  }
}
