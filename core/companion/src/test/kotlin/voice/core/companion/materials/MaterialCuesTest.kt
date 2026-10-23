package voice.core.companion.materials

import voice.core.companion.SceneCue
import voice.core.companion.scene.BookLine
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialCuesTest {

  private val lines = (0 until 10).map { BookLine(startMs = it * 60_000L, endMs = it * 60_000L + 59_000, text = "line $it") }

  @Test
  fun referencesBecomeChipsFromTheirLineAndCloseOnesMerge() {
    val cues = materialCues(
      references = listOf(LinkReference("pdf1", 1), LinkReference("pdf1", 3), LinkReference("pdf2", 9), LinkReference("pdf1", 9)),
      lines = lines,
      sectionIds = setOf("pdf1", "pdf2"),
    )

    assertEquals(
      listOf(
        SceneCue("pdf1", 60_000, 180_000 + MATERIAL_CHIP_MS),
        SceneCue("pdf1", 540_000, 540_000 + MATERIAL_CHIP_MS),
        SceneCue("pdf2", 540_000, 540_000 + MATERIAL_CHIP_MS),
      ),
      cues,
    )
  }

  @Test
  fun unknownSectionsAndLinesAreDropped() {
    assertEquals(emptyList(), materialCues(listOf(LinkReference("nope", 1), LinkReference("pdf1", 99)), lines, setOf("pdf1")))
  }
}
