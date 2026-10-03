package voice.core.copilot

/** Progress of one Snip & Synthesize capture, for whichever screen is currently on display. */
sealed interface SnipEvent {
  data object Started : SnipEvent
  data class Ready(val text: String) : SnipEvent
  data object Failed : SnipEvent
}
