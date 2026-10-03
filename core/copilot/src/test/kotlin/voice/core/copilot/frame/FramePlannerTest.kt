package voice.core.copilot.frame

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import voice.core.copilot.GeminiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FramePlannerTest {

  private val geminiClient = mockk<GeminiClient>()
  private val planner = FramePlanner(geminiClient)

  private fun reply(pose: String) = """
    ```json
    {
      "palette": "the same cold blue night palette",
      "location": "Empty street at night with a bare tree",
      "viewpoint": "The camera is at street level looking up",
      "room_for": "Leave an empty stretch of sidewalk beside the tree",
      "characters": [{
        "character": "Carl",
        "noun": "man",
        "pron": "he",
        "description": "a tall man in a leather jacket",
        "placement": "beside the tree",
        "framing": "full_body",
        "prominence_pct": 75,
        "pose": "$pose",
        "gaze": "head turned in profile, face not covered",
        "mask_box": [0.1, 0.2, 0.6, 0.95]
      }]
    }
    ```
  """.trimIndent()

  @Test
  fun `a clean plan is parsed and validated`() = runTest {
    coEvery { geminiClient.askJson(any(), any(), any()) } returns reply("He reaches up toward the branches")

    val plan = planner.plan("a highlight", "the transcript", listOf("Carl"))

    assertEquals("Carl", plan.characters.single().character)
    assertEquals(75, plan.characters.single().prominencePct)
  }

  @Test
  fun `the excerpt, caption and known names go to Gemini`() = runTest {
    val userPrompt = slot<String>()
    coEvery { geminiClient.askJson(any(), capture(userPrompt), any()) } returns reply("He reaches up")

    val plan = planner.plan("a highlight", "the transcript", listOf("Carl", "Princess Donut"))

    assertEquals(1, plan.characters.size)
    assertTrue(userPrompt.captured.contains("a highlight"))
    assertTrue(userPrompt.captured.contains("the transcript"))
    assertTrue(userPrompt.captured.contains("Carl, Princess Donut"))
  }

  @Test
  fun `a plan that names a character is retried once with the complaint fed back`() = runTest {
    val prompts = mutableListOf<String>()
    coEvery { geminiClient.askJson(any(), capture(prompts), any()) } returnsMany listOf(
      reply("Carl reaches up toward the branches"),
      reply("He reaches up toward the branches"),
    )

    val plan = planner.plan("a highlight", "the transcript", listOf("Carl"))

    assertEquals("He reaches up toward the branches", plan.characters.single().pose)
    assertEquals(2, prompts.size)
    assertTrue(prompts[1].contains("Your previous answer was rejected"))
    assertTrue(prompts[1].contains("\"Carl\""))
  }

  @Test
  fun `two bad plans in a row give up`() = runTest {
    var calls = 0
    coEvery { geminiClient.askJson(any(), any(), any()) } answers {
      calls++
      reply("Carl reaches up")
    }

    assertFailsWith<InvalidFramePlanException> { planner.plan("a highlight", "the transcript", listOf("Carl")) }

    assertEquals(2, calls)
  }

  @Test
  fun `unreadable JSON is retried too`() = runTest {
    coEvery { geminiClient.askJson(any(), any(), any()) } returnsMany listOf("not json at all", reply("He reaches up"))

    val plan = planner.plan("a highlight", "the transcript", emptyList())

    assertEquals(1, plan.characters.size)
  }
}
