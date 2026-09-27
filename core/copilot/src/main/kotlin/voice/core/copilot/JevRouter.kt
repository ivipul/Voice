package voice.core.copilot

/**
 * System 1 of the Smart Router pipeline (Jev AI / TypeSafe AI's `Noul` primitive per the PRD):
 * a fast check for whether a question needs local transcript context injected before the
 * System 2 Gemini call, or can be answered from Gemini's general knowledge alone.
 */
interface JevRouter {
  suspend fun needsTranscriptContext(question: String): Boolean
}
