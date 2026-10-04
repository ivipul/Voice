package voice.features.playbackScreen

import androidx.compose.runtime.Immutable
import voice.core.playback.misc.Decibel
import voice.core.strips.ActiveStripFrame
import voice.core.strips.StripZone
import voice.core.xray.card.ComposedPlayerCard
import voice.core.xray.card.PlayerCardData
import voice.core.xray.card.composeAt
import voice.core.xray.card.hasGameStats
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
  val xrayChips: List<XRayChipViewState>,
  /** The strips that play within the current chapter, as ranges of the seek bar. */
  val stripZones: List<StripZone> = emptyList(),
  /** The strip frame to show in place of the cover while a strip is playing. */
  val stripFrame: ActiveStripFrame? = null,
) {

  data class XRayChipViewState(val id: String, val label: String)

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
  data class SpeedDialog(val speed: Float) : BookPlayDialogViewState {

    val maxSpeed: Float get() = if (speed < 2F) 2F else 3.5F
  }

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
   * Every X-Ray chip opens one. An entity with card data keeps its raw [data] and every look's resolved
   * [imageUris] so the card can follow the playback position while it is open; [card] is how it looked when it was
   * opened. Anything else gets empty [data], shows the X-Ray [description] and, if it has one, the X-Ray [portrait].
   */
  data class XRayCardDialog(
    val name: String,
    val description: String,
    val data: PlayerCardData,
    val imageUris: Map<String, String>,
    val portrait: String?,
    val openedAtMs: Long,
  ) : BookPlayDialogViewState {

    val card: ComposedPlayerCard get() = data.composeAt(openedAtMs)

    val plain: Boolean get() = !data.hasGameStats

    val lookImage: String? get() = card.look?.image?.let(imageUris::get)
  }
}
