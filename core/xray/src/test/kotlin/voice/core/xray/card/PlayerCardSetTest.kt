package voice.core.xray.card

import android.net.Uri
import io.mockk.mockk
import voice.core.documentfile.CachedDocumentFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerCardSetTest {

  private class FakeFile(
    override val name: String?,
    private val kids: List<FakeFile>? = null,
  ) : CachedDocumentFile {
    var listings = 0
      private set
    override val uri: Uri = mockk()

    override val children: List<CachedDocumentFile>
      get() {
        listings++
        return kids.orEmpty()
      }
    override val isDirectory: Boolean get() = kids != null
    override val isFile: Boolean get() = kids == null
    override val length: Long get() = 0L
    override val lastModified: Long get() = 0L
  }

  private val carl = FakeFile("carl.png")
  private val ghost = FakeFile("ghost.png")
  private val looks = FakeFile("looks", listOf(carl, ghost))
  private val portrait = FakeFile("portrait.png")
  private val art = FakeFile("Book 1.cards", listOf(looks, portrait))

  private fun cards(vararg images: String) = mapOf(
    "carl" to PlayerCardData(
      id = "carl",
      entries = images.map { PlayerCardEntry(ms = 0L, field = PlayerCardFields.LOOK, image = it) },
    ),
  )

  @Test
  fun `a path is resolved through the art folder`() {
    val set = PlayerCardSet(cards(), art)

    assertEquals(carl.uri, set.imageUri("looks/carl.png"))
    assertEquals(ghost.uri, set.imageUri("looks/ghost.png"))
  }

  @Test
  fun `a path that does not exist, or a set without an art folder, has no image`() {
    assertNull(PlayerCardSet(cards(), art).imageUri("looks/nobody.png"))
    assertNull(PlayerCardSet(cards(), art).imageUri("looks"))
    assertNull(PlayerCardSet(cards(), imageFolder = null).imageUri("looks/carl.png"))
  }

  @Test
  fun `a path is only resolved once, and a path that was not found is not looked for again`() {
    val set = PlayerCardSet(cards(), art)

    repeat(3) { assertEquals(carl.uri, set.imageUri("looks/carl.png")) }
    repeat(3) { assertNull(set.imageUri("looks/nobody.png")) }

    // One walk down the folders per distinct path, however often it is asked for.
    assertEquals(2, art.listings)
    assertEquals(2, looks.listings)
  }

  @Test
  fun `resolving every image does the work up front and leaves later lookups free`() {
    val set = PlayerCardSet(cards("looks/carl.png", "looks/ghost.png", "looks/carl.png", "portrait.png"), art)

    set.resolveAllImages()
    val artListings = art.listings
    val looksListings = looks.listings
    assertEquals(ghost.uri, set.imageUri("looks/ghost.png"))
    assertEquals(portrait.uri, set.imageUri("portrait.png"))

    assertEquals(3, artListings)
    assertEquals(2, looksListings)
    assertEquals(artListings, art.listings)
    assertEquals(looksListings, looks.listings)
  }
}
