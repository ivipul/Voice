package voice.features.bookmark.strips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.story.StoryPlayer
import voice.core.strips.AvailableStrip
import voice.core.strips.chapterPositionOf
import voice.core.strips.frameCount
import voice.core.strips.frameIndexAt
import voice.core.strips.nextSeekMs
import voice.core.strips.previousSeekMs
import voice.core.strips.segmentEndMs

data class StripViewerViewState(
  val frameIndex: Int,
  val progress: List<Float>,
  val isPlaying: Boolean,
  val closed: Boolean,
)

/**
 * Plays a Strip as a full-screen story, in one of two modes.
 *
 * Isolated (the Strips tab): the story runs on its own [StoryPlayer], so the book's own player is only paused
 * while the story is open and its position is never moved to a frame's timestamp. Closing the story stops its
 * audio and lets the book carry on from where it was if it had been playing.
 *
 * Linked ([linkedToBook], opened from the player screen): the story is the book's own playback. It opens on
 * the frame at the book's position without pausing or seeking it, taps seek the book between frames, the
 * play button plays and pauses the book, and closing leaves the book where it is, in the state it is in.
 */
@AssistedInject
class StripViewerViewModel(
  private val storyPlayer: StoryPlayer,
  private val playerController: PlayerController,
  private val playStateManager: PlayStateManager,
  private val bookRepository: BookRepository,
  @Assisted
  private val bookId: BookId,
  @Assisted
  val strip: AvailableStrip,
  /** The comic frame the story opens on; the first frame when it is not given. */
  @Assisted
  private val startFrameIndex: Int,
  /** Whether the story is the book's own playback rather than a separate player. */
  @Assisted
  private val linkedToBook: Boolean,
) {

  private val scope = MainScope()
  private val manifest = strip.manifest
  private var resumeBookOnClose = false
  private var closed = false

  // The frame on screen, kept current here so a tap always moves one frame from what is shown.
  private var shownFrameIndex = 0

  fun start() {
    if (linkedToBook) {
      shownFrameIndex = startFrameIndex.coerceIn(0, manifest.frames.lastIndex)
      return
    }
    scope.launch {
      val chapters = bookRepository.get(bookId)?.chapters ?: return@launch
      resumeBookOnClose = playStateManager.playState == PlayStateManager.PlayState.Playing
      playerController.pause()
      val first = startFrameIndex.coerceIn(0, manifest.frames.lastIndex)
      shownFrameIndex = first
      storyPlayer.start(chapters, manifest.frames[first].startMs)
    }
  }

  /** Moves to the next frame of the story; false when the last frame is showing, which closes the story. */
  fun onNext(): Boolean {
    val next = manifest.nextSeekMs(shownFrameIndex) ?: return false
    shownFrameIndex += 1
    seekTo(next)
    return true
  }

  /** Back one frame; on the first frame it restarts that frame. */
  fun onPrevious() {
    val target = manifest.previousSeekMs(shownFrameIndex)
    shownFrameIndex = (shownFrameIndex - 1).coerceAtLeast(0)
    seekTo(target)
  }

  fun togglePlaying() {
    if (linkedToBook) playerController.playPause() else storyPlayer.togglePlaying()
  }

  fun close() {
    if (closed) return
    closed = true
    scope.cancel()
    if (linkedToBook) return
    storyPlayer.stop()
    if (resumeBookOnClose) playerController.play()
  }

  private fun seekTo(bookMs: Long) {
    if (!linkedToBook) {
      storyPlayer.seekTo(bookMs)
      return
    }
    scope.launch {
      val chapters = bookRepository.get(bookId)?.chapters ?: return@launch
      val (chapterId, positionInChapter) = chapters.chapterPositionOf(bookMs) ?: return@launch
      playerController.setPosition(positionInChapter, chapterId)
    }
  }

  private data class Position(
    val bookMs: Long,
    val isPlaying: Boolean,
  )

  @Composable
  private fun position(): Position? {
    if (linkedToBook) {
      val book by remember { bookRepository.flow(bookId).filterNotNull() }.collectAsState(initial = null)
      val playState by remember { playStateManager.playStateFlow }.collectAsState()
      return book?.let { Position(it.position, playState == PlayStateManager.PlayState.Playing) }
    }
    val story by remember { storyPlayer.state }.collectAsState(initial = null)
    return story?.let { Position(it.bookMs, it.isPlaying) }
  }

  @Composable
  fun viewState(): StripViewerViewState {
    val position = position()
    val bookMs = position?.bookMs
    val frameIndex = bookMs?.let(manifest::frameIndexAt)

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(frameIndex) {
      if (frameIndex != null) {
        started = true
        shownFrameIndex = frameIndex
      }
    }

    val displayIndex = frameIndex ?: if (linkedToBook) startFrameIndex.coerceIn(0, manifest.frames.lastIndex) else 0
    return StripViewerViewState(
      frameIndex = displayIndex,
      progress = (0 until manifest.frameCount).map { index ->
        progressOf(index, displayIndex, bookMs.takeIf { frameIndex != null })
      },
      isPlaying = position?.isPlaying ?: true,
      // The book is already in the strip when a linked story opens, so a position outside it means it has ended.
      closed = (linkedToBook || started) && bookMs != null && frameIndex == null,
    )
  }

  private fun progressOf(
    index: Int,
    displayIndex: Int,
    bookMs: Long?,
  ): Float {
    return when {
      index < displayIndex -> 1f
      index > displayIndex -> 0f
      bookMs == null -> 0f
      else -> {
        val start = if (index < manifest.frames.size) manifest.frames[index].startMs else manifest.segmentEndMs(index - 1)
        val end = manifest.segmentEndMs(index)
        ((bookMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
      }
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(
      bookId: BookId,
      strip: AvailableStrip,
      startFrameIndex: Int,
      linkedToBook: Boolean,
    ): StripViewerViewModel
  }
}
