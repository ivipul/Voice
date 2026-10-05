package voice.core.copilot

import kotlin.test.Test
import kotlin.test.assertEquals

class SnipTextTest {

  @Test
  fun theFirstLineIsTheTitleAndTheRestIsTheMoment() {
    assertEquals(
      SnipText(title = "Donut Picks a Fight", text = "The cat hisses at the llama. The llama hisses back."),
      snipTextOf("Donut Picks a Fight\nThe cat hisses at the llama.\nThe llama hisses back."),
    )
  }

  @Test
  fun labelsQuotesAndMarkdownAroundTheTitleAreDropped() {
    assertEquals("Mongo Eats a Goblin", snipTextOf("**Title:** \"Mongo Eats a Goblin.\"\nCrunch.").title)
    assertEquals("Stairwell Panic", snipTextOf("# Stairwell Panic\n\nThe crawlers run.").title)
  }

  @Test
  fun aSnipWithoutATitleLineIsOnlyTheMoment() {
    assertEquals(SnipText(title = null, text = "Carl kicks the door in."), snipTextOf("  Carl kicks the door in.  "))
  }

  @Test
  fun aLongFirstLineIsPartOfTheMomentNotATitle() {
    val snip = "Carl kicks the door in and the whole floor collapses under a hundred screaming goblins.\nThen silence."

    assertEquals(SnipText(title = null, text = snip), snipTextOf(snip))
  }
}
