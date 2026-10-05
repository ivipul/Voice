package voice.core.copilot.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechChunkerTest {

  @Test
  fun `blank text gives nothing to speak`() {
    assertEquals(emptyList(), SpeechChunker.split("   "))
  }

  @Test
  fun `the first chunk is a single sentence`() {
    val chunks = SpeechChunker.split("Carl is trapped in a dungeon with his cat. The System mocks him. He keeps going.")

    assertEquals("Carl is trapped in a dungeon with his cat.", chunks.first())
    assertEquals(listOf("The System mocks him. He keeps going."), chunks.drop(1))
  }

  @Test
  fun `a very short opener is joined to the next sentence`() {
    val chunks = SpeechChunker.split("Okay. Carl just reached the stairwell and the timer is almost out.")

    assertEquals(listOf("Okay. Carl just reached the stairwell and the timer is almost out."), chunks)
  }

  @Test
  fun `later sentences are merged but each chunk stays bounded`() {
    val sentence = "This is a reasonably long sentence about the dungeon crawl and its rules."
    val chunks = SpeechChunker.split(List(8) { sentence }.joinToString(" "))

    assertEquals(sentence, chunks.first())
    assertTrue(chunks.drop(1).all { it.length <= 220 })
    assertEquals(List(8) { sentence }.joinToString(" "), chunks.joinToString(" "))
  }

  @Test
  fun `a long first sentence is cut at a comma so the opener stays short`() {
    val sentence = "Carl has just stepped onto the second floor of the dungeon, and the System is already counting down the timer for every crawler."
    val chunks = SpeechChunker.split(sentence)

    assertEquals("Carl has just stepped onto the second floor of the dungeon,", chunks.first())
    assertEquals(sentence, chunks.joinToString(" "))
  }
}
