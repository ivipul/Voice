package voice.features.playbackScreen

import androidx.compose.runtime.Immutable

/** The System AI chat's input bar: what is in the text box, and whether the mic or the send countdown is running. */
@Immutable
internal data class ChatInputViewState(
  val text: String = "",
  /** The microphone is on and what it hears is written into [text]. */
  val isListening: Boolean = false,
  /** Dictation just ended; [text] is sent after [AUTO_SEND_DELAY_MS] unless the user steps in. */
  val autoSendPending: Boolean = false,
) {

  companion object {
    const val AUTO_SEND_DELAY_MS = 5_000L
  }
}
