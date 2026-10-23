package voice.features.playbackScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotPipeline
import voice.core.copilot.CoPilotRepository
import voice.core.companion.BookKind
import voice.core.companion.CompanionStores
import voice.core.companion.Extra
import voice.core.companion.activeAt
import voice.core.copilot.SnipEvent
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.KioskModeDemoData
import voice.core.data.durationMs
import voice.core.data.formatted
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.snipLocation
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
import voice.core.playback.speech.MessageSpeaker
import voice.core.playback.speech.SpokenMessage
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.core.ui.crawl.toStyle
import voice.core.ui.formatTime
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
  private val messageSpeaker: MessageSpeaker,
  private val stores: CompanionStores,
  @Assisted
  private val bookId: BookId,
) {

  private val scope = MainScope(dispatcherProvider)

  private var knownUnlockedExtras: Set<String>? = null

  internal val viewEffects: Flow<BookPlayViewEffect>
    field = MutableSharedFlow<BookPlayViewEffect>(extraBufferCapacity = 1)

  internal val dialogState: State<BookPlayDialogViewState?>
    field = mutableStateOf<BookPlayDialogViewState?>(null)

  internal val feedVisible: State<Boolean>
    field = mutableStateOf(false)

  internal val isThinking: State<Boolean>
    field = mutableStateOf(false)

  internal val chatInput: State<ChatInputViewState>
    field = mutableStateOf(ChatInputViewState())

  private var dictationJob: Job? = null
  private var autoSendJob: Job? = null

  internal val snipSheet: State<SnipSheetViewState?>
    field = mutableStateOf<SnipSheetViewState?>(null)

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
    val scene by remember(bookId) { stores.scene.flow(bookId) }.collectAsState(initial = null)
    val shelf by remember(bookId) { stores.shelf.flow(bookId) }.collectAsState(initial = null)
    val look by remember(bookId) { stores.look.flow(bookId) }.collectAsState(initial = null)
    val extras = shelf?.extras.orEmpty()
    LaunchedEffect(extras, book.position) { announceUnlockedExtras(extras, book.position) }
    val messagesByBook by remember { copilotRepository.allMessagesByBook }.collectAsState()
    val snipCount = messagesByBook[bookId].orEmpty().count { it.isSnip }
    val sceneChips = scene?.activeAt(book.position)?.map {
      BookPlayViewState.XRayChipViewState(id = it.id, label = it.name)
    }.orEmpty()
    val bookStyle = remember(look) { look?.toStyle() }
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
      xrayChips = sceneChips,
      bookStyle = bookStyle,
      playbackSpeed = book.content.playbackSpeed,
      inventoryCount = snipCount + extras.count { it.unlockAtMs <= book.position },
    )
  }

  /** The first Extras seen are the baseline, so only Extras reached after the screen opened get a toast. */
  private fun announceUnlockedExtras(
    extras: List<Extra>,
    bookMs: Long,
  ) {
    if (extras.isEmpty()) return
    val unlocked = extras.filter { it.unlockAtMs <= bookMs }.map { it.id }.toSet()
    val known = knownUnlockedExtras
    knownUnlockedExtras = known.orEmpty() + unlocked + extras.filter { it.unlockAtMs == 0L }.map { it.id }
    if (known != null && !known.containsAll(unlocked)) {
      viewEffects.tryEmit(BookPlayViewEffect.ExtraUnlocked)
    }
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

  fun onXRayChipClick(id: String) {
    scope.launch {
      val book = currentBook() ?: return@launch
      val index = stores.scene.get(bookId)
      val entry = index.entries.firstOrNull { it.id == id } ?: return@launch
      val extra = entry.extraId?.let { extraId -> stores.shelf.get(bookId).extras.firstOrNull { it.id == extraId } }
      dialogState.value = BookPlayDialogViewState.SceneCardDialog(
        name = entry.name,
        summary = entry.summary,
        notes = entry.notes,
        fiction = index.kind == BookKind.Fiction,
        extraId = extra?.takeIf { it.unlockAtMs <= book.position }?.id,
        extraTitle = extra?.title,
        openedAtMs = book.position,
      )
    }
  }

  fun onSceneCardExtraClick(extraId: String) {
    dialogState.value = null
    navigator.goTo(Destination.Extra(bookId, extraId))
  }

  /** The whole-book position an open card follows, so its notes reveal as the book plays. */
  @Composable
  fun cardPositionMs(openedAtMs: Long): Long {
    val persisted by remember(bookId) { bookRepository.flow(bookId).filterNotNull() }.collectAsState(initial = null)
    val live = if (experimentalPlaybackPersistenceFeatureFlag.get()) {
      remember(bookId) { player.livePlaybackStateFlow(bookId) }.collectAsState(null).value
    } else {
      null
    }
    val book = persisted ?: return openedAtMs
    return if (live != null) book.overlay(live).position else book.position
  }

  @Composable
  fun copilotMessages(): List<CoPilotMessage> {
    val allMessages by remember { copilotRepository.allMessagesByBook }.collectAsState()
    return allMessages[bookId].orEmpty()
  }

  @Composable
  fun snipLocationLabel(): (ChapterId, Long) -> String? {
    val chapters by remember(bookId) {
      bookRepository.flow(bookId).filterNotNull().map { it.chapters }
    }.collectAsState(initial = emptyList())
    return remember(chapters) {
      { chapterId, positionInChapterMs -> chapters.snipLocation(chapterId, positionInChapterMs)?.formatted() }
    }
  }

  fun onFeedClick() {
    feedVisible.value = true
  }

  fun onFeedDismiss() {
    stopDictation()
    cancelAutoSend()
    messageSpeaker.stop()
    feedVisible.value = false
  }

  /** The chat message being read aloud, if any, for its play button. */
  @Composable
  internal fun spokenMessage(): SpokenMessage? {
    return remember { messageSpeaker.speaking }.collectAsState().value
  }

  /** The play button beside a reply: reads it aloud, or stops it when it is the one being read. */
  fun onMessagePlayClick(message: CoPilotMessage) {
    if (messageSpeaker.speaking.value?.id == message.id) {
      messageSpeaker.stop()
    } else {
      // The microphone would only hear the reply being read.
      stopDictation()
      cancelAutoSend()
      messageSpeaker.speak(message.id, message.text)
    }
  }

  /** The player's Ask button: opens the chat already listening. Needs the microphone permission. */
  fun onAskClick() {
    feedVisible.value = true
    startDictation()
  }

  /** Toggles the microphone. Starting it needs the microphone permission. */
  fun onChatMicClick() {
    if (chatInput.value.isListening) stopDictation() else startDictation()
  }

  /** Touching the text box means the user wants to type: the mic and any pending send stop. */
  fun onChatInputTap() {
    stopDictation()
    cancelAutoSend()
  }

  fun onChatInputChange(text: String) {
    stopDictation()
    cancelAutoSend()
    chatInput.value = chatInput.value.copy(text = text)
  }

  fun onChatAutoSendCancel() {
    cancelAutoSend()
  }

  fun onChatSend() {
    val question = chatInput.value.text.trim()
    if (question.isEmpty() || isThinking.value) return
    stopDictation()
    cancelAutoSend()
    chatInput.value = ChatInputViewState()
    runCoPilotExchange(userText = question) { copilotPipeline.ask(bookId, question) }
  }

  private fun startDictation() {
    if (isThinking.value) return
    stopDictation()
    cancelAutoSend()
    messageSpeaker.stop()
    val typedBefore = chatInput.value.text.trim()
    chatInput.value = chatInput.value.copy(isListening = true)
    dictationJob = scope.launch {
      var heard = false
      speechInputController.dictate().collect { event ->
        heard = true
        val text = if (typedBefore.isEmpty()) event.text else "$typedBefore ${event.text}"
        chatInput.value = chatInput.value.copy(text = text)
      }
      // Only reached when the recognizer stopped listening by itself; a tap cancels this job instead.
      dictationJob = null
      chatInput.value = chatInput.value.copy(isListening = false)
      if (heard) startAutoSend()
    }
  }

  private fun stopDictation() {
    dictationJob?.cancel()
    dictationJob = null
    if (chatInput.value.isListening) chatInput.value = chatInput.value.copy(isListening = false)
  }

  private fun startAutoSend() {
    chatInput.value = chatInput.value.copy(autoSendPending = true)
    autoSendJob = scope.launch {
      delay(ChatInputViewState.AUTO_SEND_DELAY_MS)
      autoSendJob = null
      chatInput.value = chatInput.value.copy(autoSendPending = false)
      onChatSend()
    }
  }

  private fun cancelAutoSend() {
    autoSendJob?.cancel()
    autoSendJob = null
    if (chatInput.value.autoSendPending) chatInput.value = chatInput.value.copy(autoSendPending = false)
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
    }
  }

  /**
   * Run only while the screen is visible: snips captured while it isn't (e.g. from the headset
   * with the screen off) are dropped by the shared stream and never show a toast or sheet.
   */
  suspend fun collectSnipEvents() {
    try {
      copilotRepository.snipEvents.collect(::onSnipEvent)
    } finally {
      if (snipSheet.value is SnipSheetViewState.Loading) snipSheet.value = null
    }
  }

  private fun onSnipEvent(event: SnipEvent) {
    when (event) {
      is SnipEvent.Started -> {
        snipSheet.value = SnipSheetViewState.Loading(drawsPicture = event.drawsPicture)
        viewEffects.tryEmit(BookPlayViewEffect.SnipSaved)
      }
      is SnipEvent.Ready -> if (snipSheet.value is SnipSheetViewState.Loading) {
        snipSheet.value = SnipSheetViewState.Ready(event.text, event.messageId)
      }
      SnipEvent.Failed -> if (snipSheet.value is SnipSheetViewState.Loading) {
        snipSheet.value = null
      }
    }
  }

  fun onSnipSheetDismiss() {
    snipSheet.value = null
  }

  fun onSnipTimestampClick(
    chapterId: ChapterId,
    positionInChapterMs: Long,
  ) {
    player.setPosition(positionInChapterMs, chapterId)
    onFeedDismiss()
  }

  private fun runCoPilotExchange(
    userText: String,
    answer: suspend () -> String,
  ) {
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
