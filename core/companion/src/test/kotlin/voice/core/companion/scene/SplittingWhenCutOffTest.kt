package voice.core.companion.scene

import kotlinx.coroutines.test.runTest
import voice.core.ai.ReplyCutOffException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SplittingWhenCutOffTest {

  private val window = LineWindow(
    firstIndex = 270,
    lines = List(200) { BookLine(startMs = it * 10_000L, endMs = it * 10_000L + 8_000L, text = "Line $it") },
  )

  @Test
  fun aCutOffReplyIsRetriedOnEachHalf() = runTest {
    val scanned = mutableListOf<Pair<Int, Int>>()

    splittingWhenCutOff(window) { part ->
      if (part.lines.size > 60) throw ReplyCutOffException()
      scanned += part.firstIndex to part.lines.size
    }

    assertEquals(listOf(270 to 50, 320 to 50, 370 to 50, 420 to 50), scanned)
  }

  @Test
  fun aSmallWindowThatStillGetsCutOffFails() = runTest {
    assertFailsWith<ReplyCutOffException> {
      splittingWhenCutOff(window) { throw ReplyCutOffException() }
    }
  }
}
