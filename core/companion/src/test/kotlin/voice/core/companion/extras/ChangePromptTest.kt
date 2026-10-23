package voice.core.companion.extras

import voice.core.companion.Extra
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangePromptTest {

  private val extra = Extra(id = "x1", title = "Glucose log", description = "Track readings", html = "<p>log</p>", createdAtMs = 0)

  @Test
  fun theListenersRequestAndThePageGoIn() {
    val prompt = changePrompt(extra, request = "  Add a weekly average  ", error = null)

    assertTrue("The listener asks: Add a weekly average" in prompt)
    assertTrue("<p>log</p>" in prompt)
    assertFalse("error" in prompt)
  }

  @Test
  fun anEmptyRequestStillFixesTheLastError() {
    val prompt = changePrompt(extra, request = "", error = "TypeError: x is undefined")

    assertFalse("The listener asks" in prompt)
    assertTrue("TypeError: x is undefined" in prompt)
  }
}
