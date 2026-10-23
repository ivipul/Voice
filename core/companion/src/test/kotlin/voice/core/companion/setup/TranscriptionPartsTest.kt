package voice.core.companion.setup

import voice.core.ai.SpokenLine
import voice.core.transcript.TranscriptCue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TranscriptionPartsTest {

  @Test
  fun aBookIsCutIntoEvenPartsWithAShortLastOne() {
    val parts = AudioChunker.partsOf(durationMs = 50_000L, partMs = 20_000L)

    assertEquals(listOf(0L to 20_000L, 20_000L to 40_000L, 40_000L to 50_000L), parts.map { it.startMs to it.endMs })
    assertEquals(listOf(0, 1, 2), parts.map { it.index })
  }

  @Test
  fun aShortFileIsOnePart() {
    assertEquals(1, AudioChunker.partsOf(durationMs = 5_000L, partMs = 20_000L).size)
  }

  @Test
  fun linesAreShiftedToWhereTheirPartStarts() {
    val cues = listOf(SpokenLine(startMs = 1_000, endMs = 2_000, text = "Hi")).toCues(offsetMs = 1_200_000L)

    assertEquals(listOf(TranscriptCue(1_201_000L, 1_202_000L, "Hi")), cues)
  }

  @Test
  fun overlappingPartsDropTheRepeatedLineAndKeepTimeOrder() {
    val merged = mergeCues(
      listOf(
        TranscriptCue(1_200_100L, 1_203_000L, "Across the cut."),
        TranscriptCue(1_203_500L, 1_205_000L, "Second part."),
        TranscriptCue(1_198_000L, 1_200_000L, "First part."),
        TranscriptCue(1_199_900L, 1_203_000L, "Across the cut."),
      ),
    )

    assertEquals(listOf("First part.", "Across the cut.", "Second part."), merged.map { it.text })
  }

  @Test
  fun adtsHeaderDescribesTheFrame() {
    // AAC-LC, 44.1 kHz, stereo: AudioSpecificConfig 0x12 0x10.
    val config = AdtsConfig.fromAudioSpecificConfig(0x12, 0x10)
    assertEquals(AdtsConfig(profile = 2, frequencyIndex = 4, channelConfig = 2), config)

    val header = config.header(payloadSize = 371)
    assertContentEquals(
      byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x2F, 0x5F, 0xFC.toByte()),
      header,
    )
  }

  @Test
  fun heAacIsSentAsLowComplexity() {
    // HE-AAC (object type 5), 24 kHz core, stereo.
    assertEquals(2, AdtsConfig.fromAudioSpecificConfig(0x2B, 0x12).profile)
  }
}
