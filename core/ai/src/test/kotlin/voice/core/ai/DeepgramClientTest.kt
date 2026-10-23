package voice.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepgramClientTest {

  @Test
  fun utterancesBecomeLinesInMilliseconds() {
    val body = """
      {"metadata":{"duration":12.5},"results":{"channels":[],"utterances":[
        {"start":0.08,"end":3.52,"transcript":"Chapter one. ","confidence":0.99},
        {"start":4.0,"end":4.4,"transcript":"  "},
        {"start":4.61,"end":9.9,"transcript":"It was a cold morning."}
      ]}}
    """.trimIndent()

    assertEquals(
      listOf(
        SpokenLine(startMs = 80, endMs = 3_520, text = "Chapter one."),
        SpokenLine(startMs = 4_610, endMs = 9_900, text = "It was a cold morning."),
      ),
      DeepgramClient.parseUtterances(body),
    )
  }
}
