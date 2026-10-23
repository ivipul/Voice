package voice.features.bookSetup

import androidx.compose.runtime.Immutable
import voice.core.companion.BookProfile
import voice.core.companion.FeasibilityVerdict
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaStatus
import voice.core.companion.LookSource
import voice.core.companion.StepState
import voice.core.companion.TranscriptionState
import voice.core.ui.crawl.BookStyle
import kotlin.math.ceil

@Immutable
data class BookSetupViewState(
  val title: String,
  val author: String?,
  val cover: String?,
  val bookStyle: BookStyle,
  /** Keys the setup can't start without: Claude always, Deepgram when the book has no transcript yet. */
  val missingKeys: List<MissingKey>,
  val canDrawCovers: Boolean,
  /** Nothing has run yet, so the screen offers a Start button instead of progress. */
  val notStarted: Boolean,
  val transcription: TranscriptionViewState,
  val research: StepViewState,
  val profile: BookProfile?,
  val ideas: StepViewState,
  val cards: List<IdeaCard>,
  val deck: IdeaDeckViewState,
  val skippedCards: List<IdeaCard>,
  val ownIdea: OwnIdeaViewState,
  val look: LookViewState,
)

enum class MissingKey {
  Claude,
  Deepgram,
}

@Immutable
data class TranscriptionViewState(
  val state: StepState,
  val fraction: Float,
  val partsDone: Int,
  val partsTotal: Int,
  val error: String?,
  val durationMinutes: Int,
  /** What Deepgram will bill for the whole book, in US dollars. */
  val estimatedCostUsd: Double,
)

/** Claude's idea cards dealt one at a time, each waiting for Build or Skip. */
@Immutable
data class IdeaDeckViewState(
  val current: IdeaCard?,
  /** Where [current] sits in the deck, counted from 0; cards before it are decided. */
  val position: Int,
  val total: Int,
)

@Immutable
data class StepViewState(
  val state: StepState,
  val error: String?,
)

@Immutable
data class OwnIdeaViewState(
  val text: String = "",
  val checking: Boolean = false,
  val verdict: FeasibilityVerdict? = null,
  val error: String? = null,
)

@Immutable
data class LookViewState(
  val source: LookSource,
  val extraPrompt: String,
  val displayFont: String,
  val working: Boolean = false,
  val error: String? = null,
  val hasCover: Boolean,
)

/** Deepgram Nova-3 pre-recorded audio, pay as you go. */
internal const val DEEPGRAM_USD_PER_MINUTE = 0.0043

internal fun TranscriptionState.toViewState(durationMs: Long): TranscriptionViewState {
  val minutes = ceil(durationMs / 60_000.0).toInt().coerceAtLeast(0)
  return TranscriptionViewState(
    state = state,
    fraction = fraction,
    partsDone = partsDone,
    partsTotal = partsTotal,
    error = error,
    durationMinutes = minutes,
    estimatedCostUsd = minutes * DEEPGRAM_USD_PER_MINUTE,
  )
}

/** Cards still in play first: being built, then failed, then waiting for a choice, then the built ones. */
internal fun List<IdeaCard>.activeCards(): List<IdeaCard> = filter { it.status != IdeaStatus.Skipped }
  .sortedBy {
    when (it.status) {
      IdeaStatus.Building -> 0
      IdeaStatus.Failed -> 1
      IdeaStatus.Suggested -> 2
      IdeaStatus.Built -> 3
      IdeaStatus.Skipped -> 4
    }
  }

internal fun List<IdeaCard>.skippedCards(): List<IdeaCard> = filter { it.status == IdeaStatus.Skipped }

/** The listener's own ideas come first, then Claude's in the order they were suggested. */
internal fun List<IdeaCard>.ideaDeck(): IdeaDeckViewState {
  val ordered = sortedByDescending { it.fromListener }
  val decided = ordered.filter { it.status != IdeaStatus.Suggested }
  val waiting = ordered.filter { it.status == IdeaStatus.Suggested }
  return IdeaDeckViewState(current = waiting.firstOrNull(), position = decided.size, total = ordered.size)
}
