package voice.features.playbackScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.KioskModeDemoData
import voice.core.data.durationMs
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SeekForwardTimeStore
import voice.core.data.store.SeekTimeStore
import voice.core.data.store.SleepTimerPreferenceStore
import voice.core.featureflag.ExperimentalPlaybackPersistenceQualifier
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.KioskModeFeatureFlagQualifier
import voice.core.logging.api.Logger
import voice.core.playback.CurrentBookResolver
import voice.core.playback.PlayerController
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.core.ui.formatTime
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.xray.XRayEntityInfo
import voice.core.xray.XRayManifest
import voice.core.xray.XRayRepository
import voice.core.xray.activeEntities
import voice.core.xray.card.PlayerCardRepository
import voice.core.xray.card.composeAt
import voice.features.playbackScreen.batteryOptimization.BatteryOptimization
import voice.features.playbackScreen.copilot.SpeechInputController
import voice.features.sleepTimer.SleepTimerViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@AssistedInject
class BookPlayViewModel(
  private val bookRepository: BookRepository,
  private val currentBookResolver: CurrentBookResolver,
  private val player: PlayerController,
  private val sleepTimer: SleepTimer,
  private val playStateManager: PlayStateManager,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val navigator: Navigator,
  private val bookmarkRepository: BookmarkRepo,
  private val volumeGainFormatter: VolumeGainFormatter,
  private val batteryOptimization: BatteryOptimization,
  private val dispatcherProvider: DispatcherProvider,
  @SleepTimerPreferenceStore
  private val sleepTimerPreferenceStore: DataStore<SleepTimerPreference>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  @SeekForwardTimeStore
  private val seekForwardTimeStore: DataStore<Int>,
  @ExperimentalPlaybackPersistenceQualifier
  private val experimentalPlaybackPersistenceFeatureFlag: FeatureFlag<Boolean>,
  @KioskModeFeatureFlagQualifier
  private val kioskModeFeatureFlag: FeatureFlag<Boolean>,
  private val copilotRepository: CoPilotRepository,
  private val copilotPipeline: CoPilotPipeline,
  private val speechInputController: SpeechInputController,
  private val xrayRepository: XRayRepository,
  private val playerCardRepository: PlayerCardRepository,
  @Assisted
  private val bookId: BookId,
) {

  private val scope = MainScope(dispatcherProvider)

  internal val viewEffects: Flow<BookPlayViewEffect>
    field = MutableSharedFlow<BookPlayViewEffect>(extraBufferCapacity = 1)

  internal val dialogState: State<BookPlayDialogViewState?>
    field = mutableStateOf<BookPlayDialogViewState?>(null)

  internal val feedVisible: State<Boolean>
    field = mutableStateOf(false)

  internal val isThinking: State<Boolean>
    field = mutableStateOf(false)

  init {
    scope.launch {
      player.pauseIfCurrentBookDifferentFrom(bookId)
      currentBookStoreId.updateData { bookId }
    }
  }

  @Composable
  fun viewState(): BookPlayViewState? {
    val kioskMode = remember { kioskModeFeatureFlag.get() }
    if (kioskMode) return kioskModeViewState()

    val persistedBook = remember(bookId) {
      bookRepository.flow(bookId).filterNotNull()
    }.collectAsState(initial = null).value ?: return null

    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState = if (experimentalPlaybackPersistence) {
      remember(bookId) { player.livePlaybackStateFlow(bookId) }
        .collectAsState(null).value
    } else {
      null
    }
    val managerPlayState by remember {
      playStateManager.playStateFlow
    }.collectAsState()

    val book = if (livePlaybackState != null) {
      persistedBook.overlay(livePlaybackState)
    } else {
      persistedBook
    }
    val isPlaying = livePlaybackState?.isPlaying ?: (managerPlayState == PlayStateManager.PlayState.Playing)

    val currentMark = book.currentChapter.markForPosition(book.content.positionInChapter)
    val positionInCurrentMark = if (isPlaying && currentMark.durationMs > 0) {
      val relativePosition = book.content.positionInChapter - currentMark.startMs
      relativePosition.coerceIn(0L, currentMark.durationMs)
    } else {
      book.content.positionInChapter - currentMark.startMs
    }

    val sleepTime = remember { sleepTimer.state }.collectAsState().value
    val hasMoreThanOneChapter = book.chapters.sumOf { it.chapterMarks.count() } > 1
    val skipBackSeconds by remember { seekTimeStore.data }.collectAsState(initial = 15)
    val skipForwardSeconds by remember { seekForwardTimeStore.data }.collectAsState(initial = 30)
    val xrayManifest by produceState<XRayManifest?>(initialValue = null, book.currentChapter.id) {
      value = xrayRepository.manifestFor(book)
    }
    val xrayChips = xrayManifest?.activeEntities(book.content.positionInChapter)?.map {
      BookPlayViewState.XRayChipViewState(id = it.id, label = it.title)
    }.orEmpty()
    return BookPlayViewState(
      sleepTimerState = sleepTime.toViewState(),
      playing = isPlaying,
      title = book.content.name,
      showPreviousNextButtons = hasMoreThanOneChapter,
      chapterName = currentMark.name.takeIf { hasMoreThanOneChapter },
      duration = currentMark.durationMs.milliseconds,
      playedTime = positionInCurrentMark.milliseconds,
      cover = book.content.coverUrl,
      skipSilence = book.content.skipSilence,
      skipBackSeconds = skipBackSeconds,
      skipForwardSeconds = skipForwardSeconds,
      xrayChips = xrayChips,
    )
  }

  private fun kioskModeViewState(): BookPlayViewState {
    val currentlyPlaying = KioskModeDemoData.currentlyPlaying
    val book = KioskModeDemoData.currentlyPlayingBook
    return BookPlayViewState(
      sleepTimerState = BookPlayViewState.SleepTimerViewState.Disabled,
      playing = true,
      title = currentlyPlaying.title,
      showPreviousNextButtons = true,
      chapterName = currentlyPlaying.chapter,
      duration = 14.hours + 27.minutes,
      playedTime = 10.hours + 24.minutes,
      cover = book.coverUrl,
      skipSilence = false,
      skipBackSeconds = 15,
      skipForwardSeconds = 30,
      xrayChips = emptyList(),
    )
  }

  fun dismissDialog() {
    Logger.d("dismissDialog")
    dialogState.value = null
  }

  fun incrementSleepTime() {
    updateSleepTimeViewState {
      val customTime = it.customSleepTime
      val newTime = customTime + 1
      sleepTimerPreferenceStore.updateData { preference -> preference.copy(duration = newTime.minutes) }
      SleepTimerViewState(newTime)
    }
  }

  fun decrementSleepTime() {
    updateSleepTimeViewState {
      val customTime = it.customSleepTime
      val newTime = (customTime - 1).coerceAtLeast(1)
      sleepTimerPreferenceStore.updateData { preference ->
        preference.copy(duration = newTime.minutes)
      }
      SleepTimerViewState(newTime)
    }
  }

  fun onAcceptSleepTime(time: Int) {
    updateSleepTimeViewState {
      val book = currentBook() ?: return@updateSleepTimeViewState null
      scope.launch {
        bookmarkRepository.addBookmarkAtBookPosition(
          book = book,
          setBySleepTimer = true,
          title = null,
        )
      }
      sleepTimer.enable(TimedWithDuration(time.minutes))
      null
    }
  }

  fun onAcceptSleepAtEndOfChapter() {
    updateSleepTimeViewState {
      sleepTimer.enable(SleepTimerMode.EndOfChapter)
      null
    }
  }

  private fun updateSleepTimeViewState(update: suspend (SleepTimerViewState) -> SleepTimerViewState?) {
    scope.launch {
      val current = dialogState.value
      val updated: SleepTimerViewState? = if (current is BookPlayDialogViewState.SleepTimer) {
        update(current.viewState)
      } else {
        update(SleepTimerViewState(sleepTimerPreferenceStore.data.first().duration.inWholeMinutes.toInt()))
      }
      dialogState.value = updated?.let(BookPlayDialogViewState::SleepTimer)
    }
  }

  fun onPlaybackSpeedChanged(speed: Float) {
    dialogState.value = BookPlayDialogViewState.SpeedDialog(speed)
    player.setSpeed(speed)
  }

  fun onVolumeGainChanged(gain: Decibel) {
    dialogState.value = volumeGainDialogViewState(gain)
    player.setGain(gain)
  }

  fun next() {
    player.next()
  }

  fun previous() {
    player.previous()
  }

  fun playPause() {
    if (playStateManager.playState != PlayStateManager.PlayState.Playing) {
      scope.launch {
        if (batteryOptimization.shouldRequest()) {
          viewEffects.tryEmit(BookPlayViewEffect.RequestIgnoreBatteryOptimization)
          batteryOptimization.onBatteryOptimizationsRequested()
        }
      }
    }
    player.playPause()
  }

  fun rewind() {
    player.rewind()
  }

  fun fastForward() {
    player.fastForward()
  }

  fun onCloseClick() {
    navigator.goBack()
  }

  fun onCurrentChapterClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      dialogState.value = BookPlayDialogViewState.SelectChapterDialog(
        items = book.chapters.flatMapIndexed { chapterIndex, chapter ->
          chapter.chapterMarks.mapIndexed { markIndex, chapterMark ->
            val previousChapters = book.chapters.take(chapterIndex)
            BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
              number = previousChapters.sumOf { it.chapterMarks.count() } + markIndex + 1,
              name = chapterMark.name ?: "",
              active = chapterMark == book.currentMark && chapter == book.currentChapter,
              time = formatTime(previousChapters.sumOf { it.duration } + chapterMark.startMs),
            )
          }
        },
      )
    }
  }

  fun onChapterClick(number: Int) {
    scope.launch {
      val book = currentBook() ?: return@launch
      var currentIndex = -1
      book.chapters.forEach { chapter ->
        chapter.chapterMarks.forEach { mark ->
          currentIndex++
          if (currentIndex == number - 1) {
            player.setPosition(mark.startMs, chapter.id)
            dialogState.value = null
            return@launch
          }
        }
      }
    }
  }

  fun onPlaybackSpeedIconClick() {
    scope.launch {
      val playbackSpeed = currentBook()?.content?.playbackSpeed ?: return@launch
      dialogState.value = BookPlayDialogViewState.SpeedDialog(playbackSpeed)
    }
  }

  fun onVolumeGainIconClick() {
    scope.launch {
      val content = currentBook()?.content ?: return@launch
      dialogState.value = volumeGainDialogViewState(Decibel(content.gain))
    }
  }

  private fun volumeGainDialogViewState(gain: Decibel): BookPlayDialogViewState.VolumeGainDialog {
    return BookPlayDialogViewState.VolumeGainDialog(
      gain = gain,
      maxGain = VolumeGain.MAX_GAIN,
      valueFormatted = volumeGainFormatter.format(gain),
    )
  }

  fun onBookmarkClick() {
    navigator.goTo(Destination.Bookmarks(bookId))
  }

  fun onXRayChipClick(id: String) {
    scope.launch {
      val book = currentBook() ?: return@launch
      val entity = xrayRepository.manifestFor(book)?.entities?.firstOrNull { it.id == id } ?: return@launch
      dialogState.value = playerCardDialog(book, entity) ?: BookPlayDialogViewState.XRayEntityDialog(
        title = entity.title,
        description = entity.description,
        image = entity.image,
      )
    }
  }

  private suspend fun playerCardDialog(
    book: Book,
    entity: XRayEntityInfo,
  ): BookPlayDialogViewState.XRayCardDialog? = withContext(dispatcherProvider.io) {
    val cards = playerCardRepository.cardsFor(book) ?: return@withContext null
    val data = cards.card(entity.id) ?: return@withContext null
    val positionMs = book.content.positionInChapter
    if (!data.composeAt(positionMs).hasRevealedData) return@withContext null
    BookPlayDialogViewState.XRayCardDialog(
      name = entity.title,
      data = data,
      imageUris = data.entries
        .mapNotNull { it.image }
        .distinct()
        .mapNotNull { path -> cards.imageUri(path)?.let { path to it.toString() } }
        .toMap(),
      openedAtMs = positionMs,
    )
  }

  /** The playback position the open holo card follows, so stats and looks reveal as the book plays. */
  @Composable
  fun cardPositionMs(openedAtMs: Long): Long {
    val persisted by remember(bookId) { bookRepository.flow(bookId).filterNotNull() }.collectAsState(initial = null)
    val live = if (experimentalPlaybackPersistenceFeatureFlag.get()) {
      remember(bookId) { player.livePlaybackStateFlow(bookId) }.collectAsState(null).value
    } else {
      null
    }
    return live?.positionMs ?: persisted?.content?.positionInChapter ?: openedAtMs
  }

  @Composable
  fun copilotMessages(): List<CoPilotMessage> {
    val allMessages by remember { copilotRepository.allMessagesByBook }.collectAsState()
    return allMessages[bookId].orEmpty()
  }

  fun onFeedClick() {
    feedVisible.value = true
  }

  fun onFeedDismiss() {
    feedVisible.value = false
  }

  fun onSendFeedMessage(text: String) {
    runCoPilotExchange(userText = text) { copilotPipeline.ask(bookId, text) }
  }

  fun onAskClick() {
    scope.launch {
      val question = speechInputController.listen().getOrElse { error ->
        Logger.w("Speech capture failed: ${error.message}")
        return@launch
      }
      feedVisible.value = true
      runCoPilotExchange(userText = question) { copilotPipeline.ask(bookId, question) }
    }
  }

  fun onCatchMeUpClick() {
    feedVisible.value = true
    runCoPilotExchange(userText = "Catch me up on what's happened recently.") {
      copilotPipeline.catchMeUp(bookId)
    }
  }

  fun onSnipClick() {
    scope.launch {
      copilotPipeline.snip(bookId)
      viewEffects.tryEmit(BookPlayViewEffect.SnipSaved)
    }
  }

  fun onSnipTimestampClick(chapterId: ChapterId, positionInChapterMs: Long) {
    player.setPosition(positionInChapterMs, chapterId)
    feedVisible.value = false
  }

  private fun runCoPilotExchange(userText: String, answer: suspend () -> String) {
    scope.launch {
      copilotRepository.addMessage(
        bookId,
        CoPilotMessage(
          id = UUID.randomUUID().toString(),
          role = CoPilotMessage.Role.User,
          text = userText,
          timestampMs = System.currentTimeMillis(),
        ),
      )
      isThinking.value = true
      val result = answer()
      isThinking.value = false
      copilotRepository.addMessage(
        bookId,
        CoPilotMessage(
          id = UUID.randomUUID().toString(),
          role = CoPilotMessage.Role.CoPilot,
          text = result,
          timestampMs = System.currentTimeMillis(),
        ),
      )
    }
  }

  fun onBookmarkLongClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      bookmarkRepository.addBookmarkAtBookPosition(
        book = book,
        title = null,
        setBySleepTimer = false,
      )
      viewEffects.tryEmit(BookPlayViewEffect.BookmarkAdded)
    }
  }

  fun seekTo(position: Duration) {
    scope.launch {
      val book = currentBook() ?: return@launch
      val currentChapter = book.currentChapter
      val currentMark = currentChapter.markForPosition(book.content.positionInChapter)
      player.setPosition(currentMark.startMs + position.inWholeMilliseconds, currentChapter.id)
    }
  }

  fun toggleSleepTimer() {
    scope.launch {
      Logger.d("toggleSleepTimer while active=${sleepTimer.state.value}")
      if (sleepTimer.state.value.enabled) {
        sleepTimer.disable()
        dialogState.value = null
      } else {
        dialogState.value = BookPlayDialogViewState.SleepTimer(
          viewState = SleepTimerViewState(
            customSleepTime = sleepTimerPreferenceStore.data.first().duration.inWholeMinutes.toInt(),
          ),
        )
      }
    }
  }

  fun onBatteryOptimizationRequested() {
    navigator.goTo(Destination.BatteryOptimization)
  }

  fun toggleSkipSilence() {
    scope.launch {
      val skipSilence = currentBook()?.content?.skipSilence ?: return@launch
      player.skipSilence(!skipSilence)
    }
  }

  private suspend fun currentBook(): Book? {
    return currentBookResolver.book(bookId)
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): BookPlayViewModel
  }
}

private fun SleepTimerState.toViewState(): BookPlayViewState.SleepTimerViewState = when (this) {
  SleepTimerState.Disabled -> BookPlayViewState.SleepTimerViewState.Disabled
  is SleepTimerState.Enabled.WithDuration -> BookPlayViewState.SleepTimerViewState.Enabled.WithDuration(this.leftDuration)
  SleepTimerState.Enabled.WithEndOfChapter -> BookPlayViewState.SleepTimerViewState.Enabled.WithEndOfChapter
}
