package voice.features.playbackScreen.copilot

import voice.core.data.BookId

/**
 * The ask -> answer step of the co-pilot loop, behind an interface so the real
 * transcript-aware Gemini call (Phase 4) can be swapped in without touching call sites.
 */
interface CoPilotPipeline {
  suspend fun ask(bookId: BookId, question: String): String
}
