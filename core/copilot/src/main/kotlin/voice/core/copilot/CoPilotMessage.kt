package voice.core.copilot

data class CoPilotMessage(
  val id: String,
  val role: Role,
  val text: String,
  val timestampMs: Long,
  val isVisualPriority: Boolean = false,
) {
  enum class Role {
    User,
    CoPilot,
  }
}
