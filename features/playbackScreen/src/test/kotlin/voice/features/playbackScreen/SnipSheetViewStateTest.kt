package voice.features.playbackScreen

import voice.core.copilot.CoPilotMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class SnipSheetViewStateTest {

  private fun message(id: String, imagePath: String? = null, generating: Boolean = false) = CoPilotMessage(
    id = id,
    role = CoPilotMessage.Role.CoPilot,
    text = "snip",
    timestampMs = 0L,
    imagePath = imagePath,
    isGeneratingImage = generating,
  )

  @Test
  fun `while capturing, a frame placeholder shows only when a frame will be drawn`() {
    assertEquals(SnipFrameViewState.Drawing, SnipSheetViewState.Loading(drawsFrame = true).frameState(emptyList()))
    assertEquals(SnipFrameViewState.None, SnipSheetViewState.Loading(drawsFrame = false).frameState(emptyList()))
  }

  @Test
  fun `once saved, the placeholder holds until the frame image arrives`() {
    val sheet = SnipSheetViewState.Ready("text", "m1")

    assertEquals(SnipFrameViewState.Drawing, sheet.frameState(listOf(message("m1", generating = true))))
    assertEquals(
      SnipFrameViewState.Image("/frames/m1.png"),
      sheet.frameState(listOf(message("m1", imagePath = "/frames/m1.png"))),
    )
  }

  @Test
  fun `no slot when drawing failed or the message is missing`() {
    val sheet = SnipSheetViewState.Ready("text", "m1")

    assertEquals(SnipFrameViewState.None, sheet.frameState(listOf(message("m1"))))
    assertEquals(SnipFrameViewState.None, sheet.frameState(emptyList()))
  }

  @Test
  fun `only the sheet's own message is considered`() {
    val sheet = SnipSheetViewState.Ready("text", "m1")

    assertEquals(SnipFrameViewState.None, sheet.frameState(listOf(message("other", imagePath = "/frames/other.png"))))
  }

  @Test
  fun `the frame leaves room for the text above the fold`() {
    // 900 dp screen: the sheet holds 600 dp, the title and text take about 260, so the frame gets 340.
    assertEquals(340, snipFrameHeightDp(900))
    // A tall screen is capped at 40%.
    assertEquals(480, snipFrameHeightDp(1200))
    // A short screen still gets a usable frame.
    assertEquals(160, snipFrameHeightDp(500))
  }
}
