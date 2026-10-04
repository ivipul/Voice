package voice.core.copilot

/** Progress of one Snip & Synthesize capture, for whichever screen is currently on display. */
sealed interface SnipEvent {
  /** [drawsFrame] says whether a comic frame will be drawn, so the screen can reserve room for it. */
  data class Started(val drawsFrame: Boolean) : SnipEvent

  /** [messageId] is the Feed message holding the text, and later the frame image. */
  data class Ready(val text: String, val messageId: String) : SnipEvent
  data object Failed : SnipEvent
}
