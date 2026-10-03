package voice.core.strips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StripTimelineTest {

  private val strip = manifest(
    starts = listOf(1_000L, 3_000L, 6_000L),
    holdMs = 10_000L,
  )

  @Test
  fun frameIndexIsTheLastFrameStartedAtOrBeforePosition() {
    assertEquals(0, strip.frameIndexAt(1_000L))
    assertEquals(0, strip.frameIndexAt(2_999L))
    assertEquals(1, strip.frameIndexAt(3_000L))
    assertEquals(2, strip.frameIndexAt(9_000L))
  }

  @Test
  fun frameIndexIsNullOutsideTheStripRange() {
    assertNull(strip.frameIndexAt(999L))
    assertNull(strip.frameIndexAt(16_000L))
  }

  @Test
  fun segmentEndsAtNextFrameOrAtLastFrameHold() {
    assertEquals(3_000L, strip.segmentEndMs(0))
    assertEquals(6_000L, strip.segmentEndMs(1))
    assertEquals(16_000L, strip.segmentEndMs(2))
  }

  @Test
  fun nextSeekIsNextFrameStartOrNullOnLastFrame() {
    assertEquals(3_000L, strip.nextSeekMs(0))
    assertEquals(6_000L, strip.nextSeekMs(1))
    assertNull(strip.nextSeekMs(2))
  }

  @Test
  fun previousSeekIsPreviousFrameStartAndRestartsFirstFrame() {
    assertEquals(1_000L, strip.previousSeekMs(0))
    assertEquals(1_000L, strip.previousSeekMs(1))
    assertEquals(3_000L, strip.previousSeekMs(2))
  }
}

internal fun manifest(
  starts: List<Long>,
  holdMs: Long,
  book: Int = 1,
  prefix: String = "Dungeon Crawler Carl",
  heroFrames: List<Int> = emptyList(),
): StripManifest = StripManifest(
  schemaVersion = 1,
  book = book,
  bookTitlePrefix = prefix,
  chapter = 1,
  title = "The vanishing",
  summary = "summary",
  heroFrames = heroFrames,
  lastFrameHoldMs = holdMs,
  frames = starts.mapIndexed { index, start ->
    StripFrame(frame = index + 1, image = "f${index + 1}-final.png", startMs = start)
  },
)
