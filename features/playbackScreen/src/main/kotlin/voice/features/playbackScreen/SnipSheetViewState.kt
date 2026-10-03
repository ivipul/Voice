package voice.features.playbackScreen

internal sealed interface SnipSheetViewState {
  data object Loading : SnipSheetViewState
  data class Ready(val text: String) : SnipSheetViewState
}
