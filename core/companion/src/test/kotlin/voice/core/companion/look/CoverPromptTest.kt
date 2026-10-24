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
    assertTrue("@img_1 is a reference for the characters, their poses and the props only: do not copy its layout" in request)
    assertTrue("remove every other word" in request)
  }

  @Test
  fun aReimaginedCoverStillAvoidsTheLastComposition() {
    val request = coverPromptRequest(book = "Book: \"Good Energy\"", visualStyle = "", extraPrompt = "", lastComposition = "stacked", hasReference = true)

    assertTrue("The last cover used the stacked composition" in request)
  }

  @Test
  fun aRestyledCoverIsToldItsClosestPalette() {
    val palette = CURATED_PALETTES.first { it.name == "Forest & Gold" }

    val request = coverPromptRequest(book = "Book: \"Good Energy\"", visualStyle = "", extraPrompt = "", lastComposition = "", hasReference = true, palettes = listOf(palette))

    assertTrue("Use the Forest & Gold palette (#2F4B3A / #F5ECD6 / #E3B34A / #D97B5A)" in request)
  }

  @Test
  fun aRedrawOffersSeveralPalettesToChooseFrom() {
    val palettes = CURATED_PALETTES.take(3)

    val request = coverPromptRequest(book = "Book: \"Good Energy\"", visualStyle = "", extraPrompt = "", lastComposition = "", hasReference = true, palettes = palettes)

    assertTrue("whichever of these palettes suits the book and its cover best" in request)
    palettes.forEach { assertTrue("- ${it.name}: ${it.inks()}" in request) }
  }
}
