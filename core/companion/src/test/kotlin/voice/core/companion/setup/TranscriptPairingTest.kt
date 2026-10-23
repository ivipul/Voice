package voice.core.companion.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptPairingTest {

  @Test
  fun eachAudioFileGetsTheTranscriptOfTheSameName() {
    val pairs = pairTranscripts(listOf("Book 1.m4b", "Book 2.mp3", "book 1.JSON", "notes.json"))

    assertEquals(listOf("Book 1.m4b" to "book 1.JSON", "Book 2.mp3" to null), pairs)
  }

  @Test
  fun oneAudioFileAndOneTranscriptBelongTogetherWhateverTheirNames() {
    assertEquals(listOf("audiobook.m4b" to "transcript.json"), pairTranscripts(listOf("transcript.json", "audiobook.m4b")))
  }

  @Test
  fun anAudioFileAloneHasNoTranscriptAndATranscriptAloneIsNoBook() {
    assertEquals(listOf("Book.m4b" to null), pairTranscripts(listOf("Book.m4b")))
    assertEquals(emptyList(), pairTranscripts(listOf("Book.json")))
  }
}
