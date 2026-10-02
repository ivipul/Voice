package voice.core.copilot.frame

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskPngTest {

  @Test
  fun `mask is frame sized, black inside the box and white everywhere else`() {
    val png = MaskPng.box(FRAME_WIDTH, FRAME_HEIGHT, MaskBox(0.25, 0.5, 0.75, 0.9))

    val image = ImageIO.read(ByteArrayInputStream(png))
    assertEquals(FRAME_WIDTH, image.width)
    assertEquals(FRAME_HEIGHT, image.height)
    fun gray(x: Int, y: Int) = image.raster.getSample(x, y, 0)
    assertEquals(0, gray(FRAME_WIDTH / 2, (FRAME_HEIGHT * 0.7).toInt()))
    assertEquals(255, gray(FRAME_WIDTH / 2, (FRAME_HEIGHT * 0.3).toInt()))
    assertEquals(255, gray(10, (FRAME_HEIGHT * 0.7).toInt()))
    assertEquals(255, gray(FRAME_WIDTH - 10, (FRAME_HEIGHT * 0.7).toInt()))
    assertEquals(255, gray(FRAME_WIDTH / 2, FRAME_HEIGHT - 10))
  }

  @Test
  fun `box edges land on the rounded pixel boundaries`() {
    val png = MaskPng.box(100, 200, MaskBox(0.1, 0.2, 0.5, 0.6))

    val image = ImageIO.read(ByteArrayInputStream(png))
    fun gray(x: Int, y: Int) = image.raster.getSample(x, y, 0)
    assertEquals(255, gray(9, 100))
    assertEquals(0, gray(10, 100))
    assertEquals(0, gray(49, 100))
    assertEquals(255, gray(50, 100))
    assertEquals(255, gray(30, 39))
    assertEquals(0, gray(30, 40))
    assertEquals(0, gray(30, 119))
    assertEquals(255, gray(30, 120))
  }
}
