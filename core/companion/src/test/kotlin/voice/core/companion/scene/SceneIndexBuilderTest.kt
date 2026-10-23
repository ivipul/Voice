package voice.core.companion.scene

import voice.core.companion.BookKind
import voice.core.companion.activeAt
import voice.core.companion.knownAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneIndexBuilderTest {

  /** One line every 10 seconds, each 8 seconds long. */
  private val lines = List(600) { BookLine(startMs = it * 10_000L, endMs = it * 10_000L + 8_000L, text = "Line $it") }

  @Test
  fun windowsCoverEveryLineOnce() {
    val windows = windowsOf(lines, windowMs = 45 * 60 * 1000L)

    assertEquals(lines.size, windows.sumOf { it.lines.size })
    assertEquals(listOf(0, 270, 540), windows.map { it.firstIndex })
  }

  @Test
  fun lineSpansBecomeTimesWithATail() {
    val window = windowsOf(lines).first()
    val builder = SceneIndexBuilder(BookKind.NonFiction)
    builder.add(window, WindowAnswer(listOf(entry("Compound interest", spans = listOf(LineSpan(10, 20))))))

    val index = builder.build()

    assertEquals(listOf(100_000L to 208_000L + 15_000L), index.cues.map { it.startMs to it.endMs })
    assertEquals("compound-interest", index.entries.single().id)
  }

  @Test
  fun theSameEntryIsMatchedByAliasAcrossWindows() {
    val windows = windowsOf(lines)
    val builder = SceneIndexBuilder(BookKind.Fiction)
    builder.add(windows[0], WindowAnswer(listOf(entry("Elizabeth Bennet", aliases = listOf("Lizzy"), spans = listOf(LineSpan(0, 30)), note = "Sharp-witted."))))
    builder.add(windows[1], WindowAnswer(listOf(entry("Lizzy", spans = listOf(LineSpan(300, 330)), note = "Dislikes Darcy."))))

    val index = builder.build()

    val entry = index.entries.single()
    assertEquals("Elizabeth Bennet", entry.name)
    assertEquals(2, index.cues.size)
    assertEquals(listOf("Sharp-witted."), entry.knownAt(positionMs = 1_000_000L))
    assertEquals(listOf("Sharp-witted.", "Dislikes Darcy."), entry.knownAt(positionMs = 3_000_000L))
  }

  @Test
  fun passingMentionsShorterThanAMinuteAreDropped() {
    val builder = SceneIndexBuilder(BookKind.Fiction)
    builder.add(windowsOf(lines).first(), WindowAnswer(listOf(entry("Waiter", spans = listOf(LineSpan(5, 5))))))

    assertTrue(builder.build().entries.isEmpty())
  }

  @Test
  fun closeSpansMergeSoChipsDontFlicker() {
    assertEquals(listOf(0L..200L, 1_000L..1_100L), mergeSpans(listOf(150L..200L, 0L..100L, 1_000L..1_100L), gapMs = 60L))
  }

  @Test
  fun activeEntriesFollowThePosition() {
    val builder = SceneIndexBuilder(BookKind.NonFiction)
    builder.add(windowsOf(lines).first(), WindowAnswer(listOf(entry("Index funds", spans = listOf(LineSpan(10, 20))))))
    val index = builder.build()

    assertEquals(listOf("Index funds"), index.activeAt(150_000L).map { it.name })
    assertTrue(index.activeAt(50_000L).isEmpty())
  }

  @Test
  fun namesAreComparedWithoutArticlesOrPunctuation() {
    assertEquals("red queen", normalized("The Red Queen!"))
  }

  private fun entry(
    name: String,
    aliases: List<String> = emptyList(),
    spans: List<LineSpan>,
    note: String = "",
  ) = WindowEntry(name = name, aliases = aliases, summary = "About $name", note = note, spans = spans)
}
