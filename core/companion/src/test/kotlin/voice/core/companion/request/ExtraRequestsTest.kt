package voice.core.companion.request

import voice.core.companion.ExtensionPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExtraRequestsTest {

  @Test
  fun aNewFeasibleRequestIsBuiltRightAway() {
    val outcome = outcomeOf(plan(), brief = "brief", positionMs = 42_000L, existing = emptyList())

    val card = assertIs<RequestOutcome.Build>(outcome).card
    assertEquals("Places so far", card.title)
    assertEquals(ExtensionPoint.Shelf, card.point)
    assertEquals("map", card.kind)
    assertEquals("brief", card.why)
    assertEquals(42_000L, card.requestedAtMs)
    assertTrue(card.fromListener)
  }

  @Test
  fun aRequestThatOverlapsAnExistingExtraAsksFirst() {
    val outcome = outcomeOf(
      plan(existingId = "e1", existingSummary = "Shows every place visited so far."),
      brief = "brief",
      positionMs = 0L,
      existing = listOf(ExistingExtra("e1", "Journey map", "A map of the journey.")),
    )

    val confirm = assertIs<RequestOutcome.Confirm>(outcome)
    assertEquals(
      "There's already “Journey map”, which shows every place visited so far. Do you still want me to make a new one?",
      confirm.question,
    )
    assertEquals("Places so far", confirm.card.title)
  }

  @Test
  fun anUnknownExistingIdIsTreatedAsNew() {
    val outcome = outcomeOf(plan(existingId = "missing"), brief = "", positionMs = 0L, existing = emptyList())

    assertIs<RequestOutcome.Build>(outcome)
  }

  @Test
  fun anInfeasibleRequestOffersTheClosestAlternative() {
    val outcome = outcomeOf(
      plan(
        feasible = false,
        point = null,
        reason = "I can't play sound effects",
        alternativePoint = "shelf",
        alternativeTitle = "Battle timeline",
        alternativeDescription = "A timeline of the fight so far.",
      ),
      brief = "",
      positionMs = 0L,
      existing = emptyList(),
    )

    val confirm = assertIs<RequestOutcome.Confirm>(outcome)
    assertEquals(
      "I can't build that exactly: I can't play sound effects. The closest I can make is “Battle timeline”, " +
        "a timeline of the fight so far. Want me to build that?",
      confirm.question,
    )
    assertEquals(ExtensionPoint.Shelf, confirm.card.point)
  }

  @Test
  fun anInfeasibleRequestWithNothingCloseIsRefused() {
    val outcome = outcomeOf(
      plan(feasible = false, point = null, reason = "I can't order things online."),
      brief = "",
      positionMs = 0L,
      existing = emptyList(),
    )

    assertEquals(
      "I can't order things online. Ask me for a quiz, explainer, timeline, map or calculator instead.",
      assertIs<RequestOutcome.Refuse>(outcome).reply,
    )
  }

  @Test
  fun onlyShelfExtrasKeepAKind() {
    val outcome = outcomeOf(plan(point = "snip_picture", kind = "map"), brief = "", positionMs = 0L, existing = emptyList())

    assertEquals("", assertIs<RequestOutcome.Build>(outcome).card.kind)
  }

  @Test
  fun yesAndNoRepliesAreRecognised() {
    listOf("Yes", "yeah please", "Sure, go ahead", "do it").forEach { assertTrue(isAffirmative(it), it) }
    listOf("No", "nah", "never mind", "Don't bother").forEach { assertTrue(isNegative(it), it) }
    assertFalse(isAffirmative("what about the castle?"))
    assertFalse(isNegative("nobody told me"))
  }

  @Test
  fun requestsAreToldApartFromQuestions() {
    assertTrue(looksLikeExtraRequest("Make me a map of the places so far"))
    assertTrue(looksLikeExtraRequest("Can I get a quiz on this chapter?"))
    assertTrue(looksLikeExtraRequest("I'd like a timeline of the war"))
    assertFalse(looksLikeExtraRequest("Where is the castle?"))
    assertFalse(looksLikeExtraRequest("Who is the man on the map?"))
    assertFalse(looksLikeExtraRequest("Why did she build the boat?"))
  }

  @Test
  fun theBriefKeepsTheRequestAndWhatWasPlaying() {
    assertEquals("The listener asked for this while listening: \"a quiz\"", requestBrief("a quiz", null))
    val brief = requestBrief("a quiz", "x".repeat(5_000))
    assertTrue(brief.startsWith("The listener asked for this while listening: \"a quiz\"\n"))
    assertTrue(brief.endsWith("x".repeat(4_000)))
    assertFalse(brief.endsWith("x".repeat(4_001)))
  }

  private fun plan(
    feasible: Boolean = true,
    point: String? = "shelf",
    kind: String = "map",
    existingId: String? = null,
    existingSummary: String = "",
    reason: String = "",
    alternativePoint: String? = null,
    alternativeTitle: String = "",
    alternativeDescription: String = "",
  ) = RequestPlan(
    isRequest = true,
    feasible = feasible,
    point = point,
    title = "Places so far",
    description = "A map of every place visited so far.",
    kind = kind,
    existingId = existingId,
    existingSummary = existingSummary,
    reason = reason,
    alternativePoint = alternativePoint,
    alternativeTitle = alternativeTitle,
    alternativeDescription = alternativeDescription,
  )
}
