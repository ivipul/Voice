package voice.core.copilot

import voice.core.data.BookId

/**
 * The Smart Router pipeline (Jev -> transcript -> Gemini) behind every co-pilot action,
 * shared by the Bluetooth-triggered modes (core:playback's CoPilotEngine) and the Feed's
 * typed/spoken interactions (features:playbackScreen's BookPlayViewModel) so both call
 * sites get identical routing/prompting behavior.
 */
interface CoPilotPipeline {

  /** Open Mic Co-Pilot / Feed Ask: answers a listener's question, spoken-length by default. */
  suspend fun ask(bookId: BookId, question: String): String

  /** Auto-Identify: explains what's happening in the last 60s, using up to 30m as context. */
  suspend fun autoIdentify(bookId: BookId): String

  /** Catch-Me-Up: recaps the last 30 minutes, opening with "In the last 30 minutes,". */
  suspend fun catchMeUp(bookId: BookId): String

  /** What to say when a co-pilot round trip fails outright, worded in the current voice style. */
  suspend fun failureMessage(): String

  /** Snip & Synthesize: silently extracts a 3-bullet summary of the last 3 minutes to the Feed. */
  suspend fun snip(bookId: BookId)
}
