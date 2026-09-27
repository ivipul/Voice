package voice.features.playbackScreen

internal sealed interface BookPlayViewEffect {
  data object SnipSaved : BookPlayViewEffect
  data object BookmarkAdded : BookPlayViewEffect
  data object RequestIgnoreBatteryOptimization : BookPlayViewEffect
}
