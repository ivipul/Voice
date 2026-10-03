package voice.features.playbackScreen

import voice.core.copilot.CoPilotMessage
import voice.features.playbackScreen.view.FRAME_ASPECT

internal sealed interface SnipSheetViewState {
  data class Loading(val drawsFrame: Boolean) : SnipSheetViewState
  data class Ready(val text: String, val messageId: String) : SnipSheetViewState
}

/** The comic frame slot of the snip sheet: no slot, a holographic placeholder, or the finished image. */
internal sealed interface SnipFrameViewState {
  data object None : SnipFrameViewState
  data object Drawing : SnipFrameViewState
  data class Image(val path: String) : SnipFrameViewState
}

internal fun SnipSheetViewState.frameState(messages: List<CoPilotMessage>): SnipFrameViewState = when (this) {
  is SnipSheetViewState.Loading -> if (drawsFrame) SnipFrameViewState.Drawing else SnipFrameViewState.None
  is SnipSheetViewState.Ready -> {
    val message = messages.firstOrNull { it.id == messageId }
    val path = message?.imagePath
    when {
      path != null -> SnipFrameViewState.Image(path)
      message?.isGeneratingImage == true -> SnipFrameViewState.Drawing
      else -> SnipFrameViewState.None
    }
  }
}

/**
 * The height of the frame in the snip sheet. The sheet holds up to two thirds of the screen; the frame leaves room
 * for the title and about five lines of snip text, so the text is visible without scrolling, and never exceeds
 * 40% of the screen.
 */
internal fun snipFrameHeightDp(screenHeightDp: Int): Int {
  val roomLeft = screenHeightDp * 2 / 3 - SNIP_SHEET_CHROME_DP
  return minOf(roomLeft, screenHeightDp * 2 / 5).coerceAtLeast(MIN_SNIP_FRAME_DP)
}

/** The frame is about 62% of the sheet's width, unless that would push the text below the fold. */
internal fun snipFrameWidthDp(availableWidthDp: Float, screenHeightDp: Int): Float =
  minOf(availableWidthDp * SNIP_FRAME_WIDTH_FRACTION, snipFrameHeightDp(screenHeightDp) * FRAME_ASPECT)

private const val SNIP_FRAME_WIDTH_FRACTION = 0.62f
private const val SNIP_SHEET_CHROME_DP = 260
private const val MIN_SNIP_FRAME_DP = 160
