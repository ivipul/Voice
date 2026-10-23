package voice.features.playbackScreen

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import voice.core.common.DispatcherProvider
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.copilot.SnipEvent
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.KioskModeDemoData
import voice.core.data.MarkData
import voice.core.data.repo.BookmarkRepo
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.playback.CurrentBookResolver
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.core.companion.BookKind
import voice.core.companion.BookLook
import voice.core.companion.CompanionStores
import voice.core.companion.Extra
import voice.core.companion.ExtraShelf
import voice.core.companion.LookSource
import voice.core.companion.SceneCue
import voice.core.companion.SceneEntry
import voice.core.companion.SceneIndex
import voice.core.companion.SceneNote
import voice.core.companion.StoredPalette
import voice.core.transcript.BookFiles
import voice.core.ui.crawl.toStyle
import voice.core.playback.speech.MessageSpeaker
import voice.core.playback.speech.SpokenMessage
import voice.features.playbackScreen.copilot.DictationEvent
import voice.features.playbackScreen.view.knownNotes
import voice.features.playbackScreen.copilot.SpeechInputController
import voice.features.sleepTimer.SleepTimerViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import java.io.File
import kotlin.io.path.createTempDirectory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class BookPlayViewModelTest {

  private val scope = TestScope()
  private val sleepTimerDataStore = MemoryDataStore(SleepTimerPreference.Default.copy(duration = 5.minutes))
  private val book = book()
  private val sleepTimer = mockk<SleepTimer> {
    val stateFlow = MutableStateFlow<SleepTimerState>(SleepTimerState.Disabled)
    every {
      state
    } returns stateFlow
    every {
      enable(any())
    } answers {
      stateFlow.value = when (val mode = firstArg<SleepTimerMode>()) {
        is TimedWithDuration -> SleepTimerState.Enabled.WithDuration(mode.duration)
        SleepTimerMode.TimedWithDefault -> SleepTimerState.Enabled.WithDuration(runBlocking { sleepTimerDataStore.data.first() }.duration)
        SleepTimerMode.EndOfChapter -> SleepTimerState.Enabled.WithEndOfChapter
      }
    }
    every {
      disable()
    } answers {
      stateFlow.value = SleepTimerState.Disabled
    }
  }

  private val player = mockk<PlayerController>()
  private val playStateManager = mockk<PlayStateManager> {
    every { playStateFlow } returns MutableStateFlow(PlayStateManager.PlayState.Paused)
  }
  private val currentBookStoreId = MemoryDataStore<BookId?>(null)
  private val currentBookResolver = mockk<CurrentBookResolver> {
    coEvery { book(book.id) } returns book
  }
  private val copilotRepository = CoPilotRepository()
  private val copilotPipeline = mockk<CoPilotPipeline> {
    every { awaitingReply(any()) } returns false
  }
  private val speechInputController = mockk<SpeechInputController>()
  private val spokenMessage = MutableStateFlow<SpokenMessage?>(null)
  private val messageSpeaker = mockk<MessageSpeaker> {
    every { speaking } returns spokenMessage
    every { speak(any(), any()) } answers { spokenMessage.value = SpokenMessage(firstArg(), audible = false) }
    every { stop() } answers { spokenMessage.value = null }
  }
  private val bookmarkRepository = mockk<BookmarkRepo> {
    coEvery { addBookmarkAtBookPosition(book, any(), any()) } returns Bookmark(
      bookId = book.id,
      chapterId = book.currentChapter.id,
      addedAt = Instant.now(),
      setBySleepTimer = true,
      id = Bookmark.Id(Uuid.random()),
      time = 0L,
      title = null,
    )
  }
  private val navigator = mockk<Navigator>(relaxed = true)
  private val filesDir: File = createTempDirectory().toFile()
  private val stores = CompanionStores(BookFiles(mockk { every { filesDir } returns this@BookPlayViewModelTest.filesDir }))
  private val viewModel = BookPlayViewModel(
    bookRepository = mockk {
      coEvery { get(book.id) } returns book
      every { flow(book.id) } returns MutableStateFlow(book)
    },
    currentBookResolver = currentBookResolver,
    player = player.apply {
      every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
    },
    sleepTimer = sleepTimer,
    playStateManager = playStateManager,
    currentBookStoreId = currentBookStoreId,
    navigator = navigator,
    bookmarkRepository = bookmarkRepository,
    volumeGainFormatter = mockk(),
    batteryOptimization = mockk(),
    sleepTimerPreferenceStore = sleepTimerDataStore,
    seekTimeStore = MemoryDataStore(15),
    seekForwardTimeStore = MemoryDataStore(30),
    bookId = book.id,
    dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
    experimentalPlaybackPersistenceFeatureFlag = MemoryFeatureFlag(false),
    kioskModeFeatureFlag = MemoryFeatureFlag(false),
    copilotRepository = copilotRepository,
    copilotPipeline = copilotPipeline,
    speechInputController = speechInputController,
    messageSpeaker = messageSpeaker,
    stores = stores,
  )

  @Test
  fun sleepTimerValueChanging() = scope.runTest {
    fun assertDialogSleepTime(expected: Int) {
      assertEquals(expected = BookPlayDialogViewState.SleepTimer(SleepTimerViewState(expected)), actual = viewModel.dialogState.value)
    }

    viewModel.toggleSleepTimer()
    yield()
    assertDialogSleepTime(5)

    suspend fun incrementAndAssert(time: Int) {
      viewModel.incrementSleepTime()
      yield()
      assertDialogSleepTime(time)
    }

    suspend fun decrementAndAssert(time: Int) {
      viewModel.decrementSleepTime()
      yield()
      assertDialogSleepTime(time)
    }

    decrementAndAssert(4)
    decrementAndAssert(3)
    decrementAndAssert(2)
    decrementAndAssert(1)

    decrementAndAssert(1)

    incrementAndAssert(2)
    incrementAndAssert(3)
  }

  @Test
  fun sleepTimerSettingFixedValue() = scope.runTest {
    viewModel.toggleSleepTimer()
    viewModel.onAcceptSleepTime(10)
    assertEquals(expected = 5.minutes, actual = sleepTimerDataStore.data.first().duration)
    yield()
    verify(exactly = 1) {
      sleepTimer.enable(TimedWithDuration(10.minutes))
    }
  }

  @Test
  fun deactivateSleepTimer() = scope.runTest {
    viewModel.toggleSleepTimer()
    viewModel.onAcceptSleepTime(10)
    viewModel.toggleSleepTimer()
    yield()
    verifyOrder {
      sleepTimer.enable(TimedWithDuration(10.minutes))
      sleepTimer.disable()
    }
    assertIs<SleepTimerState.Disabled>(sleepTimer.state.value)
  }

  @Test
  fun onCurrentChapterClickShowsDialogWithCorrectState() = scope.runTest {
    viewModel.onCurrentChapterClick()
    yield()

    val dialogState = assertIs<BookPlayDialogViewState.SelectChapterDialog>(viewModel.dialogState.value)

    assertEquals(
      expected = listOf(
        BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
          number = 1,
          name = "Chapter Start",
          active = false,
          time = "0:00",
        ),
        BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
          number = 2,
          name = "Middle Section",
          active = false,
          time = "2:00",
        ),
        BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
          number = 3,
          name = "Final Section",
          active = false,
          time = "4:00",
        ),
        BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
          number = 4,
          name = "Chapter Start",
          active = false,
          time = "5:00",
        ),
        BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
          number = 5,
          name = "Middle Section",
          active = true,
          time = "7:00",
        ),
        BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
          number = 6,
          name = "Final Section",
          active = false,
          time = "9:00",
        ),
      ),
      actual = dialogState.items,
    )
  }

  @Test
  fun onChapterClickSetsPositionAndDismissesDialog() = scope.runTest {
    every { player.setPosition(any(), any()) } just Runs

    viewModel.onCurrentChapterClick()
    yield()

    assertIs<BookPlayDialogViewState.SelectChapterDialog>(viewModel.dialogState.value)

    viewModel.onChapterClick(number = 2)
    yield()

    // Verify player.setPosition was called with correct parameters
    // The second mark starts at 2 minutes position in the first chapter
    verify(exactly = 1) {
      player.setPosition(time = 2.minutes.inWholeMilliseconds, id = book.chapters.first().id)
    }

    assertEquals(expected = null, actual = viewModel.dialogState.value)
  }

  @Test
  fun `overlay prefers live controller position`() {
    val persistedBook = book()
    val overlaidBook = persistedBook.overlay(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.chapters.first().id,
        positionMs = 1.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )

    assertEquals(expected = persistedBook.chapters.first().id, actual = overlaidBook.currentChapter.id)
    assertEquals(expected = 1.minutes.inWholeMilliseconds, actual = overlaidBook.content.positionInChapter)
  }

  @Test
  fun `viewState prefers live playback state when feature flag is enabled`() = scope.runTest {
    val persistedBook = book()
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel = viewModel(
      book = persistedBook,
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = livePlaybackFlow,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      assertEquals(expected = null, actual = awaitItem())
      assertEquals(expected = 30.seconds, actual = awaitItem()!!.playedTime)

      livePlaybackFlow.value = LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.chapters.first().id,
        positionMs = 1.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )

      val state = awaitItem()!!
      assertEquals(expected = true, actual = state.playing)
      assertEquals(expected = "Chapter Start", actual = state.chapterName)
      assertEquals(expected = 1.minutes, actual = state.playedTime)
    }
  }

  @Test
  fun `viewState falls back to manager play state when live playback is unavailable`() = scope.runTest {
    val viewModel = viewModel(
      experimentalPlaybackPersistence = true,
      livePlaybackFlow = MutableStateFlow(null),
      playStateFlow = MutableStateFlow(PlayStateManager.PlayState.Playing),
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      assertEquals(expected = null, actual = awaitItem())
      val state = awaitItem()!!
      assertEquals(expected = true, actual = state.playing)
      assertEquals(expected = 30.seconds, actual = state.playedTime)
    }
  }

  // The book is two 5 minute files and sits at 2:30 into the second one: 7:30 of the whole book.
  private val bookPositionMs = 450_000L

  private fun extra(id: String, unlockAtMs: Long) = Extra(
    id = id,
    title = "Extra $id",
    description = "About $id",
    html = "<p>$id</p>",
    createdAtMs = 0L,
    unlockAtMs = unlockAtMs,
  )

  @Test
  fun `viewState takes the book's colors from its look, and the playback speed`() = scope.runTest {
    val faster = book.let { it.copy(content = it.content.copy(playbackSpeed = 1.3F)) }
    val look = BookLook(
      source = LookSource.Cover,
      palette = StoredPalette(0xFFE7EEF2.toInt(), 0xFF101820.toInt(), 0xFF2F5D8A.toInt(), -1, 0xFF101820.toInt(), -1, 0xFFB4C4CF.toInt()),
      displayFont = "classic",
    )
    stores.look.set(faster.id, look)
    val viewModel = viewModel(book = faster)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state?.bookStyle == null) state = awaitItem()
      assertEquals(look.toStyle(), state.bookStyle)
      assertEquals(1.3F, state.playbackSpeed)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a book without a look gets the app's periwinkle and navy`() = scope.runTest {
    val viewModel = viewModel()

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state?.bookStyle == null) state = awaitItem()
      assertEquals(BookLook().toStyle(), state.bookStyle)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `scene chips show who is in the scene at the whole-book position`() = scope.runTest {
    stores.scene.set(
      book.id,
      SceneIndex(
        kind = BookKind.Fiction,
        entries = listOf(SceneEntry(id = "anna", name = "Anna"), SceneEntry(id = "boris", name = "Boris")),
        cues = listOf(
          SceneCue(entry = "anna", startMs = 400_000L, endMs = 500_000L),
          SceneCue(entry = "boris", startMs = 100_000L, endMs = 200_000L),
        ),
      ),
    )
    val viewModel = viewModel()

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state == null || state.xrayChips.isEmpty()) state = awaitItem()
      assertEquals(listOf(BookPlayViewState.XRayChipViewState(id = "anna", label = "Anna")), state.xrayChips)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `tapping a chip opens its card with the notes and the unlocked Extra of its topic`() = scope.runTest {
    val notes = listOf(SceneNote(fromMs = 60_000L, text = "Defined."), SceneNote(fromMs = 900_000L, text = "Later point."))
    stores.scene.set(
      book.id,
      SceneIndex(
        kind = BookKind.NonFiction,
        entries = listOf(SceneEntry(id = "compounding", name = "Compounding", summary = "Growth on growth.", notes = notes, extraId = "calc")),
        cues = listOf(SceneCue(entry = "compounding", startMs = 60_000L, endMs = 500_000L)),
      ),
    )
    stores.shelf.set(book.id, ExtraShelf(listOf(extra("calc", unlockAtMs = 60_000L))))
    val viewModel = viewModel()

    viewModel.onXRayChipClick("compounding")
    runCurrent()

    val dialog = assertIs<BookPlayDialogViewState.SceneCardDialog>(viewModel.dialogState.value)
    assertEquals("Compounding", dialog.name)
    assertEquals(false, dialog.fiction)
    assertEquals("calc", dialog.extraId)
    assertEquals(bookPositionMs, dialog.openedAtMs)
    assertEquals(listOf("Defined."), knownNotes(dialog.notes, dialog.openedAtMs))
  }

  @Test
  fun `a topic's Extra still ahead in the book is not offered on its card`() = scope.runTest {
    stores.scene.set(
      book.id,
      SceneIndex(
        kind = BookKind.NonFiction,
        entries = listOf(SceneEntry(id = "risk", name = "Risk", extraId = "quiz")),
        cues = listOf(SceneCue(entry = "risk", startMs = 0L, endMs = 10_000L)),
      ),
    )
    stores.shelf.set(book.id, ExtraShelf(listOf(extra("quiz", unlockAtMs = 600_000L))))
    val viewModel = viewModel()

    viewModel.onXRayChipClick("risk")
    runCurrent()

    assertEquals(null, assertIs<BookPlayDialogViewState.SceneCardDialog>(viewModel.dialogState.value).extraId)
  }

  @Test
  fun `the speed sheet opens on the book's speed and follows changes`() = scope.runTest {
    every { player.setSpeed(any()) } just Runs

    viewModel.onPlaybackSpeedIconClick()
    yield()
    assertEquals(BookPlayDialogViewState.SpeedDialog(1F), viewModel.dialogState.value)

    viewModel.onPlaybackSpeedChanged(1.5F)
    assertEquals(BookPlayDialogViewState.SpeedDialog(1.5F), viewModel.dialogState.value)
    verify { player.setSpeed(1.5F) }
  }

  @Test
  fun `the Notebook card counts the book's snips and the Extras reached so far`() = scope.runTest {
    val copilotRepository = CoPilotRepository()
    copilotRepository.addMessage(book.id, snipMessage("first"))
    copilotRepository.addMessage(book.id, snipMessage("second"))
    copilotRepository.addMessage(
      book.id,
      CoPilotMessage(id = "answer", role = CoPilotMessage.Role.CoPilot, text = "An answer.", timestampMs = 0L),
    )
    copilotRepository.addMessage(BookId("another-book"), snipMessage("elsewhere"))
    stores.shelf.set(book.id, ExtraShelf(listOf(extra("reached", unlockAtMs = 60_000L), extra("ahead", unlockAtMs = 500_000L))))
    val viewModel = viewModel(copilotRepository = copilotRepository)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state == null || state.inventoryCount < 3) state = awaitItem()
      assertEquals(3, state.inventoryCount)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `reaching a new Extra toasts once, and Extras already reached when the screen opens do not`() = scope.runTest {
    // The book opens at 7:30; the first Extra unlocked at 1:00 and the second one unlocks at 8:20.
    stores.shelf.set(book.id, ExtraShelf(listOf(extra("reached", unlockAtMs = 60_000L), extra("ahead", unlockAtMs = 500_000L))))
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel = viewModel(experimentalPlaybackPersistence = true, livePlaybackFlow = livePlaybackFlow)
    val effects = mutableListOf<BookPlayViewEffect>()
    backgroundScope.launch { viewModel.viewEffects.collect { effects += it } }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state == null || state.inventoryCount == 0) state = awaitItem()
      runCurrent()
      assertEquals(emptyList<BookPlayViewEffect>(), effects)

      fun playAt(positionInChapter: Duration) {
        livePlaybackFlow.value = LivePlaybackState(
          bookId = book.id,
          chapterId = book.chapters[1].id,
          positionMs = positionInChapter.inWholeMilliseconds,
          isPlaying = true,
          playbackSpeed = 1F,
        )
      }
      playAt(3.minutes)
      awaitItem()
      runCurrent()
      assertEquals(emptyList<BookPlayViewEffect>(), effects)

      playAt(201.seconds)
      awaitItem()
      runCurrent()
      assertEquals(listOf<BookPlayViewEffect>(BookPlayViewEffect.ExtraUnlocked), effects)

      playAt(3.minutes)
      awaitItem()
      playAt(205.seconds)
      awaitItem()
      runCurrent()
      assertEquals(listOf<BookPlayViewEffect>(BookPlayViewEffect.ExtraUnlocked), effects)
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun snipMessage(id: String) = CoPilotMessage(
    id = id,
    role = CoPilotMessage.Role.CoPilot,
    text = "A title\nThe moment.",
    timestampMs = 0L,
    snipChapterId = book.currentChapter.id,
    snipPositionInChapterMs = 1_000L,
  )

  @Test
  fun `viewState uses currently playing demo book in kiosk mode`() = scope.runTest {
    val viewModel = viewModel(kioskMode = true)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      val state = awaitItem()!!
      assertEquals(expected = KioskModeDemoData.currentlyPlaying.title, actual = state.title)
      assertEquals(expected = KioskModeDemoData.currentlyPlaying.chapter, actual = state.chapterName)
      assertEquals(expected = KioskModeDemoData.currentlyPlaying.coverUrl, actual = state.cover)
    }
  }

  @Test
  fun `sending a Feed message routes through the same CoPilotPipeline ask as Open Mic`() = scope.runTest {
    coEvery { copilotPipeline.ask(book.id, "who is Carl?") } returns "Carl is the protagonist."

    viewModel.onChatInputChange("who is Carl?")
    viewModel.onChatSend()
    yield()
    yield()

    val messages = copilotRepository.allMessagesByBook.value.getValue(book.id)
    assertEquals(2, messages.size)
    assertEquals(CoPilotMessage.Role.User, messages[0].role)
    assertEquals("who is Carl?", messages[0].text)
    assertEquals(CoPilotMessage.Role.CoPilot, messages[1].role)
    assertEquals("Carl is the protagonist.", messages[1].text)
  }

  @Test
  fun `when the answer asks whether to build a requested Extra, the chat listens for the reply`() = scope.runTest {
    val heard = MutableSharedFlow<DictationEvent>()
    every { speechInputController.dictate() } returns heard
    coEvery { copilotPipeline.ask(book.id, "make me a quiz") } returns "There's already a quiz. Make a new one?"
    every { copilotPipeline.awaitingReply(book.id) } returns true

    viewModel.onAskClick()
    runCurrent()
    viewModel.onChatInputChange("make me a quiz")
    viewModel.onChatSend()
    runCurrent()

    assertEquals(true, viewModel.chatInput.value.isListening)
    viewModel.onFeedDismiss()
  }

  @Test
  fun `Ask opens the chat listening and writes the words into the text box as they are heard`() = scope.runTest {
    val heard = MutableSharedFlow<DictationEvent>()
    every { speechInputController.dictate() } returns heard

    viewModel.onAskClick()
    runCurrent()

    assertEquals(true, viewModel.feedVisible.value)
    assertEquals(ChatInputViewState(text = "", isListening = true), viewModel.chatInput.value)

    heard.emit(DictationEvent.Partial("who is"))
    runCurrent()
    assertEquals(ChatInputViewState(text = "who is", isListening = true), viewModel.chatInput.value)

    heard.emit(DictationEvent.Partial("who is Carl"))
    runCurrent()
    assertEquals(ChatInputViewState(text = "who is Carl", isListening = true), viewModel.chatInput.value)

    viewModel.onFeedDismiss()
  }

  @Test
  fun `a dictated question sends itself five seconds after the mic stops`() = scope.runTest {
    every { speechInputController.dictate() } returns flowOf(
      DictationEvent.Partial("who is"),
      DictationEvent.Final("who is Carl"),
    )
    coEvery { copilotPipeline.ask(book.id, "who is Carl") } returns "Carl is the protagonist."

    viewModel.onAskClick()
    runCurrent()
    assertEquals(
      ChatInputViewState(text = "who is Carl", isListening = false, autoSendPending = true),
      viewModel.chatInput.value,
    )

    advanceTimeBy(ChatInputViewState.AUTO_SEND_DELAY_MS - 1)
    runCurrent()
    assertEquals(true, viewModel.chatInput.value.autoSendPending)
    assertEquals(null, copilotRepository.allMessagesByBook.value[book.id])

    advanceTimeBy(1)
    runCurrent()
    assertEquals(ChatInputViewState(), viewModel.chatInput.value)
    val messages = copilotRepository.allMessagesByBook.value.getValue(book.id)
    assertEquals(listOf("who is Carl", "Carl is the protagonist."), messages.map { it.text })
  }

  @Test
  fun `tapping the X or the text box during the countdown keeps the text and sends nothing`() = scope.runTest {
    every { speechInputController.dictate() } returns flowOf(DictationEvent.Final("who is Carl"))

    viewModel.onAskClick()
    runCurrent()
    viewModel.onChatAutoSendCancel()
    advanceTimeBy(ChatInputViewState.AUTO_SEND_DELAY_MS * 2)
    runCurrent()
    assertEquals(ChatInputViewState(text = "who is Carl"), viewModel.chatInput.value)

    viewModel.onChatMicClick()
    runCurrent()
    assertEquals(true, viewModel.chatInput.value.autoSendPending)
    viewModel.onChatInputTap()
    advanceTimeBy(ChatInputViewState.AUTO_SEND_DELAY_MS * 2)
    runCurrent()
    assertEquals(false, viewModel.chatInput.value.autoSendPending)

    assertEquals(null, copilotRepository.allMessagesByBook.value[book.id])
  }

  @Test
  fun `tapping the mic or the text box while listening stops the mic without a countdown`() = scope.runTest {
    val heard = MutableSharedFlow<DictationEvent>()
    every { speechInputController.dictate() } returns heard

    viewModel.onAskClick()
    runCurrent()
    heard.emit(DictationEvent.Partial("who is"))
    runCurrent()
    viewModel.onChatMicClick()
    runCurrent()
    assertEquals(ChatInputViewState(text = "who is"), viewModel.chatInput.value)
    assertEquals(0, heard.subscriptionCount.value)

    viewModel.onChatMicClick()
    runCurrent()
    assertEquals(true, viewModel.chatInput.value.isListening)
    heard.emit(DictationEvent.Partial("Carl"))
    runCurrent()
    assertEquals("who is Carl", viewModel.chatInput.value.text)

    viewModel.onChatInputTap()
    runCurrent()
    advanceTimeBy(ChatInputViewState.AUTO_SEND_DELAY_MS * 2)
    assertEquals(ChatInputViewState(text = "who is Carl"), viewModel.chatInput.value)
    assertEquals(0, heard.subscriptionCount.value)
    assertEquals(null, copilotRepository.allMessagesByBook.value[book.id])
  }

  @Test
  fun `hearing nothing turns the mic off and starts no countdown`() = scope.runTest {
    every { speechInputController.dictate() } returns emptyFlow()

    viewModel.onAskClick()
    runCurrent()

    assertEquals(true, viewModel.feedVisible.value)
    assertEquals(ChatInputViewState(), viewModel.chatInput.value)
  }

  @Test
  fun `closing the chat stops the mic and a pending send`() = scope.runTest {
    every { speechInputController.dictate() } returns flowOf(DictationEvent.Final("who is Carl"))

    viewModel.onAskClick()
    runCurrent()
    viewModel.onFeedDismiss()
    advanceTimeBy(ChatInputViewState.AUTO_SEND_DELAY_MS * 2)
    runCurrent()

    assertEquals(false, viewModel.feedVisible.value)
    assertEquals(false, viewModel.chatInput.value.autoSendPending)
    assertEquals(null, copilotRepository.allMessagesByBook.value[book.id])
  }

  @Test
  fun `the play button reads a reply aloud, and a second tap stops it`() = scope.runTest {
    val reply = CoPilotMessage(id = "a1", role = CoPilotMessage.Role.CoPilot, text = "Carl is a crawler.", timestampMs = 1L)

    viewModel.onMessagePlayClick(reply)
    verify(exactly = 1) { messageSpeaker.speak("a1", "Carl is a crawler.") }

    viewModel.onMessagePlayClick(reply)
    assertEquals(null, spokenMessage.value)
    verify(exactly = 1) { messageSpeaker.speak(any(), any()) }
  }

  @Test
  fun `tapping play on another reply switches to it`() = scope.runTest {
    val first = CoPilotMessage(id = "a1", role = CoPilotMessage.Role.CoPilot, text = "First.", timestampMs = 1L)
    val second = CoPilotMessage(id = "a2", role = CoPilotMessage.Role.CoPilot, text = "Second.", timestampMs = 2L)

    viewModel.onMessagePlayClick(first)
    viewModel.onMessagePlayClick(second)

    verifyOrder {
      messageSpeaker.speak("a1", "First.")
      messageSpeaker.speak("a2", "Second.")
    }
    assertEquals(SpokenMessage("a2", audible = false), spokenMessage.value)
  }

  @Test
  fun `leaving the chat stops the reply being read`() = scope.runTest {
    val reply = CoPilotMessage(id = "a1", role = CoPilotMessage.Role.CoPilot, text = "Carl is a crawler.", timestampMs = 1L)
    viewModel.onFeedClick()
    viewModel.onMessagePlayClick(reply)

    viewModel.onFeedDismiss()

    assertEquals(null, spokenMessage.value)
    assertEquals(false, viewModel.feedVisible.value)
  }

  @Test
  fun `turning the mic on stops the reply being read, and reading a reply turns the mic off`() = scope.runTest {
    val heard = MutableSharedFlow<DictationEvent>()
    every { speechInputController.dictate() } returns heard
    val reply = CoPilotMessage(id = "a1", role = CoPilotMessage.Role.CoPilot, text = "Carl is a crawler.", timestampMs = 1L)

    viewModel.onMessagePlayClick(reply)
    viewModel.onChatMicClick()
    runCurrent()
    assertEquals(null, spokenMessage.value)
    assertEquals(true, viewModel.chatInput.value.isListening)

    viewModel.onMessagePlayClick(reply)
    runCurrent()
    assertEquals(false, viewModel.chatInput.value.isListening)
    assertEquals(SpokenMessage("a1", audible = false), spokenMessage.value)
    assertEquals(0, heard.subscriptionCount.value)
  }

  @Test
  fun `Snip button runs Snip and Synthesize, not a plain bookmark`() = scope.runTest {
    coEvery { copilotPipeline.snip(book.id) } just Runs

    viewModel.onSnipClick()
    yield()

    coVerify { copilotPipeline.snip(book.id) }
    coVerify(exactly = 0) { bookmarkRepository.addBookmarkAtBookPosition(any(), any(), any()) }
  }

  @Test
  fun `one snip shows one toast effect and a sheet that fills in when the text is ready`() = scope.runTest {
    val effects = mutableListOf<BookPlayViewEffect>()
    backgroundScope.launch { viewModel.collectSnipEvents() }
    backgroundScope.launch { viewModel.viewEffects.collect { effects += it } }
    runCurrent()

    copilotRepository.emitSnipEvent(SnipEvent.Started(drawsPicture = true))
    runCurrent()
    assertEquals(listOf<BookPlayViewEffect>(BookPlayViewEffect.SnipSaved), effects)
    assertEquals(SnipSheetViewState.Loading(drawsPicture = true), viewModel.snipSheet.value)

    copilotRepository.emitSnipEvent(SnipEvent.Ready("Carl's blade froze mid-swing.", "m1"))
    runCurrent()
    assertEquals(SnipSheetViewState.Ready("Carl's blade froze mid-swing.", "m1"), viewModel.snipSheet.value)
    assertEquals(1, effects.size)
  }

  @Test
  fun `Snip button itself emits no toast, only the shared stream does`() = scope.runTest {
    coEvery { copilotPipeline.snip(book.id) } just Runs
    val effects = mutableListOf<BookPlayViewEffect>()
    backgroundScope.launch { viewModel.collectSnipEvents() }
    backgroundScope.launch { viewModel.viewEffects.collect { effects += it } }
    runCurrent()

    viewModel.onSnipClick()
    runCurrent()

    assertEquals(emptyList(), effects)
  }

  @Test
  fun `snips captured while nothing is collecting are dropped`() = scope.runTest {
    val effects = mutableListOf<BookPlayViewEffect>()
    copilotRepository.emitSnipEvent(SnipEvent.Started(drawsPicture = true))
    copilotRepository.emitSnipEvent(SnipEvent.Ready("missed", "m0"))

    backgroundScope.launch { viewModel.collectSnipEvents() }
    backgroundScope.launch { viewModel.viewEffects.collect { effects += it } }
    runCurrent()

    assertEquals(emptyList(), effects)
    assertEquals(null, viewModel.snipSheet.value)
  }

  @Test
  fun `a failed snip closes the loading sheet`() = scope.runTest {
    backgroundScope.launch { viewModel.collectSnipEvents() }
    runCurrent()

    copilotRepository.emitSnipEvent(SnipEvent.Started(drawsPicture = true))
    runCurrent()
    assertEquals(SnipSheetViewState.Loading(drawsPicture = true), viewModel.snipSheet.value)

    copilotRepository.emitSnipEvent(SnipEvent.Failed)
    runCurrent()
    assertEquals(null, viewModel.snipSheet.value)
  }

  @Test
  fun `a dismissed sheet is not reopened when the text arrives`() = scope.runTest {
    backgroundScope.launch { viewModel.collectSnipEvents() }
    runCurrent()

    copilotRepository.emitSnipEvent(SnipEvent.Started(drawsPicture = true))
    runCurrent()
    viewModel.onSnipSheetDismiss()
    copilotRepository.emitSnipEvent(SnipEvent.Ready("late", "m2"))
    runCurrent()

    assertEquals(null, viewModel.snipSheet.value)
  }

  @Test
  fun `leaving the screen while capturing clears the loading sheet`() = scope.runTest {
    val collector = backgroundScope.launch { viewModel.collectSnipEvents() }
    runCurrent()
    copilotRepository.emitSnipEvent(SnipEvent.Started(drawsPicture = true))
    runCurrent()

    collector.cancelAndJoin()

    assertEquals(null, viewModel.snipSheet.value)
  }

  @Test
  fun `Feed view state updates live when another source writes to the shared repository`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.copilotMessages()
    }.test {
      assertEquals(expected = emptyList(), actual = awaitItem())

      // Simulates Open Mic / Snip & Synthesize, which write to CoPilotRepository
      // directly rather than through onChatSend.
      copilotRepository.addMessage(
        book.id,
        CoPilotMessage(
          id = "snip-1",
          role = CoPilotMessage.Role.CoPilot,
          text = "Snip summary from a Bluetooth action.",
          timestampMs = 0L,
          isVisualPriority = true,
        ),
      )

      val withSnip = awaitItem()
      assertEquals(expected = 1, actual = withSnip.size)
      assertEquals(expected = "Snip summary from a Bluetooth action.", actual = withSnip.single().text)
    }
  }

  @Test
  fun `the open card follows the whole-book position, live when it is available`() = scope.runTest {
    val liveFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel = viewModel(experimentalPlaybackPersistence = true, livePlaybackFlow = liveFlow)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.cardPositionMs(openedAtMs = 1L)
    }.test {
      assertEquals(expected = 1L, actual = awaitItem())
      assertEquals(expected = bookPositionMs, actual = awaitItem())

      liveFlow.value = LivePlaybackState(
        bookId = book.id,
        chapterId = book.chapters.first().id,
        positionMs = 3.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      assertEquals(expected = 3.minutes.inWholeMilliseconds, actual = awaitItem())
    }
  }

  private fun viewModel(
    book: Book = this.book,
    experimentalPlaybackPersistence: Boolean = false,
    kioskMode: Boolean = false,
    livePlaybackFlow: MutableStateFlow<LivePlaybackState?> = MutableStateFlow(null),
    playStateFlow: MutableStateFlow<PlayStateManager.PlayState> = MutableStateFlow(PlayStateManager.PlayState.Paused),
    copilotRepository: CoPilotRepository = CoPilotRepository(),
  ): BookPlayViewModel {
    return BookPlayViewModel(
      bookRepository = mockk {
        coEvery { get(book.id) } returns book
        every { flow(book.id) } returns MutableStateFlow(book)
      },
      currentBookResolver = currentBookResolver,
      player = mockk {
        every { pauseIfCurrentBookDifferentFrom(book.id) } just Runs
        every { livePlaybackStateFlow(book.id) } returns livePlaybackFlow
      },
      sleepTimer = sleepTimer,
      playStateManager = mockk {
        every { this@mockk.playStateFlow } returns playStateFlow
        every { playState } returns playStateFlow.value
      },
      currentBookStoreId = MemoryDataStore(null),
      navigator = mockk(),
      bookmarkRepository = mockk(),
      volumeGainFormatter = mockk(),
      batteryOptimization = mockk(),
      sleepTimerPreferenceStore = sleepTimerDataStore,
      seekTimeStore = MemoryDataStore(15),
      seekForwardTimeStore = MemoryDataStore(30),
      bookId = book.id,
      dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
      experimentalPlaybackPersistenceFeatureFlag = MemoryFeatureFlag(experimentalPlaybackPersistence),
      kioskModeFeatureFlag = MemoryFeatureFlag(kioskMode),
      copilotRepository = copilotRepository,
      copilotPipeline = mockk(),
      speechInputController = mockk(),
      messageSpeaker = mockk(relaxed = true),
      stores = stores,
    )
  }
}

