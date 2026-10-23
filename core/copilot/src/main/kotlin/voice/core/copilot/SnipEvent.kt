package voice.core.copilot

/** Progress of one Snip & Synthesize capture, for whichever screen is currently on display. */
sealed interface SnipEvent {
  /** [drawsPicture] says whether a snip picture will be drawn, so the screen can reserve room for it. */
  data class Started(val drawsPicture: Boolean) : SnipEvent

  /** [messageId] is the chat message holding the text, and later the picture. */
  data class Ready(val text: String, val messageId: String) : SnipEvent
  data object Failed : SnipEvent
}
