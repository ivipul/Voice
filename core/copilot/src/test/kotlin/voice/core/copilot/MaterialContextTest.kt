package voice.core.copilot

import voice.core.companion.BookMaterials
import voice.core.companion.MaterialSection
import voice.core.companion.SceneCue
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialContextTest {

  private val materials = BookMaterials(
    sections = listOf(
      MaterialSection(id = "pdf1", fileId = "f", title = "Good Energy Plan", extract = "Week 1: walk.", keyTerms = listOf("glucose monitor")),
      MaterialSection(id = "pdf2", fileId = "f", title = "Recipes", extract = "Chia pudding."),
      MaterialSection(id = "pdf3", fileId = "f", title = "Blank", extract = ""),
    ),
    cues = listOf(SceneCue("pdf2", startMs = 1_000, endMs = 5_000), SceneCue("pdf3", startMs = 1_000, endMs = 5_000)),
  )

  @Test
  fun sectionsTheNarrationIsOnComeFirstThenOnesTheQuestionNames() {
    val sections = relevantSections(materials, positionMs = 2_000, question = "Should I wear a glucose monitor?")

    assertEquals(listOf("pdf2", "pdf1"), sections.map { it.id })
  }

  @Test
  fun nothingMatchesAnUnrelatedQuestionAwayFromAReference() {
    assertEquals(emptyList(), relevantSections(materials, positionMs = 9_000, question = "Who is the author?"))
  }
}
