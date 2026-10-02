package voice.core.copilot.frame

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FrameCharacterTest {

  private fun look(seq: Int, firstMs: Long, withImages: Boolean = true) = FrameLook(
    seq = seq,
    firstMs = firstMs,
    description = "look $seq",
    standing = if (withImages) File("look-$seq-standing.png") else null,
    action = if (withImages) File("look-$seq-action.png") else null,
  )

  private val carl = FrameCharacter(
    name = "Carl",
    slug = "carl",
    looks = listOf(look(1, 93_800), look(2, 6_436_624), look(3, 10_525_000), look(4, 13_699_000, withImages = false)),
  )

  @Test
  fun `the look in force is the latest one at or before the snip`() {
    assertEquals(2, carl.lookAt(7_000_000)?.seq)
    assertEquals(3, carl.lookAt(10_525_000)?.seq)
  }

  @Test
  fun `before the first look it uses the earliest, never a later one`() {
    assertEquals(1, carl.lookAt(10_000)?.seq)
  }

  @Test
  fun `a look with no image on disk falls back to the latest earlier look that has one`() {
    assertEquals(3, carl.lookAt(20_000_000)?.seq)
  }

  @Test
  fun `a character whose looks have no images has no look`() {
    assertEquals(null, FrameCharacter("Zev", "zev", listOf(look(1, 0, withImages = false))).lookAt(5_000))
  }

  @Test
  fun `a look's references are its standing then action image`() {
    assertEquals(listOf(File("look-2-standing.png"), File("look-2-action.png")), carl.lookAt(7_000_000)?.references)
  }
}
