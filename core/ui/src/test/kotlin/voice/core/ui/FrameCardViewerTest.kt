package voice.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameCardViewerTest {

  @Test
  fun `on a tall phone the card is as wide as 90 percent of the screen`() {
    val (width, height) = fitCard(screenWidth = 1080f, screenHeight = 2400f)

    assertEquals(972f, width, 0.01f)
    assertEquals(972f / FRAME_ASPECT, height, 0.01f)
    assertTrue(height <= 2400f * 0.9f)
  }

  @Test
  fun `on a short screen the card is as tall as 90 percent of the screen`() {
    val (width, height) = fitCard(screenWidth = 1080f, screenHeight = 1400f)

    assertEquals(1260f, height, 0.01f)
    assertEquals(1260f * FRAME_ASPECT, width, 0.01f)
    assertTrue(width <= 1080f * 0.9f)
  }

  @Test
  fun `the card always keeps the frame's 9 by 16 shape`() {
    val (width, height) = fitCard(screenWidth = 800f, screenHeight = 1600f)

    assertEquals(FRAME_ASPECT, width / height, 0.001f)
  }
}
