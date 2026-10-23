package voice.features.playbackScreen

import androidx.compose.runtime.Immutable
import voice.core.companion.SceneNote
import voice.core.playback.misc.Decibel
import voice.core.ui.crawl.BookStyle
import voice.features.sleepTimer.SleepTimerViewState
import kotlin.time.Duration

@Immutable
data class BookPlayViewState(
  val chapterName: String?,
  val showPreviousNextButtons: Boolean,
  val title: String,
  val sleepTimerState: SleepTimerViewState,
  val playedTime: Duration,
  val duration: Duration,
  val playing: Boolean,
  val cover: String?,
  val skipSilence: Boolean,
  val skipBackSeconds: Int,
  val skipForwardSeconds: Int,
  /** Who (fiction) or what (non-fiction) is in the scene right now, once the book's scene scan has run. */
  val xrayChips: List<XRayChipViewState>,
  /** The book's colors and display type. */
  val bookStyle: BookStyle? = null,
  val playbackSpeed: Float = 1F,
  /** The book's snips plus the Extras reached so far: what the Notebook card counts. */
  val inventoryCount: Int = 0,
) {

  data class XRayChipViewState(
    val id: String,
    val label: String,
  )

  sealed interface SleepTimerViewState {
    data object Disabled : SleepTimerViewState

    sealed interface Enabled : SleepTimerViewState {
      data object WithEndOfChapter : Enabled

      @JvmInline
      value class WithDuration(val leftDuration: Duration) : Enabled
    }
  }

  init {
    require(duration > Duration.ZERO) {
      "Duration must be positive in $this"
    }
  }
}

internal sealed interface BookPlayDialogViewState {
  data class SpeedDialog(val speed: Float) : BookPlayDialogViewState

  data class VolumeGainDialog(
    val gain: Decibel,
    val valueFormatted: String,
    val maxGain: Decibel,
  ) : BookPlayDialogViewState

  data class SelectChapterDialog(val items: List<ItemViewState>) : BookPlayDialogViewState {

    data class ItemViewState(
      val number: Int,
      val name: String,
      val active: Boolean,
      val time: String,
    )
  }

  @JvmInline
  value class SleepTimer(val viewState: SleepTimerViewState) : BookPlayDialogViewState

  /**
   * A scene chip's card: who or what it is, and the notes the book has given so far, which reveal as playback reaches
   * them. A topic with an unlocked Extra links to it.
   */
  data class SceneCardDialog(
    val name: String,
    val summary: String,
    val notes: List<SceneNote>,
    val fiction: Boolean,
    val extraId: String?,
    val extraTitle: String?,
    val openedAtMs: Long,
  ) : BookPlayDialogViewState
}
