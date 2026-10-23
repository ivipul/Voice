package voice.core.copilot

import voice.core.data.ChapterId
import voice.core.transcript.BookSection
import voice.core.transcript.TranscriptCue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskRoutingTest {

  @Test
  fun obviousQuestionsAreRoutedWithoutClaude() {
    assertEquals(AskRoute.BuildExtra, quickRoute("Make me a quiz on this chapter")?.route)
    assertEquals(AskRoute.Chapter, quickRoute("What has happened in this chapter?")?.route)
    assertEquals(AskRoute.Recent, quickRoute("What did he just say?")?.route)
    assertNull(quickRoute("Who is Mordecai?"))
    assertNull(quickRoute("When was the printing press invented?"))
  }

  @Test
  fun theRoutersAnswerIsKeptWithinBounds() {
    val decision = RouteAnswer(route = "book", recentMinutes = 45, searchTerms = listOf(" Donut ", "Donut", "x", "Mongo")).toDecision()

    assertEquals(AskRoute.Book, decision.route)
    assertEquals(10, decision.recentMinutes)
    assertEquals(listOf("Donut", "Mongo"), decision.searchTerms)
    assertEquals(AskRoute.Recent, RouteAnswer("something else", 1, emptyList()).toDecision().route)
    assertEquals(2, RouteAnswer("web", 1, emptyList()).toDecision().recentMinutes)
  }

  @Test
  fun passagesAroundEachMentionAreKeptInBookOrder() {
    val one = section(0, "Chapter 1")
    val two = section(1, "Chapter 2")
    val heard = listOf(
      one to lines("a", "b", "c", "Mordecai waves", "d", "e", "f", "g", "h", "i", "j", "k", "Mordecai again", "l"),
      two to lines("nothing", "here"),
    )

    val text = passagesFor(heard, listOf("mordecai"), maxChars = 10_000)

    assertEquals("Chapter 1: …a b c Mordecai waves d e f…\n\nChapter 1: …i j k Mordecai again l…", text)
    assertEquals("", passagesFor(heard, emptyList(), maxChars = 10_000))
  }

  @Test
  fun whenPassagesDontFitTheOnesWithMoreMentionsWin() {
    val heard = listOf(
      section(0, "One") to lines("Zev once", "x", "x", "x", "x", "x", "x", "x", "x", "x"),
      section(1, "Two") to lines("Zev and Zev", "Zev"),
    )

    val text = passagesFor(heard, listOf("Zev"), maxChars = 20)

    assertEquals("Two: …Zev and Zev Zev…", text)
  }

  @Test
  fun rangesThatTouchAreMerged() {
    assertEquals(listOf(0..4, 8..9), mergedRanges(listOf(1, 3, 9), around = 1, size = 10))
  }

  @Test
  fun summariesCoverOnlyFinishedChaptersThatHaveOne() {
    val finished = listOf(section(0, "One"), section(1, "Two"))
    val block = summariesBlock("Book", finished, mapOf(finished[0].key to "Things happen.", "other@0" to "Later."))

    assertTrue(block!!.endsWith("One: Things happen."))
    assertNull(summariesBlock("Book", finished, emptyMap()))
  }

  @Test
  fun theQuestionComesAfterItsLabelledExcerpts() {
    val prompt = userQuestionPrompt("Why?", listOf(AskContext.Excerpt("The last 4 minutes", "text")))

    assertEquals("The last 4 minutes:\n\"\"\"\ntext\n\"\"\"\n\nQuestion: Why?", prompt)
    assertTrue(userQuestionPrompt("Why?", emptyList()).startsWith("There is no transcript"))
  }

  private fun section(index: Int, name: String) = BookSection(index, ChapterId("file:///book.m4b"), name, index * 1000L, index * 1000L + 999)

  private fun lines(vararg texts: String) = texts.mapIndexed { i, text -> TranscriptCue(i * 10L, i * 10L + 9, text) }
}
