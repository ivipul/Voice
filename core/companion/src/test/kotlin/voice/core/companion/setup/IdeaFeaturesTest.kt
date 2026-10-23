package voice.core.companion.setup

import voice.core.companion.ExtensionPoint
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaFeature
import voice.core.companion.extras.featureText
import kotlin.test.Test
import kotlin.test.assertEquals

class IdeaFeaturesTest {

  @Test
  fun featuresAreTrimmedDedupedAndCappedAtFour() {
    val features = featuresOf(listOf(" Quiz per chapter. ", "", "Score history", "Quiz per chapter", "Hints", "Timer", "Badges"))

    assertEquals(listOf("Quiz per chapter", "Score history", "Hints", "Timer"), features.map { it.label })
    assertEquals(true, features.all { it.included })
  }

  @Test
  fun theBuildBriefKeepsTickedPartsAndLeavesOutUnticked() {
    val card = IdeaCard(
      id = "a",
      title = "Chapter quiz",
      description = "",
      point = ExtensionPoint.Shelf,
      features = listOf(IdeaFeature("Hints"), IdeaFeature("Timer", included = false), IdeaFeature("Score history")),
    )

    assertEquals("\nInclude: Hints; Score history\nLeave out: Timer", card.featureText())
  }

  @Test
  fun aCardWithoutFeaturesAddsNothing() {
    val card = IdeaCard(id = "a", title = "Chips", description = "", point = ExtensionPoint.SceneChips)

    assertEquals("", card.featureText())
  }
}
