package voice.features.playbackScreen.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.delay
import voice.core.data.BookId

/**
 * Placeholder pipeline until Phase 4 wires in the real transcript + Gemini call.
 * Confirms the question was heard so the Feed UI and Ask flow can be exercised end-to-end.
 */
@ContributesBinding(AppScope::class)
class StubCoPilotPipeline : CoPilotPipeline {
  override suspend fun ask(bookId: BookId, question: String): String {
    delay(400)
    return "Got it — you asked: \"$question\". The real answer pipeline (transcript + Gemini) isn't wired up yet."
  }
}
