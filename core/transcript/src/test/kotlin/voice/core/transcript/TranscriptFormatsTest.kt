package voice.core.transcript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptFormatsTest {

  @Test
  fun `reads whisper cpp json as OpenAudible exports it`() {
    val cues = TranscriptParser.parse(
      """
      {"systeminfo": "x", "model": {"type": "medium"}, "transcription": [
        {"timestamps": {"from": "00:00:00,000", "to": "00:00:03,840"}, "offsets": {"from": 0, "to": 3840}, "text": " This is Audible."},
        {"timestamps": {"from": "00:00:03,840", "to": "00:00:04,000"}, "offsets": {"from": 3840, "to": 4000}, "text": " "}
      ]}
      """.trimIndent(),
    )

    assertEquals(listOf(TranscriptCue(0, 3840, "This is Audible.")), cues)
  }

  @Test
  fun `reads openai whisper segments in seconds`() {
    val cues = TranscriptParser.parse("""{"text": "all", "segments": [{"start": 1.5, "end": 2.25, "text": " Hi"}]}""")

    assertEquals(listOf(TranscriptCue(1500, 2250, "Hi")), cues)
  }

  @Test
  fun `rejects json that is not a transcript`() {
    assertFailsWith<IllegalArgumentException> { TranscriptParser.parse("""{"title": "Good Energy", "author": "Casey Means"}""") }
  }

  @Test
  fun `writes the app format back readably`() {
    val cues = listOf(TranscriptCue(0, 10, "a"), TranscriptCue(10, 20, "b"))

    assertEquals(cues, TranscriptParser.parse(TranscriptParser.encode(cues)))
  }

  @Test
  fun `reads srt subtitles`() {
    val srt = "1\r\n00:00:00,000 --> 00:00:03,840\r\n This is Audible.\r\n\r\n2\r\n00:00:03,840 --> 00:00:29,840\r\n" +
      " Penguin Random House\r\n<i>Audio</i> presents\r\n\r\n3\r\n01:02:03,5 --> 01:02:04,250\r\n\r\n"

    assertEquals(
      listOf(TranscriptCue(0, 3840, "This is Audible."), TranscriptCue(3840, 29840, "Penguin Random House Audio presents")),
      SrtParser.parse(srt),
    )
  }

  @Test
  fun `transcripts beside an audio file can keep its extension`() {
    assertEquals(
      listOf("Good Energy.json", "Good Energy.m4b.json", "Good Energy.srt", "Good Energy.m4b.srt"),
      transcriptNamesFor("Good Energy.m4b"),
    )
    assertTrue(isTranscriptFor("good energy.M4B.json", "Good Energy.m4b"))
    assertFalse(isTranscriptFor("Good Energy.pdf", "Good Energy.m4b"))
  }
}
