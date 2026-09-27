package voice.core.copilot

import voice.core.data.ChapterId

data class CoPilotMessage(
  val id: String,
  val role: Role,
  val text: String,
  val timestampMs: Long,
  val isVisualPriority: Boolean = false,
  val imagePath: String? = null,
  // Where in the audiobook this moment was captured (Snip & Synthesize only), so the Feed
  // can link straight back to it.
  val snipChapterId: ChapterId? = null,
  val snipPositionInChapterMs: Long? = null,
) {
  enum class Role {
    User,
    CoPilot,
  }
}
