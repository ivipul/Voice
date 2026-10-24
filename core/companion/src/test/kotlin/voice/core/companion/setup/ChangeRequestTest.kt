package voice.core.companion.setup

import voice.core.companion.IdeaFeature
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeRequestTest {

  private val before = listOf(IdeaFeature("Daily log"), IdeaFeature("Weekly chart"), IdeaFeature("Export", included = false))

  @Test
  fun tickedAndUntickedPartsComeBeforeTheTypedRequest() {
    val after = listOf(IdeaFeature("Daily log"), IdeaFeature("Weekly chart", included = false), IdeaFeature("Export"))

    assertEquals(
      "Add these parts: Export.\nRemove these parts: Weekly chart.\nUse bigger buttons",
      changeRequest(before, after, typed = "  Use bigger buttons "),
    )
  }

  @Test
  fun onlyTheTypedRequestWhenNoPartChanged() {
    assertEquals("Make it darker", changeRequest(before, before, typed = "Make it darker"))
  }
}
