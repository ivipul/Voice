package voice.core.copilot.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechChunkerTest {

  private val sentence = "This is a reasonably long sentence about the dungeon crawl and its rules."

  private fun streamed(text: String, step: Int): List<String> {
    val chunker = SpeechChunker()
    val out = mutableListOf<String>()
    text.chunked(step).forEach { out += chunker.feed(it) }
    out += chunker.finish()
    return out
  }

  private fun joined(chunks: List<String>) = chunks.joinToString(" ")

  @Test
  fun `blank text gives nothing to speak`() {
    assertEquals(emptyList(), SpeechChunker.split(""))
    assertEquals(emptyList(), SpeechChunker.split("   "))
  }

  @Test
  fun `the first chunk is the first sentence and later sentences are packed`() {
    val chunks = SpeechChunker.split("Carl is trapped in a dungeon with his cat Donut. The System mocks him constantly with new rules. He keeps going anyway.")

    assertEquals("Carl is trapped in a dungeon with his cat Donut.", chunks.first())
    assertEquals("The System mocks him constantly with new rules. He keeps going anyway.", joined(chunks.drop(1)))
  }

  @Test
  fun `abbreviations and decimals do not end a sentence`() {
    val chunks = SpeechChunker.split("Mr. Smith owes the guild 3.5 million gold and Dr. Jones wants it back by tomorrow morning. Then something else happens.")

    assertEquals("Mr. Smith owes the guild 3.5 million gold and Dr. Jones wants it back by tomorrow morning.", chunks.first())
  }

  @Test
  fun `short sentences are merged forward until the first chunk has eight words`() {
    val chunks = SpeechChunker.split("Okay. Sure. Carl just reached the stairwell and the timer is almost out.")

    assertEquals(listOf("Okay. Sure. Carl just reached the stairwell and the timer is almost out."), chunks)
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
    assertEquals(text, joined(chunks))
  }

  @Test
  fun `deltas split mid-word and mid-sentence give the same chunks as the whole text`() {
    val text = List(6) { sentence }.joinToString(" ") + " And a final partial sentence without a full stop"
    val whole = SpeechChunker.split(text)

    for (step in listOf(1, 3, 7, 25, 61)) assertEquals(whole, streamed(text, step), "step $step")
    assertEquals(text, joined(whole))
  }

  @Test
  fun `the first chunk is emitted as soon as its sentence is complete, before later text arrives`() {
    val chunker = SpeechChunker()

    assertEquals(emptyList(), chunker.feed("Carl has just stepped onto the second floor"))
    assertEquals(emptyList(), chunker.feed(" of the dungeon."))
    assertEquals(listOf("Carl has just stepped onto the second floor of the dungeon."), chunker.feed(" The Sys"))
    assertEquals(emptyList(), chunker.feed("tem mocks everyone loudly all day long, and then some more. "))
    assertEquals(listOf("The System mocks everyone loudly all day long, and then some more."), chunker.finish())
  }

  @Test
  fun `an abbreviation split across deltas is not treated as a sentence end`() {
    val chunker = SpeechChunker()
    val out = chunker.feed("Please ask Mr") + chunker.feed(". Smith about the very long way to the stairwell today.") + chunker.finish()

    assertEquals(listOf("Please ask Mr. Smith about the very long way to the stairwell today."), out)
  }

  @Test
  fun `the final partial sentence is flushed by finish`() {
    val chunker = SpeechChunker()

    assertEquals(emptyList(), chunker.feed("A quick answer with no full stop"))
    assertEquals(listOf("A quick answer with no full stop"), chunker.finish())
  }

  @Test
  fun `later chunks are bounded by the cap`() {
    val chunks = SpeechChunker.split(List(8) { sentence }.joinToString(" "))

    assertEquals(sentence, chunks.first())
    assertTrue(chunks.all { it.length <= 200 })
    assertEquals(List(8) { sentence }.joinToString(" "), joined(chunks))
  }
}
