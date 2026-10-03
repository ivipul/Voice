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
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.story.StoryPlayer
import voice.core.strips.AvailableStrip
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
 * Plays a Strip as a story on its own [StoryPlayer], so the book's own player is only paused while the story
 * is open and its position is never moved to a frame's timestamp. Closing the story stops its audio and lets
 * the book carry on from where it was if it had been playing.
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
) {

  private val scope = MainScope()
  private val manifest = strip.manifest
  private var resumeBookOnClose = false
  private var closed = false

  // The frame on screen, kept current here so a tap always moves one frame from what is shown.
  private var shownFrameIndex = 0

  fun start() {
    scope.launch {
      val chapters = bookRepository.get(bookId)?.chapters ?: return@launch
      resumeBookOnClose = playStateManager.playState == PlayStateManager.PlayState.Playing
      playerController.pause()
      storyPlayer.start(chapters, manifest.frames.first().startMs)
    }
  }

  /** Moves to the next frame of the story; false when the last frame is showing, which closes the story. */
  fun onNext(): Boolean {
    val next = manifest.nextSeekMs(shownFrameIndex) ?: return false
    shownFrameIndex += 1
    storyPlayer.seekTo(next)
    return true
  }

  /** Back one frame; on the first frame it restarts that frame. */
  fun onPrevious() {
    val target = manifest.previousSeekMs(shownFrameIndex)
    shownFrameIndex = (shownFrameIndex - 1).coerceAtLeast(0)
    storyPlayer.seekTo(target)
  }

  fun togglePlaying() {
    storyPlayer.togglePlaying()
  }

  fun close() {
    if (closed) return
    closed = true
    scope.cancel()
    storyPlayer.stop()
    if (resumeBookOnClose) playerController.play()
  }

  @Composable
  fun viewState(): StripViewerViewState {
    val story by remember { storyPlayer.state }.collectAsState(initial = null)
    val bookMs = story?.bookMs
    val frameIndex = bookMs?.let(manifest::frameIndexAt)

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(frameIndex) {
      if (frameIndex != null) {
        started = true
        shownFrameIndex = frameIndex
      }
    }

    val displayIndex = frameIndex ?: 0
    return StripViewerViewState(
      frameIndex = displayIndex,
      progress = (0 until manifest.frameCount).map { index ->
        progressOf(index, displayIndex, bookMs.takeIf { frameIndex != null })
      },
      isPlaying = story?.isPlaying ?: true,
      closed = started && frameIndex == null,
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
    ): StripViewerViewModel
  }
}
