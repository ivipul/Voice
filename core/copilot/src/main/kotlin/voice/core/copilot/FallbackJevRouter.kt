package voice.core.copilot

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Jev/OpenRouter integration is not validated yet (network access to openrouter.ai is
 * blocked in some build environments, and no confirmed working key exists at the time of
 * writing). Until a real [JevRouter] is wired in, always inject transcript context - it
 * only ever adds context, never removes something Gemini might need, so it's the safe
 * default while the router itself is unproven.
 */
@ContributesBinding(AppScope::class)
@Inject
class FallbackJevRouter : JevRouter {
  override suspend fun needsTranscriptContext(question: String): Boolean = true
}
