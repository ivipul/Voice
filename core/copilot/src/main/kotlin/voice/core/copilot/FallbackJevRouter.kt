package voice.core.copilot

/**
 * Was the bound [JevRouter] before Jev/OpenRouter was validated against the live Decisions
 * endpoint; [RealJevRouter] is now bound instead. Kept unbound (no [dev.zacsweers.metro.ContributesBinding])
 * for a quick manual rollback, or as a safe stand-in in tests: it always injects transcript
 * context, which only ever adds context Gemini might need, never removes it.
 */
class FallbackJevRouter : JevRouter {
  override suspend fun needsTranscriptContext(question: String): Boolean = true
}
