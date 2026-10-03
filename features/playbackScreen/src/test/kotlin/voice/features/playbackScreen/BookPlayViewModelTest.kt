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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import voice.core.common.DispatcherProvider
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
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.copilot.SnipEvent
import voice.core.xray.XRayEntityInfo
import voice.core.xray.XRayManifest
import voice.core.xray.card.PlayerCardData
import voice.core.xray.card.PlayerCardEntry
import voice.core.xray.card.PlayerCardFields
import voice.core.xray.card.PlayerCardRepository
import voice.core.xray.card.PlayerCardSet
import voice.core.xray.card.composeAt
import voice.features.sleepTimer.SleepTimerViewState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
  private val copilotPipeline = mockk<CoPilotPipeline>()
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
    navigator = mockk(),
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
    speechInputController = mockk(),
    xrayRepository = mockk {
      coEvery { manifestFor(any()) } returns null
    },
    playerCardRepository = mockk {
      coEvery { cardsFor(any()) } returns null
    },
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

    viewModel.onSendFeedMessage("who is Carl?")
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
  fun `bookmark long-click adds a plain bookmark, not Snip and Synthesize`() = scope.runTest {
    viewModel.onBookmarkLongClick()
    yield()

    coVerify { bookmarkRepository.addBookmarkAtBookPosition(book, any(), any()) }
    coVerify(exactly = 0) { copilotPipeline.snip(any()) }
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

    copilotRepository.emitSnipEvent(SnipEvent.Started)
    runCurrent()
    assertEquals(listOf<BookPlayViewEffect>(BookPlayViewEffect.SnipSaved), effects)
    assertEquals(SnipSheetViewState.Loading, viewModel.snipSheet.value)

    copilotRepository.emitSnipEvent(SnipEvent.Ready("Carl's blade froze mid-swing."))
    runCurrent()
    assertEquals(SnipSheetViewState.Ready("Carl's blade froze mid-swing."), viewModel.snipSheet.value)
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
    copilotRepository.emitSnipEvent(SnipEvent.Started)
    copilotRepository.emitSnipEvent(SnipEvent.Ready("missed"))

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

    copilotRepository.emitSnipEvent(SnipEvent.Started)
    runCurrent()
    assertEquals(SnipSheetViewState.Loading, viewModel.snipSheet.value)

    copilotRepository.emitSnipEvent(SnipEvent.Failed)
    runCurrent()
    assertEquals(null, viewModel.snipSheet.value)
  }

  @Test
  fun `a dismissed sheet is not reopened when the text arrives`() = scope.runTest {
    backgroundScope.launch { viewModel.collectSnipEvents() }
    runCurrent()

    copilotRepository.emitSnipEvent(SnipEvent.Started)
    runCurrent()
    viewModel.onSnipSheetDismiss()
    copilotRepository.emitSnipEvent(SnipEvent.Ready("late"))
    runCurrent()

    assertEquals(null, viewModel.snipSheet.value)
  }

  @Test
  fun `leaving the screen while capturing clears the loading sheet`() = scope.runTest {
    val collector = backgroundScope.launch { viewModel.collectSnipEvents() }
    runCurrent()
    copilotRepository.emitSnipEvent(SnipEvent.Started)
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
      // directly rather than through onSendFeedMessage.
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
  fun `tapping a chip with card data opens the holo card composed at the playback position`() = scope.runTest {
    val viewModel = viewModel(
      xrayManifest = carlManifest,
      playerCards = PlayerCardSet(
        cards = mapOf(
          "carl" to PlayerCardData(
            id = "carl",
            entries = listOf(
              PlayerCardEntry(ms = 1.minutes.inWholeMilliseconds, field = PlayerCardFields.LEVEL, value = "3"),
              PlayerCardEntry(ms = 2.minutes.inWholeMilliseconds, field = PlayerCardFields.LEVEL, value = "4"),
              PlayerCardEntry(ms = 10.minutes.inWholeMilliseconds, field = PlayerCardFields.LEVEL, value = "9"),
            ),
          ),
        ),
        imageFolder = null,
      ),
    )

    viewModel.onXRayChipClick("carl")
    yield()

    val dialog = assertIs<BookPlayDialogViewState.XRayCardDialog>(viewModel.dialogState.value)
    assertEquals(expected = "Carl", actual = dialog.name)
    assertEquals(expected = 4, actual = dialog.card.level)
    assertEquals(expected = null, actual = dialog.lookImage)
  }

  @Test
  fun `tapping a chip keeps the bottom sheet when the entity has no card`() = scope.runTest {
    val viewModel = viewModel(
      xrayManifest = carlManifest,
      playerCards = PlayerCardSet(cards = emptyMap(), imageFolder = null),
    )

    viewModel.onXRayChipClick("carl")
    yield()

    assertEquals(
      expected = BookPlayDialogViewState.XRayEntityDialog(title = "Carl", description = "A crawler.", image = null),
      actual = viewModel.dialogState.value,
    )
  }

  @Test
  fun `tapping a chip keeps the bottom sheet when nothing on the card is revealed yet`() = scope.runTest {
    val viewModel = viewModel(
      xrayManifest = carlManifest,
      playerCards = PlayerCardSet(
        cards = mapOf(
          "carl" to PlayerCardData(
            id = "carl",
            entries = listOf(PlayerCardEntry(ms = 1.hours.inWholeMilliseconds, field = PlayerCardFields.LEVEL, value = "9")),
          ),
        ),
        imageFolder = null,
      ),
    )

    viewModel.onXRayChipClick("carl")
    yield()

    assertIs<BookPlayDialogViewState.XRayEntityDialog>(viewModel.dialogState.value)
  }

  @Test
  fun `tapping a chip keeps the bottom sheet when the book has no cards file`() = scope.runTest {
    val viewModel = viewModel(xrayManifest = carlManifest, playerCards = null)

    viewModel.onXRayChipClick("carl")
    yield()

    assertIs<BookPlayDialogViewState.XRayEntityDialog>(viewModel.dialogState.value)
  }

  @Test
  fun `the open card follows the live playback position when it is available`() = scope.runTest {
    val liveFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel = viewModel(experimentalPlaybackPersistence = true, livePlaybackFlow = liveFlow)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.cardPositionMs(openedAtMs = 1L)
    }.test {
      assertEquals(expected = 1L, actual = awaitItem())
      assertEquals(expected = book.content.positionInChapter, actual = awaitItem())

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

  @Test
  fun `the open card dialog keeps the raw card so it can be composed at any position`() = scope.runTest {
    val data = PlayerCardData(
      id = "carl",
      entries = listOf(
        PlayerCardEntry(ms = 1.minutes.inWholeMilliseconds, field = PlayerCardFields.LEVEL, value = "3"),
        PlayerCardEntry(ms = 10.minutes.inWholeMilliseconds, field = PlayerCardFields.LEVEL, value = "9"),
      ),
    )
    val viewModel = viewModel(xrayManifest = carlManifest, playerCards = PlayerCardSet(mapOf("carl" to data), imageFolder = null))

    viewModel.onXRayChipClick("carl")
    yield()

    val dialog = assertIs<BookPlayDialogViewState.XRayCardDialog>(viewModel.dialogState.value)
    assertEquals(expected = 2.5.minutes.inWholeMilliseconds, actual = dialog.openedAtMs)
    assertEquals(expected = 3, actual = dialog.card.level)
    assertEquals(expected = 9, actual = dialog.data.composeAt(11.minutes.inWholeMilliseconds).level)
  }

  private val carlManifest = XRayManifest(
    entities = listOf(XRayEntityInfo(id = "carl", title = "Carl", description = "A crawler.")),
    timeline = emptyList(),
  )

  private fun viewModel(
    book: Book = this.book,
    experimentalPlaybackPersistence: Boolean = false,
    kioskMode: Boolean = false,
    livePlaybackFlow: MutableStateFlow<LivePlaybackState?> = MutableStateFlow(null),
    playStateFlow: MutableStateFlow<PlayStateManager.PlayState> = MutableStateFlow(PlayStateManager.PlayState.Paused),
    xrayManifest: XRayManifest? = null,
    playerCards: PlayerCardSet? = null,
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
      copilotRepository = CoPilotRepository(),
      copilotPipeline = mockk(),
      speechInputController = mockk(),
      xrayRepository = mockk {
        coEvery { manifestFor(any()) } returns xrayManifest
      },
      playerCardRepository = mockk<PlayerCardRepository> {
        coEvery { cardsFor(any()) } returns playerCards
      },
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
