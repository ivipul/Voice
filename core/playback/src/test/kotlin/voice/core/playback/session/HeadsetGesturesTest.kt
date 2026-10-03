package voice.core.playback.session

import voice.core.data.CoPilotButtonMapping
import voice.core.data.CoPilotTriggerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadsetGesturesTest {

  private val next = 87
  private val previous = 88
  private val counter = HeadsetPressCounter(maxGapMs = 1500)

  @Test
  fun `presses of the same key inside the gap build up a sequence`() {
    assertEquals(1, counter.press(next, atMs = 0))
    assertEquals(2, counter.press(next, atMs = 500))
    assertEquals(3, counter.press(next, atMs = 1000))
  }

  @Test
  fun `a press after the gap starts a new sequence`() {
    counter.press(next, atMs = 0)

    assertEquals(1, counter.press(next, atMs = 1501))
  }

  @Test
  fun `a different key starts a new sequence`() {
    counter.press(next, atMs = 0)

    assertEquals(1, counter.press(previous, atMs = 100))
  }

  @Test
  fun `reset forgets the sequence`() {
    counter.press(next, atMs = 0)
    counter.reset()

    assertEquals(1, counter.press(next, atMs = 100))
  }

  @Test
  fun `NEXT waits for a third tap but PREVIOUS settles on the second`() {
    assertFalse(isLastPossiblePress(isNext = true, presses = 1))
    assertFalse(isLastPossiblePress(isNext = true, presses = 2))
    assertTrue(isLastPossiblePress(isNext = true, presses = 3))
    assertFalse(isLastPossiblePress(isNext = false, presses = 1))
    assertTrue(isLastPossiblePress(isNext = false, presses = 2))
  }

  @Test
  fun `the mapping picks the action for each gesture`() {
    val mapping = CoPilotButtonMapping(
      singleNext = CoPilotTriggerAction.OpenMic,
      doubleNext = CoPilotTriggerAction.CatchMeUp,
      tripleNext = CoPilotTriggerAction.Snip,
      singlePrevious = CoPilotTriggerAction.DefaultRewind,
      doublePrevious = CoPilotTriggerAction.AutoIdentify,
    )

    assertEquals(CoPilotTriggerAction.OpenMic, mapping.actionFor(isNext = true, presses = 1))
    assertEquals(CoPilotTriggerAction.CatchMeUp, mapping.actionFor(isNext = true, presses = 2))
    assertEquals(CoPilotTriggerAction.Snip, mapping.actionFor(isNext = true, presses = 3))
    assertEquals(CoPilotTriggerAction.DefaultRewind, mapping.actionFor(isNext = false, presses = 1))
    assertEquals(CoPilotTriggerAction.AutoIdentify, mapping.actionFor(isNext = false, presses = 2))
  }

  @Test
  fun `triple NEXT defaults to Auto-Identify`() {
    assertEquals(CoPilotTriggerAction.AutoIdentify, CoPilotButtonMapping().tripleNext)
  }
}
