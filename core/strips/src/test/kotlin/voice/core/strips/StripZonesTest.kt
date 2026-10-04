package voice.core.strips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StripZonesTest {

  // First frame at 1000, last at 6000 with a 10000 hold: the range is 1000 until 16000.
  private val first = AvailableStrip(manifest(starts = listOf(1_000L, 3_000L, 6_000L), holdMs = 10_000L), File("a"))
  private val second = AvailableStrip(
    manifest(starts = listOf(40_000L, 50_000L), holdMs = 5_000L).copy(chapter = 2),
    File("b"),
  )

  @Test
  fun `a strip's range runs from its first frame to the end of the last frame's hold`() {
    assertEquals(1_000L until 16_000L, first.manifest.rangeMs())
  }

  @Test
  fun `a bar covering the whole book gets each strip as a fraction of it`() {
    val zones = listOf(first, second).zonesWithin(spanStartMs = 0L, spanEndMs = 100_000L)
    assertEquals(
      listOf(StripZone(0.01f, 0.16f, 1_000L, chapter = 1), StripZone(0.4f, 0.55f, 40_000L, chapter = 2)),
      zones,
    )
  }

  @Test
  fun `a per-chapter bar clips a zone that starts before it and keeps the strip start as the target`() {
    // The chapter bar covers 10000..20000, so the first strip's tail (10000..16000) fills 60% of it.
    val zones = listOf(first).zonesWithin(spanStartMs = 10_000L, spanEndMs = 20_000L)
    assertEquals(listOf(StripZone(0f, 0.6f, 1_000L, chapter = 1)), zones)
  }

  @Test
  fun `a strip that ends before the bar or starts after it has no zone`() {
    assertEquals(emptyList(), listOf(first).zonesWithin(spanStartMs = 16_000L, spanEndMs = 30_000L))
    assertEquals(emptyList(), listOf(second).zonesWithin(spanStartMs = 0L, spanEndMs = 40_000L))
  }

  @Test
  fun `an empty or backwards span has no zones`() {
    assertEquals(emptyList(), listOf(first).zonesWithin(spanStartMs = 5_000L, spanEndMs = 5_000L))
  }

  @Test
  fun `a tap inside a zone targets the strip start, outside it targets nothing`() {
    val zones = listOf(first, second).zonesWithin(spanStartMs = 0L, spanEndMs = 100_000L)
    assertEquals(1_000L, zones.stripStartAt(0.05f))
    assertEquals(40_000L, zones.stripStartAt(0.5f))
    assertNull(zones.stripStartAt(0.3f))
    assertNull(zones.stripStartAt(0.9f))
  }

  @Test
  fun `before a strip no frame is active`() {
    assertNull(listOf(first).activeFrameAt(999L))
  }

  @Test
  fun `inside a strip the frame is the latest one started at or before the position`() {
    assertEquals(0, listOf(first).activeFrameAt(1_000L)?.frameIndex)
    assertEquals(0, listOf(first).activeFrameAt(2_999L)?.frameIndex)
    assertEquals(1, listOf(first).activeFrameAt(3_000L)?.frameIndex)
    assertEquals(2, listOf(first).activeFrameAt(6_000L)?.frameIndex)
  }

  @Test
  fun `the last frame stays active through its hold and the strip ends after it`() {
    assertEquals(2, listOf(first).activeFrameAt(15_999L)?.frameIndex)
    assertNull(listOf(first).activeFrameAt(16_000L))
  }

  @Test
  fun `with several strips the one containing the position wins`() {
    val strips = listOf(first, second)
    assertEquals(first, strips.activeFrameAt(2_000L)?.strip)
    assertEquals(second, strips.activeFrameAt(45_000L)?.strip)
    assertNull(strips.activeFrameAt(30_000L))
  }

  @Test
  fun `overlapping strips use the later starting one`() {
    val overlapping = AvailableStrip(
      manifest(starts = listOf(5_000L, 7_000L), holdMs = 5_000L).copy(chapter = 3),
      File("c"),
    )
    assertEquals(overlapping, listOf(first, overlapping).activeFrameAt(5_500L)?.strip)
  }
}
