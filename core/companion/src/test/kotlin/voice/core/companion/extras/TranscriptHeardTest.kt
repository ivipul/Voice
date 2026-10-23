package voice.core.companion.extras

import voice.core.transcript.TranscriptCue
import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptHeardTest {

  @Test
  fun onlyLinesThatStartedByThePositionCountAsHeard() {
    val cues = listOf(
      TranscriptCue(timestampStartMs = 0, timestampEndMs = 4_000, text = "one"),
      TranscriptCue(timestampStartMs = 4_000, timestampEndMs = 9_000, text = "two"),
      TranscriptCue(timestampStartMs = 9_500, timestampEndMs = 12_000, text = "three"),
    )

    assertEquals(listOf("one", "two"), cues.heardBy(positionMs = 5_000).map { it.text })
    assertEquals(emptyList(), cues.heardBy(positionMs = -1).map { it.text })
  }
}
