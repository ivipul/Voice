package voice.core.xray

import org.junit.Assert.assertEquals
import org.junit.Test

class XRayManifestTest {

  private val carl = XRayEntityInfo(id = "carl", title = "Carl", description = "A crawler.")
  private val donut = XRayEntityInfo(id = "donut", title = "Princess Donut", description = "A cat.")
  private val manifest = XRayManifest(
    entities = listOf(carl, donut),
    timeline = listOf(
      XRayEntityCue(entity = "carl", startMs = 0, endMs = 1_000),
      XRayEntityCue(entity = "donut", startMs = 500, endMs = 1_500),
      XRayEntityCue(entity = "carl", startMs = 2_000, endMs = 3_000),
    ),
  )

  @Test
  fun `only carl is active before donut enters`() {
    assertEquals(listOf(carl), manifest.activeEntities(positionMs = 100))
  }

  @Test
  fun `both are active once overlapping`() {
    assertEquals(listOf(carl, donut), manifest.activeEntities(positionMs = 600))
  }

  @Test
  fun `none are active in a gap`() {
    assertEquals(emptyList<XRayEntityInfo>(), manifest.activeEntities(positionMs = 1_800))
  }

  @Test
  fun `carl reappears in his second range`() {
    assertEquals(listOf(carl), manifest.activeEntities(positionMs = 2_500))
  }
}
