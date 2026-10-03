package voice.core.copilot

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.transcript.TranscriptRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RealCoPilotPipelineTest {

  private val book = fakeBook()
  private val bookRepository = mockk<BookRepository> {
    coEvery { get(book.id) } returns book
  }
  private val transcriptRepository = mockk<TranscriptRepository>()
  private val jevRouter = mockk<JevRouter>()
  private val geminiClient = mockk<GeminiClient>()
  private val coPilotRepository = CoPilotRepository()
  private var voiceStyle = VoiceStyle.Plain

  private val pipeline = RealCoPilotPipeline(
    bookRepository = bookRepository,
    transcriptRepository = transcriptRepository,
    jevRouter = jevRouter,
    geminiClient = geminiClient,
    coPilotRepository = coPilotRepository,
    voiceStyleSource = { voiceStyle },
  )

  @Test
  fun `ask injects transcript when Jev says it's needed`() = runTest {
    val userPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns true
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "recent plot events"
    coEvery { geminiClient.ask(any(), capture(userPrompt)) } returns "the answer"

    val result = pipeline.ask(book.id, "what just happened?")

    assertEquals("the answer", result)
    assertTrue(userPrompt.captured.contains("recent plot events"))
  }

  @Test
  fun `ask skips the transcript when Jev says it's not needed`() = runTest {
    var transcriptRequested = false
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } answers {
      transcriptRequested = true
      "recent plot events"
    }
    coEvery { geminiClient.ask(any(), any()) } returns "the answer"

    val result = pipeline.ask(book.id, "who is Carl?")

    assertEquals("the answer", result)
    assertTrue(!transcriptRequested)
  }

  @Test
  fun `ask falls back gracefully when Gemini fails`() = runTest {
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(any(), any()) } throws IllegalStateException("network down")

    val result = pipeline.ask(book.id, "who is Carl?")

    assertTrue(result.isNotBlank())
  }

  @Test
  fun `autoIdentify prioritizes the last 60 seconds over the 30-minute context`() = runTest {
    val userPrompt = slot<String>()
    coEvery {
      transcriptRepository.textForPrecedingWindow(book, 60.seconds.inWholeMilliseconds)
    } returns "the last 60 seconds"
    coEvery {
      transcriptRepository.textForPrecedingWindow(book, 30.minutes.inWholeMilliseconds)
    } returns "the last 30 minutes"
    coEvery { geminiClient.ask(any(), capture(userPrompt)) } returns "here's what's happening"

    val result = pipeline.autoIdentify(book.id)

    assertEquals("here's what's happening", result)
    assertTrue(userPrompt.captured.contains("the last 60 seconds"))
    assertTrue(userPrompt.captured.contains("the last 30 minutes"))
  }

  @Test
  fun `autoIdentify still answers when there's no 30-minute context yet`() = runTest {
    coEvery {
      transcriptRepository.textForPrecedingWindow(book, 60.seconds.inWholeMilliseconds)
    } returns "the last 60 seconds"
    coEvery {
      transcriptRepository.textForPrecedingWindow(book, 30.minutes.inWholeMilliseconds)
    } returns null
    coEvery { geminiClient.ask(any(), any()) } returns "here's what's happening"

    val result = pipeline.autoIdentify(book.id)

    assertEquals("here's what's happening", result)
  }

  @Test
  fun `autoIdentify falls back when there's no transcript at all`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns null

    val result = pipeline.autoIdentify(book.id)

    assertEquals("I don't have a transcript for this book yet, so I can't answer that.", result)
  }

  @Test
  fun `catchMeUp always summarizes the last 30 minutes with an explicit opening`() = runTest {
    val systemPrompt = slot<String>()
    coEvery {
      transcriptRepository.textForPrecedingWindow(book, 30.minutes.inWholeMilliseconds)
    } returns "the last 30 minutes of plot"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "In the last 30 minutes, Carl fought a goblin."

    val result = pipeline.catchMeUp(book.id)

    assertEquals("In the last 30 minutes, Carl fought a goblin.", result)
    assertTrue(systemPrompt.captured.contains("In the last 30 minutes,"))
  }

  @Test
  fun `catchMeUp falls back when there's no transcript yet`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns null

    val result = pipeline.catchMeUp(book.id)

    assertEquals("I don't have a transcript for this book yet, so I can't answer that.", result)
  }

  @Test
  fun `snip saves an evocative visual-priority message to the Feed`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(any(), any()) } returns "Carl's blade froze mid-swing as the goblin laughed."

    pipeline.snip(book.id)

    val messages = coPilotRepository.allMessagesByBook.value.getValue(book.id)
    val saved = messages.single()
    assertEquals(CoPilotMessage.Role.CoPilot, saved.role)
    assertTrue(saved.isVisualPriority)
    assertEquals("Carl's blade froze mid-swing as the goblin laughed.", saved.text)
    assertEquals(book.currentChapter.id, saved.snipChapterId)
    assertEquals(book.content.positionInChapter, saved.snipPositionInChapterMs)
  }

  @Test
  fun `snip does nothing when there is no transcript yet`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns null

    pipeline.snip(book.id)

    assertTrue(coPilotRepository.allMessagesByBook.value[book.id].orEmpty().isEmpty())
  }

  @Test
  fun `plain style keeps the original prompts and word budget`() = runTest {
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    pipeline.ask(book.id, "who is Carl?")

    assertTrue(systemPrompt.captured.contains("roughly 40-60 words"))
    assertFalse(systemPrompt.captured.contains("System AI"))
  }

  @Test
  fun `System AI voice adds the persona and widens ask to 60-80 words`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = true, keepItClean = false)
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    pipeline.ask(book.id, "who is Carl?")

    assertTrue(systemPrompt.captured.contains("System AI from Dungeon Crawler Carl"))
    assertTrue(systemPrompt.captured.contains("roughly 60-80 words"))
    assertFalse(systemPrompt.captured.contains("KEEP IT CLEAN"))
  }

  @Test
  fun `keep it clean adds the no swearing or sexual lines instruction`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = true, keepItClean = true)
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    pipeline.ask(book.id, "who is Carl?")

    assertTrue(systemPrompt.captured.contains("KEEP IT CLEAN"))
    assertTrue(systemPrompt.captured.contains("no swearing or profanity and no sexual or suggestive lines"))
  }

  @Test
  fun `keep it clean is ignored while the System AI voice is off`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = false, keepItClean = true)
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    pipeline.ask(book.id, "who is Carl?")

    assertFalse(systemPrompt.captured.contains("KEEP IT CLEAN"))
    assertTrue(systemPrompt.captured.contains("roughly 40-60 words"))
  }

  @Test
  fun `System AI voice autoIdentify uses the 60-80 word budget`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = true, keepItClean = false)
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "text"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "here's what's happening"

    pipeline.autoIdentify(book.id)

    assertTrue(systemPrompt.captured.contains("roughly 60-80 words"))
  }

  @Test
  fun `System AI voice catchMeUp uses the 80-100 word budget without the forced opening`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = true, keepItClean = false)
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "text"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "a recap"

    pipeline.catchMeUp(book.id)

    assertTrue(systemPrompt.captured.contains("roughly 80-100 words"))
    assertFalse(systemPrompt.captured.contains("exact words"))
  }

  @Test
  fun `System AI voice snip asks for a System AI caption`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = true, keepItClean = true)
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "text"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "New achievement! Nice."

    pipeline.snip(book.id)

    assertTrue(systemPrompt.captured.contains("System AI from Dungeon Crawler Carl"))
    assertTrue(systemPrompt.captured.contains("KEEP IT CLEAN"))
    assertEquals("New achievement! Nice.", coPilotRepository.allMessagesByBook.value.getValue(book.id).single().text)
  }

  @Test
  fun `System AI voice fallbacks are worded in character`() = runTest {
    voiceStyle = VoiceStyle(systemAiVoice = true, keepItClean = false)
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns null
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(any(), any()) } throws IllegalStateException("network down")

    assertTrue(pipeline.catchMeUp(book.id).startsWith("Query failed."))
    assertTrue(pipeline.ask(book.id, "who is Carl?").startsWith("Query failed."))
    assertTrue(pipeline.failureMessage().startsWith("Error."))
  }

  @Test
  fun `plain failure message is the original apology`() = runTest {
    assertEquals("Sorry, something went wrong.", pipeline.failureMessage())
  }
}

private fun fakeBook(): Book {
  val chapterId = ChapterId(Uuid.random().toString())
  val chapter = Chapter(
    id = chapterId,
    name = "Chapter 1",
    duration = 60 * 60 * 1000L,
    fileLastModified = Instant.EPOCH,
    fileSize = 0,
    markData = emptyList(),
  )
  return Book(
    content = BookContent(
      id = BookId(Uuid.random().toString()),
      playbackSpeed = 1f,
      skipSilence = false,
      isActive = true,
      lastPlayedAt = Instant.EPOCH,
      author = null,
      name = "Dungeon Crawler Carl",
      addedAt = Instant.EPOCH,
      chapters = listOf(chapterId),
      currentChapter = chapterId,
      positionInChapter = 5 * 60 * 1000L,
      cover = null,
      gain = 0f,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    ),
    chapters = listOf(chapter),
  )
}
