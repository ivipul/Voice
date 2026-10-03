package voice.core.strips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StripTimelineTest {

  private val strip = manifest(
    starts = listOf(1_000L, 3_000L, 6_000L),
    holdMs = 10_000L,
  )

  @Test
  fun stripLocksBeforeItsFirstFrameAndUnlocksFromItsStart() {
    val available = AvailableStrip(strip, java.io.File("strip"))
    assertFalse(available.isUnlockedAt(999L))
    assertTrue(available.isUnlockedAt(1_000L))
    assertTrue(available.isUnlockedAt(5_000L))
  }

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

  private val withStrip = manifest(
    starts = listOf(1_000L, 3_000L, 6_000L),
    holdMs = 10_000L,
    stripImage = "ch01-strip.png",
  )

  @Test
  fun aStripImageAddsOneFrameToTheCount() {
    assertEquals(3, strip.frameCount)
    assertEquals(4, withStrip.frameCount)
  }

  @Test
  fun theFullStripShowsAfterTheLastFrameHoldForSevenSecondsThenTheStoryEnds() {
    assertEquals(2, withStrip.frameIndexAt(15_999L))
    assertEquals(3, withStrip.frameIndexAt(16_000L))
    assertEquals(3, withStrip.frameIndexAt(22_999L))
    assertNull(withStrip.frameIndexAt(23_000L))
    assertEquals(23_000L, withStrip.storyEndMs())
  }

  @Test
  fun withoutAStripImageTheStoryEndsWithTheLastFrameHold() {
    assertEquals(16_000L, strip.storyEndMs())
    assertNull(strip.frameIndexAt(16_000L))
  }

  @Test
  fun theLastComicFrameLeadsToTheFullStripAndTheFullStripHasNoNext() {
    assertEquals(16_000L, withStrip.nextSeekMs(2))
    assertNull(withStrip.nextSeekMs(3))
  }

  @Test
  fun backFromTheFullStripGoesToTheLastComicFrame() {
    assertEquals(6_000L, withStrip.previousSeekMs(3))
  }

  @Test
  fun segmentsCoverTheLastFrameHoldAndTheFullStripHold() {
    assertEquals(16_000L, withStrip.segmentEndMs(2))
    assertEquals(23_000L, withStrip.segmentEndMs(3))
  }
}

internal fun manifest(
  starts: List<Long>,
  holdMs: Long,
  book: Int = 1,
  prefix: String = "Dungeon Crawler Carl",
  heroFrames: List<Int> = emptyList(),
  stripImage: String? = null,
): StripManifest = StripManifest(
  schemaVersion = 1,
  book = book,
  bookTitlePrefix = prefix,
  chapter = 1,
  title = "The vanishing",
  summary = "summary",
  heroFrames = heroFrames,
  lastFrameHoldMs = holdMs,
  stripImage = stripImage,
  frames = starts.mapIndexed { index, start ->
    StripFrame(frame = index + 1, image = "f${index + 1}-final.png", startMs = start)
  },
)
