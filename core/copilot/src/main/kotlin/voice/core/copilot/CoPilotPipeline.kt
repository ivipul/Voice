package voice.core.copilot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import voice.core.data.BookId

/**
 * The transcript -> Claude pipeline behind every co-pilot action, shared by the headset-triggered modes
 * (core:playback's CoPilotEngine) and the chat's typed/spoken questions (features:playbackScreen's
 * BookPlayViewModel) so both get identical prompting.
 */
interface CoPilotPipeline {

  /** Open Mic Co-Pilot / Feed Ask: answers a listener's question, spoken-length by default. */
  suspend fun ask(bookId: BookId, question: String): String

  /** Auto-Identify: explains what's happening in the last 60s, using up to 30m as context. */
  suspend fun autoIdentify(bookId: BookId): String

  /** Catch-Me-Up: recaps the last 30 minutes, opening with "In the last 30 minutes,". */
  suspend fun catchMeUp(bookId: BookId): String

  /** What to say when a co-pilot round trip fails outright, worded in the current voice. */
  suspend fun failureMessage(): String

  /** Snip: silently summarizes what is being discussed around the current moment into the chat and Notebook. */
  suspend fun snip(bookId: BookId)

  /** Loads the current book's transcript into memory ahead of the first co-pilot action. */
  suspend fun warmUp(bookId: BookId) = Unit

  /**
   * Streaming variants of [ask], [autoIdentify] and [catchMeUp] for the spoken flows: the same
   * answer as text deltas, so speech can start before the whole answer exists. They throw on
   * failure, and the caller then falls back to the non-streaming call.
   */
  fun askStream(bookId: BookId, question: String): Flow<String> = flow { emit(ask(bookId, question)) }

  fun autoIdentifyStream(bookId: BookId): Flow<String> = flow { emit(autoIdentify(bookId)) }

  fun catchMeUpStream(bookId: BookId): Flow<String> = flow { emit(catchMeUp(bookId)) }
}
