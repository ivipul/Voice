package voice.features.playbackScreen

import voice.core.copilot.CoPilotMessage

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
