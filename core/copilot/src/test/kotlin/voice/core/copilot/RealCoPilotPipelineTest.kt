package voice.core.copilot

import androidx.datastore.core.DataStore
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest
import voice.core.copilot.frame.SnipFrameRunner
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.CoPilotVoiceSettings
import voice.core.data.repo.BookRepository
import voice.core.transcript.TranscriptRepository
import java.time.Instant
import kotlin.random.Random
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
  private val snipFrameRunner = mockk<SnipFrameRunner>(relaxed = true) {
    coEvery { isAvailable() } returns false
  }

  private val voiceStore = MemoryDataStore(CoPilotVoiceSettings())

  private val pipeline = RealCoPilotPipeline(
    bookRepository = bookRepository,
    transcriptRepository = transcriptRepository,
    jevRouter = jevRouter,
    geminiClient = geminiClient,
    coPilotRepository = coPilotRepository,
    voiceSettingsStore = voiceStore,
    snipFrameRunner = snipFrameRunner,
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
    voiceStore.updateData { it.copy(useSystemAiVoice = false) }
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
    voiceStore.updateData { it.copy(useSystemAiVoice = false) }
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
  fun `snip reports started then the saved text`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(any(), any()) } returns "Carl's blade froze mid-swing."

    coPilotRepository.snipEvents.test {
      pipeline.snip(book.id)

      assertEquals(SnipEvent.Started(drawsFrame = false), awaitItem())
      val ready = awaitItem() as SnipEvent.Ready
      assertEquals("Carl's blade froze mid-swing.", ready.text)
      assertEquals(coPilotRepository.allMessagesByBook.value.getValue(book.id).single().id, ready.messageId)
    }
  }

  @Test
  fun `snip reports failure when Gemini fails`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(any(), any()) } throws IllegalStateException("network down")

    coPilotRepository.snipEvents.test {
      pipeline.snip(book.id)

      assertEquals(SnipEvent.Started(drawsFrame = false), awaitItem())
      assertEquals(SnipEvent.Failed, awaitItem())
    }
  }

  @Test
  fun `snip events with no listener are dropped, not replayed later`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(any(), any()) } returns "Carl's blade froze mid-swing."

    pipeline.snip(book.id)

    coPilotRepository.snipEvents.test {
      expectNoEvents()
    }
  }

  @Test
  fun `snip hands the moment to the frame runner when frames can be drawn`() = runTest {
    coEvery { snipFrameRunner.isAvailable() } returns true
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(any(), any()) } returns "Carl's blade froze mid-swing."

    pipeline.snip(book.id)

    val saved = coPilotRepository.allMessagesByBook.value.getValue(book.id).single()
    assertTrue(saved.isGeneratingImage)
    verify {
      snipFrameRunner.start(
        book.id,
        saved.id,
        match { it.highlight == "Carl's blade froze mid-swing." && it.transcript == "the last three minutes" && it.positionMs == book.position },
      )
    }
  }

  @Test
  fun `snip saves plain text and starts nothing when frames cannot be drawn`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(any(), any()) } returns "Carl's blade froze mid-swing."

    pipeline.snip(book.id)

    assertFalse(coPilotRepository.allMessagesByBook.value.getValue(book.id).single().isGeneratingImage)
    verify(exactly = 0) { snipFrameRunner.start(any(), any(), any()) }
  }

  @Test
  fun `snip does nothing when there is no transcript yet`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns null

    pipeline.snip(book.id)

    assertTrue(coPilotRepository.allMessagesByBook.value[book.id].orEmpty().isEmpty())
  }

  @Test
  fun `fallbacks speak in the System AI voice by default`() = runTest {
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns null

    val result = pipeline.catchMeUp(book.id)

    assertTrue(result.startsWith("Warning!"))
  }

  @Test
  fun `ask uses the System AI persona with a 60-80 word budget by default`() = runTest {
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    assertEquals("the answer", pipeline.ask(book.id, "who is Mordecai?"))

    assertTrue(systemPrompt.captured.contains("System AI from Dungeon Crawler Carl"))
    assertTrue(systemPrompt.captured.contains("60-80 words"))
    assertTrue(systemPrompt.captured.contains("Occasional swearing"))
    assertFalse(systemPrompt.captured.contains("Keep it clean"))
  }

  @Test
  fun `keep it clean swaps the crude allowance for a no swearing no sexual lines rule`() = runTest {
    voiceStore.updateData { it.copy(keepItClean = true) }
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    assertEquals("the answer", pipeline.ask(book.id, "who is Mordecai?"))

    assertTrue(systemPrompt.captured.contains("Keep it clean: no swearing or profanity, and no sexual"))
    assertFalse(systemPrompt.captured.contains("Occasional swearing"))
  }

  @Test
  fun `turning the System AI voice off restores the plain assistant and ignores keep it clean`() = runTest {
    voiceStore.updateData { CoPilotVoiceSettings(useSystemAiVoice = false, keepItClean = true) }
    val systemPrompt = slot<String>()
    coEvery { jevRouter.needsTranscriptContext(any()) } returns false
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "the answer"

    assertEquals("the answer", pipeline.ask(book.id, "who is Mordecai?"))

    assertTrue(systemPrompt.captured.contains("You are a voice assistant for an audiobook app."))
    assertFalse(systemPrompt.captured.contains("System AI"))
    assertFalse(systemPrompt.captured.contains("Keep it clean"))
    assertTrue(systemPrompt.captured.contains("40-60 words"))
    assertFalse(systemPrompt.captured.contains("60-80 words"))
  }

  @Test
  fun `snip writes the caption in the System AI voice by default`() = runTest {
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "Nice."

    pipeline.snip(book.id)

    assertTrue(systemPrompt.captured.contains("System AI from Dungeon Crawler Carl"))
    assertTrue(systemPrompt.captured.contains("Occasional swearing"))
  }

  @Test
  fun `snip keep it clean forbids swearing and sexual lines`() = runTest {
    voiceStore.updateData { it.copy(keepItClean = true) }
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "Nice."

    pipeline.snip(book.id)

    assertTrue(systemPrompt.captured.contains("Keep it clean: no swearing or profanity, and no sexual"))
  }

  @Test
  fun `snip keeps the plain caption prompt when the System AI voice is off`() = runTest {
    voiceStore.updateData { it.copy(useSystemAiVoice = false) }
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "the last three minutes"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "Nice."

    pipeline.snip(book.id)

    assertFalse(systemPrompt.captured.contains("System AI"))
    assertTrue(systemPrompt.captured.contains("single most vivid, emotionally charged moment"))
  }

  @Test
  fun `failure message is in the System AI voice by default and plain when it is off`() = runTest {
    assertTrue(pipeline.failureMessage().startsWith("System message."))

    voiceStore.updateData { it.copy(useSystemAiVoice = false) }

    assertEquals("Sorry, something went wrong.", pipeline.failureMessage())
  }

  @Test
  fun `catchMeUp gets the 80-100 word budget and never a banner flavor`() = runTest {
    val systemPrompt = slot<String>()
    coEvery { transcriptRepository.textForPrecedingWindow(book, any()) } returns "plot"
    coEvery { geminiClient.ask(capture(systemPrompt), any()) } returns "In the last 30 minutes, things happened."

    repeat(30) { assertTrue(pipeline.catchMeUp(book.id).isNotEmpty()) }

    assertTrue(systemPrompt.captured.contains("80-100 words"))
    assertTrue(systemPrompt.captured.contains(SystemFlavor.Plain.instruction))
  }

  @Test
  fun `banner flavors are rare and plain answers dominate`() {
    val random = Random(42)
    val counts = (1..1000).map { pickSystemFlavor(random) }.groupingBy { it }.eachCount()

    assertTrue(SystemFlavor.entries.all { (counts[it] ?: 0) > 0 })
    assertTrue(counts.getValue(SystemFlavor.Plain) in 550..750)
    assertTrue(counts.getValue(SystemFlavor.Achievement) in 60..180)
  }

  @Test
  fun `defaults enable the System AI voice and leave keep it clean off`() {
    assertEquals(CoPilotVoiceSettings(useSystemAiVoice = true, keepItClean = false), CoPilotVoiceSettings())
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {
  private val value = MutableStateFlow(initial)
  override val data: Flow<T> get() = value
  override suspend fun updateData(transform: suspend (t: T) -> T): T = value.updateAndGet { transform(it) }
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