private fun book(
  name: String = "TestBook",
  lastPlayedAtMillis: Long = 0L,
  addedAtMillis: Long = 0L,
): Book {
  val chapters = listOf(
    chapter(),
    chapter(),
  )
  return Book(
    content = BookContent(
      author = Uuid.random().toString(),
      name = name,
      positionInChapter = 2.5.minutes.inWholeMilliseconds,
      playbackSpeed = 1F,
      addedAt = Instant.ofEpochMilli(addedAtMillis),
      chapters = chapters.map { it.id },
      cover = null,
      currentChapter = chapters[1].id,
      isActive = true,
      lastPlayedAt = Instant.ofEpochMilli(lastPlayedAtMillis),
      skipSilence = false,
      id = BookId(Uuid.random().toString()),
      gain = 0F,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    ),
    chapters = chapters,
  )
}

private fun chapter(): Chapter {
  return Chapter(
    id = ChapterId("http://${Uuid.random()}"),
    duration = 5.minutes.inWholeMilliseconds,
    fileLastModified = Instant.EPOCH,
    markData = listOf(
      MarkData(startMs = 0L, name = "Chapter Start"),
      MarkData(startMs = 2.minutes.inWholeMilliseconds, name = "Middle Section"),
      MarkData(startMs = 4.minutes.inWholeMilliseconds, name = "Final Section"),
    ),
    name = "name",
    fileSize = 0,
  )
}
