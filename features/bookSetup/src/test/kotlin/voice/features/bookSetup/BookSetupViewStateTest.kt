package voice.features.bookSetup

import voice.core.companion.ExtensionPoint
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaStatus
import voice.core.companion.StepState
import voice.core.companion.TranscriptionState
import kotlin.test.Test
import kotlin.test.assertEquals

class BookSetupViewStateTest {

  @Test
  fun cardsInPlayComeFirstAndSkippedOnesAreSetAside() {
    val cards = listOf(
      card("built", IdeaStatus.Built),
      card("suggested", IdeaStatus.Suggested),
      card("skipped", IdeaStatus.Skipped),
      card("failed", IdeaStatus.Failed),
      card("building", IdeaStatus.Building),
    )

    assertEquals(listOf("building", "failed", "suggested", "built"), cards.activeCards().map { it.id })
    assertEquals(listOf("skipped"), cards.skippedCards().map { it.id })
  }

  @Test
  fun theDeckDealsTheListenersOwnIdeasFirstThenTheRestInOrder() {
    val cards = listOf(
      card("first", IdeaStatus.Built),
      card("second", IdeaStatus.Suggested),
      card("third", IdeaStatus.Skipped),
      card("fourth", IdeaStatus.Suggested),
    )

    val deck = cards.ideaDeck()
    assertEquals("second", deck.current?.id)
    assertEquals(2, deck.position)
    assertEquals(4, deck.total)

    val withOwn = (cards + card("own", IdeaStatus.Suggested).copy(fromListener = true)).ideaDeck()
    assertEquals("own", withOwn.current?.id)
  }

  @Test
  fun theDeckIsEmptyOnceEveryCardIsDecided() {
    val deck = listOf(card("a", IdeaStatus.Built), card("b", IdeaStatus.Skipped)).ideaDeck()

    assertEquals(null, deck.current)
    assertEquals(2, deck.position)
  }

  @Test
  fun transcriptionCostIsEstimatedFromTheWholeBook() {
    val state = TranscriptionState(state = StepState.Running, partsDone = 3, partsTotal = 12)
      .toViewState(durationMs = 10 * 60 * 60_000L + 30_000L)

    assertEquals(601, state.durationMinutes)
    assertEquals(601 * DEEPGRAM_USD_PER_MINUTE, state.estimatedCostUsd, 0.0001)
    assertEquals(0.25F, state.fraction)
  }

  @Test
  fun durationsReadAsMinutesOrHours() {
    assertEquals("45 min", formatHours(45))
    assertEquals("10.0 h", formatHours(600))
    assertEquals("$2.58", formatUsd(2.5843))
  }

  private fun card(
    id: String,
    status: IdeaStatus,
  ) = IdeaCard(id = id, title = id, description = id, point = ExtensionPoint.Shelf, status = status)
}
