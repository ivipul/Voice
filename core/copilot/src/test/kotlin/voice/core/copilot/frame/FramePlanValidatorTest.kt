package voice.core.copilot.frame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FramePlanValidatorTest {

  private val validator = FramePlanValidator(NameGuard(listOf("Carl", "Princess Donut")))

  @Test
  fun `a known name anywhere in the plan is rejected`() {
    val plan = framePlan(plannedCharacter().copy(pose = "Carl reaches up toward the branches"))

    val error = assertFailsWith<InvalidFramePlanException> { validator.validate(plan) }

    assertTrue(error.message.orEmpty().contains("Carl"))
  }

  @Test
  fun `a name token is rejected but the title word is not`() {
    assertFailsWith<InvalidFramePlanException> {
      validator.validate(framePlan(plannedCharacter().copy(placement = "beside Donut")))
    }

    val plan = validator.validate(framePlan(plannedCharacter().copy(placement = "beside a princess statue")))

    assertEquals("beside a princess statue", plan.characters.single().placement)
  }

  @Test
  fun `the character field may carry the name because it never reaches a prompt`() {
    val plan = validator.validate(framePlan(plannedCharacter(character = "Carl")))

    assertEquals("Carl", plan.characters.single().character)
  }

  @Test
  fun `prominence is raised to the floor and the mask grows to match it`() {
    val plan = validator.validate(framePlan(plannedCharacter(prominence = 25, box = listOf(0.4, 0.4, 0.5, 0.5))))

    val character = plan.characters.single()
    assertEquals(55, character.prominencePct)
    val box = character.maskBox
    assertTrue(box[3] - box[1] >= 0.55 - 1e-9, "mask height ${box[3] - box[1]}")
    assertTrue(box.all { it in 0.0..1.0 })
  }

  @Test
  fun `establishing frames may go down to 40 percent`() {
    val plan = validator.validate(framePlan(plannedCharacter(prominence = 40), establishing = true))

    assertEquals(40, plan.characters.single().prominencePct)
  }

  @Test
  fun `small characters have no prominence and keep their small box`() {
    val cat = plannedCharacter(
      noun = "cat",
      pron = "it",
      framing = "small",
      prominence = 70,
      box = listOf(0.52, 0.28, 0.70, 0.37),
      scale = "a normal house cat at realistic scale",
    )

    val character = validator.validate(framePlan(cat)).characters.single()

    assertEquals(null, character.prominencePct)
    assertEquals(0.28, character.maskBox[1], 1e-9)
    assertEquals(0.37, character.maskBox[3], 1e-9)
  }

  @Test
  fun `no more than three characters survive`() {
    val plan = validator.validate(framePlan(*Array(5) { plannedCharacter() }))

    assertEquals(3, plan.characters.size)
  }

  @Test
  fun `structural problems are rejected with a reason`() {
    assertFailsWith<InvalidFramePlanException> { validator.validate(framePlan(plannedCharacter(framing = "pyramid"))) }
    assertFailsWith<InvalidFramePlanException> { validator.validate(framePlan(plannedCharacter(box = listOf(0.1, 0.2)))) }
    assertFailsWith<InvalidFramePlanException> {
      validator.validate(framePlan(plannedCharacter(framing = "small", prominence = null, scale = null)))
    }
    assertFailsWith<InvalidFramePlanException> { validator.validate(framePlan().copy(location = " ")) }
  }

  @Test
  fun `blocked clauses are stripped before they can reach a prompt`() {
    val plan = validator.validate(
      framePlan(plannedCharacter().copy(description = "a furred cat, with eight nipples, and orange eyes")),
    )

    assertEquals("a furred cat, and orange eyes", plan.characters.single().description)
  }

  @Test
  fun `a mask box given back to front is put in order`() {
    val character = validator.validate(framePlan(plannedCharacter(box = listOf(0.6, 0.95, 0.1, 0.2)))).characters.single()

    assertTrue(character.maskBox[0] < character.maskBox[2])
    assertTrue(character.maskBox[1] < character.maskBox[3])
  }
}
