package voice.core.copilot.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechChunkerTest {

  private val sentence = "This is a reasonably long sentence about the dungeon crawl and its rules."

  @Test
  fun `blank text gives nothing to speak`() {
    assertEquals(emptyList(), SpeechChunker.split(""))
    assertEquals(emptyList(), SpeechChunker.split("   "))
  }

  @Test
  fun `the first chunk is the first sentence and later sentences are merged`() {
    val chunks = SpeechChunker.split("Carl is trapped in a dungeon with his cat Donut. The System mocks him constantly with new rules. He keeps going anyway.")

    assertEquals("Carl is trapped in a dungeon with his cat Donut.", chunks.first())
    assertEquals(listOf("The System mocks him constantly with new rules. He keeps going anyway."), chunks.drop(1))
  }

  @Test
  fun `abbreviations and decimals do not end a sentence`() {
    val chunks = SpeechChunker.split("Mr. Smith owes the guild 3.5 million gold and Dr. Jones wants it back by tomorrow morning. Then something else happens in the story later.")

    assertEquals("Mr. Smith owes the guild 3.5 million gold and Dr. Jones wants it back by tomorrow morning.", chunks.first())
  }

  @Test
  fun `short sentences are merged so no chunk is under eight words`() {
    val chunks = SpeechChunker.split("Okay. Sure. Carl just reached the stairwell and the timer is almost out. Go. Now.")

    assertTrue(chunks.all { it.split(' ').size >= 8 }, chunks.toString())
    assertEquals("Okay. Sure. Carl just reached the stairwell and the timer is almost out. Go. Now.", chunks.joinToString(" "))
  }

  @Test
  fun `a short answer stays one chunk`() {
    assertEquals(listOf("Yes."), SpeechChunker.split("Yes."))
  }

  @Test
  fun `one very long sentence is cut at commas and never exceeds the cap`() {
    val long = List(12) { "the crawlers keep running through another long corridor" }.joinToString(", ") + "."
    val chunks = SpeechChunker.split(long)

    assertTrue(chunks.size > 2)
    assertTrue(chunks.all { it.length <= 200 }, chunks.map { it.length }.toString())
    assertEquals(long.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
  }

  @Test
  fun `a long first sentence is cut at a comma so the first chunk stays short`() {
    val text = "Carl has just stepped onto the second floor of the dungeon with his cat, and the System is already counting down the timer for every crawler."
    val chunks = SpeechChunker.split(text)

    assertEquals("Carl has just stepped onto the second floor of the dungeon with his cat,", chunks.first())
    assertEquals(text, chunks.joinToString(" "))
  }

  @Test
  fun `later chunks are bounded by the cap`() {
    val chunks = SpeechChunker.split(List(8) { sentence }.joinToString(" "))

    assertEquals(sentence, chunks.first())
    assertTrue(chunks.drop(1).all { it.length <= 200 })
    assertEquals(List(8) { sentence }.joinToString(" "), chunks.joinToString(" "))
  }
}
