package voice.features.playbackScreen.copilot

import androidx.compose.runtime.Immutable

@Immutable
data class CoPilotMessage(
  val id: String,
  val role: Role,
  val text: String,
  val timestampMs: Long,
) {
  enum class Role {
    User,
    CoPilot,
  }
}
