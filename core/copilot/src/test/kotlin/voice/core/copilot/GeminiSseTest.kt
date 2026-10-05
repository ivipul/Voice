package voice.core.copilot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeminiSseTest {

  @Test
  fun `a data line yields its text`() {
    val line = """data: {"candidates": [{"content": {"parts": [{"text": "Carl is "}],"role": "model"},"index": 0}],"usageMetadata": {"promptTokenCount": 10}}"""

    assertEquals("Carl is ", GeminiClient.parseSseDelta(line))
  }

  @Test
  fun `multiple parts are joined and thought parts are skipped`() {
    val line = """data: {"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Hello"},{"text":" there"}]}}]}"""

    assertEquals("Hello there", GeminiClient.parseSseDelta(line))
  }

  @Test
  fun `blank lines, comments and done markers yield nothing`() {
    assertNull(GeminiClient.parseSseDelta(""))
    assertNull(GeminiClient.parseSseDelta(": keep-alive"))
    assertNull(GeminiClient.parseSseDelta("data: [DONE]"))
    assertNull(GeminiClient.parseSseDelta("event: message"))
  }

  @Test
  fun `a chunk with no candidates or text yields nothing`() {
    assertNull(GeminiClient.parseSseDelta("""data: {"usageMetadata": {"totalTokenCount": 5}}"""))
    assertEquals("", GeminiClient.parseSseDelta("""data: {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":""}]}}]}"""))
  }

  @Test
  fun `data without a space after the colon is accepted`() {
    assertEquals("Hi", GeminiClient.parseSseDelta("""data:{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}"""))
  }
}
