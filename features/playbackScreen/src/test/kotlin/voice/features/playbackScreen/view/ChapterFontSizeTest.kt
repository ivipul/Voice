package voice.features.playbackScreen.view

import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterFontSizeTest {

  @Test
  fun aShortNameKeepsTheFullSize() {
    assertEquals(44.sp, chapterFontSize("Chapter 6", 44.sp))
  }

  @Test
  fun aLongNameIsSetMuchSmaller() {
    assertEquals(24.sp, chapterFontSize("5. Getting Wealthy vs. Staying Wealthy", 40.sp))
  }

  @Test
  fun noNameGoesBelowTheSmallestSize() {
    assertEquals(22.sp, chapterFontSize("A very long chapter name that keeps on going", 30.sp))
  }
}
