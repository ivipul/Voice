package voice.core.playback.session

import kotlinx.serialization.json.Json
import voice.core.data.CoPilotButtonMapping
import voice.core.data.CoPilotTriggerAction
import voice.core.data.actionFor
import kotlin.test.Test
import kotlin.test.assertEquals

class CoPilotButtonMappingTest {

  @Test
  fun `triple NEXT defaults to Auto-Identify`() {
    assertEquals(CoPilotTriggerAction.AutoIdentify, CoPilotButtonMapping().tripleNext)
  }

  @Test
  fun `press counts pick the mapped slot`() {
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
  fun `a mapping saved before triple NEXT existed still loads with the default`() {
    val legacy = """{"singleNext":"OpenMic","doubleNext":"CatchMeUp"}"""

    val mapping = Json.decodeFromString(CoPilotButtonMapping.serializer(), legacy)

    assertEquals(CoPilotTriggerAction.OpenMic, mapping.singleNext)
    assertEquals(CoPilotTriggerAction.CatchMeUp, mapping.doubleNext)
    assertEquals(CoPilotTriggerAction.AutoIdentify, mapping.tripleNext)
  }
}
