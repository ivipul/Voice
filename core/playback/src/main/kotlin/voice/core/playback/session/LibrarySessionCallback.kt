package voice.core.playback.session

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.playback.player.VoicePlayer
import voice.core.playback.session.search.BookSearchHandler
import voice.core.playback.session.search.BookSearchParser

@Inject
class LibrarySessionCallback(
  private val mediaItemProvider: MediaItemProvider,
  private val scope: CoroutineScope,
  private val player: VoicePlayer,
  private val bookSearchParser: BookSearchParser,
  private val bookSearchHandler: BookSearchHandler,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val bookRepository: BookRepository,
  private val copilotEngine: CoPilotEngine,
) : MediaLibrarySession.Callback {

  private var pendingHeadsetPressJob: Job? = null
  private var lastHeadsetPressKeyCode: Int? = null
  private var lastHeadsetPressAtMs: Long = 0L
  private var lastNextOrPreviousAtMs: Long = 0L

  // Intercepts headset NEXT/PREVIOUS to disambiguate single vs. double presses (Phase 3) and
  // dispatch the default co-pilot mapping (Phase 4). Consumes both so the default seek
  // behavior doesn't also fire underneath our own handling.
  override fun onMediaButtonEvent(
    session: MediaSession,
    controllerInfo: ControllerInfo,
    intent: Intent,
  ): Boolean {
    val keyEvent = intent.extractKeyEvent() ?: return super.onMediaButtonEvent(session, controllerInfo, intent)
    val keyCode = keyEvent.keyCode

    if (keyCode in PLAY_PAUSE_KEY_CODES) {
      // Every physical press delivers both ACTION_DOWN and ACTION_UP. Consume both ourselves:
      // Media3's default onMediaButtonEvent PLAY_PAUSE toggle doesn't reliably resume (observed
      // calling pause() again even when playWhenReady was already false), and forwarding the
      // paired ACTION_UP to it would immediately re-toggle whatever ACTION_DOWN just did.
      if (keyEvent.action == KeyEvent.ACTION_DOWN) {
        if (System.currentTimeMillis() - lastNextOrPreviousAtMs <= NEXT_PREVIOUS_ECHO_WINDOW_MS) {
          // Some Bluetooth peripherals (observed on Pixel Buds' double-tap-to-skip gesture)
          // send a PLAY key event automatically right after NEXT/PREVIOUS as their own
          // "keep playing" signal, not as a separate deliberate press. Without this, that
          // synthetic PLAY reaches us ~10ms later and immediately interrupts whichever
          // co-pilot mode NEXT/PREVIOUS just triggered, before the user hears anything.
          Logger.d("Ignoring PLAY_PAUSE echo shortly after NEXT/PREVIOUS")
        } else {
          val wasPlaying = player.playWhenReady
          Logger.d("PLAY_PAUSE toggling (playWhenReady=$wasPlaying -> ${!wasPlaying})")
          if (wasPlaying) {
            player.pause()
          } else {
            player.play()
          }
        }
      }
      return true
    }

    if (keyEvent.action != KeyEvent.ACTION_DOWN) {
      return super.onMediaButtonEvent(session, controllerInfo, intent)
    }
    if (keyCode != KeyEvent.KEYCODE_MEDIA_NEXT && keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
      return super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    val now = System.currentTimeMillis()
    val isDoublePress = keyCode == lastHeadsetPressKeyCode && now - lastHeadsetPressAtMs <= DOUBLE_PRESS_THRESHOLD_MS
    lastHeadsetPressKeyCode = keyCode
    lastHeadsetPressAtMs = now
    lastNextOrPreviousAtMs = now
    pendingHeadsetPressJob?.cancel()

    if (isDoublePress) {
      lastHeadsetPressKeyCode = null
      // Default mapping (Phase 4): Double NEXT = Auto-Identify, Double PREVIOUS = Catch-Me-Up.
      // Persisting a user-configurable mapping is follow-up work (see CoPilotSettingsScreen).
      if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
        triggerCoPilotMode(CoPilotMode.AutoIdentify)
      } else {
        triggerCoPilotMode(CoPilotMode.CatchMeUp)
      }
    } else {
      pendingHeadsetPressJob = scope.launch {
        delay(DOUBLE_PRESS_THRESHOLD_MS)
        // Default mapping (Phase 4): Single NEXT = Open Mic, Single PREVIOUS = Rewind (same
        // default/override as the on-screen skip-back button - see SeekTimeStore).
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
          triggerCoPilotMode(CoPilotMode.OpenMic)
        } else {
          copilotEngine.interruptIfActive()
          player.seekBack()
        }
      }
    }
    return true
  }

  /**
   * Every mapped button press interrupts whichever co-pilot mode is currently active (if any)
   * before starting its own action fresh, rather than queuing behind it - this is also how a
   * different mode's Single NEXT reads as "that's not what I wanted, ask me something else"
   * instead of just bailing out to the book.
   */
  private fun triggerCoPilotMode(mode: CoPilotMode) {
    copilotEngine.interruptIfActive()
    player.pause()
    copilotEngine.trigger(mode, onFinished = { player.play() })
  }

  private fun Intent.extractKeyEvent(): KeyEvent? =
    if (Build.VERSION.SDK_INT >= 33) {
      getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
      @Suppress("DEPRECATION")
      getParcelableExtra(Intent.EXTRA_KEY_EVENT)
    }

  override fun onAddMediaItems(
    mediaSession: MediaSession,
    controller: ControllerInfo,
    mediaItems: MutableList<MediaItem>,
  ): ListenableFuture<List<MediaItem>> {
    Logger.d("onAddMediaItems")
    return scope.future {
      mediaItems.map { item ->
        mediaItemProvider.item(item.mediaId) ?: item
      }
    }
  }

  override fun onSetMediaItems(
    mediaSession: MediaSession,
    controller: ControllerInfo,
    mediaItems: MutableList<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
  ): ListenableFuture<MediaItemsWithStartPosition> {
    Logger.d("onSetMediaItems(mediaItems.size=${mediaItems.size}, startIndex=$startIndex, startPosition=$startPositionMs)")
    val item = mediaItems.singleOrNull()
    return if (startIndex == C.INDEX_UNSET && startPositionMs == C.TIME_UNSET && item != null) {
      scope.future {
        onSetMediaItemsForSingleItem(item)
          ?: super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs).await()
      }
    } else {
      super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }
  }

  private suspend fun onSetMediaItemsForSingleItem(item: MediaItem): MediaItemsWithStartPosition? {
    val searchQuery = item.requestMetadata.searchQuery
    return if (searchQuery != null) {
      val search = bookSearchParser.parse(searchQuery, item.requestMetadata.extras)
      val searchResult = bookSearchHandler.handle(search) ?: return null
      currentBookStoreId.updateData { searchResult.id }
      mediaItemProvider.mediaItemsWithStartPosition(searchResult)
    } else {
      (item.mediaId.toMediaIdOrNull() as? MediaId.Book)?.let { bookId ->
        currentBookStoreId.updateData { bookId.id }
      }
      mediaItemProvider.mediaItemsWithStartPosition(item.mediaId)
    }
  }

  override fun onGetLibraryRoot(
    session: MediaLibrarySession,
    browser: ControllerInfo,
    params: LibraryParams?,
  ): ListenableFuture<LibraryResult<MediaItem>> {
    val mediaItem = if (params?.isRecent == true) {
      mediaItemProvider.recent() ?: mediaItemProvider.root()
    } else {
      mediaItemProvider.root()
    }
    Logger.d("onGetLibraryRoot(isRecent=${params?.isRecent == true}). Returning ${mediaItem.mediaId}")
    return Futures.immediateFuture(LibraryResult.ofItem(mediaItem, params))
  }

  override fun onGetItem(
    session: MediaLibrarySession,
    browser: ControllerInfo,
    mediaId: String,
  ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
    Logger.d("onGetItem(mediaId=$mediaId)")
    val item = mediaItemProvider.item(mediaId)
    if (item != null) {
      LibraryResult.ofItem(item, null)
    } else {
      LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }
  }

  override fun onGetChildren(
    session: MediaLibrarySession,
    browser: ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: LibraryParams?,
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
    Logger.d("onGetChildren for $parentId")
    val children = mediaItemProvider.children(parentId)
    if (children != null) {
      LibraryResult.ofItemList(children, params)
    } else {
      LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }
  }

  override fun onPlaybackResumption(
    mediaSession: MediaSession,
    controller: ControllerInfo,
    isForPlayback: Boolean,
  ): ListenableFuture<MediaItemsWithStartPosition> {
    Logger.d("onPlaybackResumption")
    return scope.future {
      val currentBook = currentBook()
      if (currentBook != null) {
        mediaItemProvider.mediaItemsWithStartPosition(currentBook)
      } else {
        throw UnsupportedOperationException()
      }
    }
  }

  private suspend fun currentBook(): Book? {
    val bookId = currentBookStoreId.data.first() ?: return null
    return bookRepository.get(bookId)
  }

  override fun onConnect(
    session: MediaSession,
    controller: ControllerInfo,
  ): ConnectionResult {
    Logger.d("onConnect to ${controller.packageName}")

    if (player.playbackState == Player.STATE_IDLE &&
      controller.packageName == "com.google.android.projection.gearhead"
    ) {
      Logger.d("onConnect to ${controller.packageName} and player is idle.")
      Logger.d("Preparing current book so it shows up as recently played")
      scope.launch {
        prepareCurrentBook()
      }
    }

    val connectionResult = super.onConnect(session, controller)
    val sessionCommands = connectionResult.availableSessionCommands
      .buildUpon()
      .add(SessionCommand(CustomCommand.CUSTOM_COMMAND_ACTION, Bundle.EMPTY))
      .build()
    return ConnectionResult.accept(
      sessionCommands,
      connectionResult.availablePlayerCommands,
    )
  }

  private suspend fun prepareCurrentBook() {
    val bookId = currentBookStoreId.data.first() ?: return
    val book = bookRepository.get(bookId) ?: return
    val item = mediaItemProvider.mediaItem(book)
    player.setMediaItem(item)
    player.prepare()
  }

  override fun onCustomCommand(
    session: MediaSession,
    controller: ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle,
  ): ListenableFuture<SessionResult> {
    val command = CustomCommand.parse(customCommand, args)
      ?: return super.onCustomCommand(session, controller, customCommand, args)
    when (command) {
      CustomCommand.ForceSeekToNext -> {
        player.forceSeekToNext()
      }
      CustomCommand.ForceSeekToPrevious -> {
        player.forceSeekToPrevious()
      }
      is CustomCommand.SetSkipSilence -> {
        player.setSkipSilenceEnabled(command.skipSilence)
      }
      is CustomCommand.SetGain -> {
        player.setGain(command.gain)
      }
    }

    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
  }

  private companion object {
    const val DOUBLE_PRESS_THRESHOLD_MS = 1500L
    const val NEXT_PREVIOUS_ECHO_WINDOW_MS = 500L
    val PLAY_PAUSE_KEY_CODES = setOf(
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
      KeyEvent.KEYCODE_MEDIA_PLAY,
      KeyEvent.KEYCODE_MEDIA_PAUSE,
    )
  }
}
