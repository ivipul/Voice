package voice.core.companion.look

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverPromptTest {

  @Test
  fun everyCompositionAndFaceIsOffered() {
    (COVER_COMPOSITIONS.keys + COVER_FONTS.keys).forEach { id -> assertTrue("- $id:" in COVER_PROMPT_SYSTEM, id) }
  }

  @Test
  fun aNewCoverAvoidsTheLastComposition() {
    val request = coverPromptRequest(book = "Book: \"Good Energy\"", visualStyle = "", extraPrompt = "", lastComposition = "editorial", hasReference = false)

    assertTrue("The last cover used the editorial composition" in request)
  }

  @Test
  fun aFirstCoverHasNoCompositionToAvoid() {
    val request = coverPromptRequest(book = "Book: \"Good Energy\"", visualStyle = "", extraPrompt = "more green", lastComposition = "", hasReference = true)

    assertFalse("last cover" in request)
    assertTrue("The listener wants: more green" in request)
    assertTrue("retheme that cover" in request)
  }
}
