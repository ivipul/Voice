package voice.core.copilot.frame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FramePromptsTest {

  @Test
  fun `plate prompt follows the base plate template`() {
    val prompt = FramePrompts.platePrompt(framePlan())

    assertTrue(prompt.startsWith("Comic-book panel. Full-bleed illustration, no border. Empty residential street"))
    assertTrue(prompt.contains("The attached images are style references only"))
    assertTrue(prompt.endsWith("No people or characters in the image. No text, lettering or signs with writing anywhere."))
    assertTrue(prompt.contains("Leave an empty stretch of sidewalk beside the tree."))
  }

  @Test
  fun `reference character prompt uses the reference images, style lock and keep clause`() {
    val plan = framePlan(plannedCharacter())

    val prompt = FramePrompts.characterPrompt(plan, plan.characters.single(), hasLook = true, lookNote = "A man in a leather jacket")

    assertTrue(prompt.startsWith("Add the man shown in the reference images, standing beside the bare tree."))
    assertTrue(prompt.contains("Drawn exactly as in the reference images: his exact face, hair and build."))
    assertTrue(prompt.contains("This outfit and look: A man in a leather jacket."))
    assertTrue(prompt.contains("filling about three quarters of the frame height from head to feet"))
    assertTrue(prompt.contains("the same cold blue night palette and sodium-orange lamp glow. It must look like a hand-drawn comic panel"))
    assertTrue(prompt.contains("Low camera angle."))
    assertTrue(prompt.endsWith("Keep everything else exactly the same."))
  }

  @Test
  fun `text-only character is described, not referenced`() {
    val plan = framePlan(plannedCharacter())

    val prompt = FramePrompts.characterPrompt(plan, plan.characters.single(), hasLook = false, lookNote = null)

    assertTrue(prompt.startsWith("Add a tall man in a worn leather jacket, standing beside the bare tree."))
    assertFalse(prompt.contains("reference images"))
  }

  @Test
  fun `establishing frames keep the figure in the middle ground`() {
    val plan = framePlan(plannedCharacter(prominence = 40), establishing = true)

    val prompt = FramePrompts.characterPrompt(plan, plan.characters.single(), hasLook = true, lookNote = null)

    assertTrue(prompt.contains("He stands in the middle ground, filling about two fifths of the frame height"))
  }

  @Test
  fun `small subjects state their scale and use no frame fraction`() {
    val cat = plannedCharacter(
      noun = "cat",
      pron = "she",
      framing = "small",
      prominence = null,
      scale = "She is a normal house cat at realistic scale, about a quarter of the man's height.",
    )
    val plan = framePlan(cat)

    val prompt = FramePrompts.characterPrompt(plan, cat, hasLook = true, lookNote = null)

    assertTrue(prompt.contains("She is a normal house cat at realistic scale, about a quarter of the man's height. Her whole body is visible"))
    assertTrue(prompt.contains("her exact face, hair and build"))
    assertFalse(prompt.contains("of the frame height"))
  }

  @Test
  fun `style lock tolerates a trailing period in the palette`() {
    assertEquals(
      FramePrompts.styleLock("a warm dusk palette"),
      FramePrompts.styleLock("a warm dusk palette."),
    )
  }
}
