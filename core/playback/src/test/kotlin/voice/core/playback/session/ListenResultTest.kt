package voice.core.playback.session

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenResultTest {

  @Test
  fun `speech timeout and no match count as silence`() {
    assertTrue(recognizerErrorIsSilence(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    assertTrue(recognizerErrorIsSilence(SpeechRecognizer.ERROR_NO_MATCH))
  }

  @Test
  fun `real recognizer failures are not silence`() {
    listOf(
      SpeechRecognizer.ERROR_NETWORK,
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
      SpeechRecognizer.ERROR_AUDIO,
      SpeechRecognizer.ERROR_SERVER,
      SpeechRecognizer.ERROR_CLIENT,
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
    ).forEach { error ->
      assertFalse(recognizerErrorIsSilence(error), "error $error")
    }
  }

  @Test
  fun `recognized text is heard`() {
    assertEquals(ListenResult.Heard("who is Carl?"), listenResultFor(listOf("who is Carl?", "who is call")))
  }

  @Test
  fun `empty or blank results are silence`() {
    assertEquals(ListenResult.Silence, listenResultFor(null))
    assertEquals(ListenResult.Silence, listenResultFor(emptyList()))
    assertEquals(ListenResult.Silence, listenResultFor(listOf("  ")))
  }
}
