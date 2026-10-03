package voice.features.bookmark.strips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.playback.PlayerController
import voice.core.strips.AvailableStrip
import voice.core.strips.bookPositionOf
import voice.core.strips.chapterPositionOf
import voice.core.strips.frameIndexAt
import voice.core.strips.nextSeekMs
import voice.core.strips.previousSeekMs
import voice.core.strips.segmentEndMs

data class StripViewerViewState(
  val frameIndex: Int,
  val progress: List<Float>,
  val nextFrameStartMs: Long?,
  val previousFrameStartMs: Long,
  val closed: Boolean,
)

@AssistedInject
class StripViewerViewModel(
  private val playerController: PlayerController,
  private val bookRepository: BookRepository,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @Assisted
  private val bookId: BookId,
  @Assisted
  val strip: AvailableStrip,
) {

  private val scope = MainScope()
  private val manifest = strip.manifest

  fun start() {
    scope.launch {
      currentBookStore.updateData { bookId }
      seekTo(manifest.frames.first().startMs)
    }
  }

  fun seekTo(bookMs: Long) {
    scope.launch {
      val chapters = bookRepository.get(bookId)?.chapters ?: return@launch
      val (chapterId, positionInChapter) = chapters.chapterPositionOf(bookMs) ?: return@launch
      playerController.setPosition(positionInChapter, chapterId)
    }
  }

  @Composable
  fun viewState(): StripViewerViewState {
    val chapters by produceState<List<Chapter>>(emptyList(), bookId) {
      value = bookRepository.get(bookId)?.chapters.orEmpty()
    }
    val livePlayback by remember(bookId) { playerController.livePlaybackStateFlow(bookId) }
      .collectAsState(initial = null)
    val bookMs = livePlayback?.let { chapters.bookPositionOf(it.chapterId, it.positionMs) }
    val frameIndex = bookMs?.let(manifest::frameIndexAt)

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(frameIndex) {
      if (frameIndex != null) started = true
    }

    val displayIndex = frameIndex ?: 0
    return StripViewerViewState(
      frameIndex = displayIndex,
      progress = manifest.frames.indices.map { index ->
        progressOf(index, displayIndex, bookMs.takeIf { frameIndex != null })
      },
      nextFrameStartMs = manifest.nextSeekMs(displayIndex),
      previousFrameStartMs = manifest.previousSeekMs(displayIndex),
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
        val start = manifest.frames[index].startMs
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
