package voice.core.data

import kotlinx.serialization.Serializable

/** What a mapped headset button press should do - shared between the settings UI (which
 * lets the user choose one per slot) and the actual Bluetooth dispatch logic that reads it. */
@Serializable
public enum class CoPilotTriggerAction {
  DefaultRewind,
  DefaultForward,
  OpenMic,
  AutoIdentify,
  CatchMeUp,
  Snip,
}

/** Persisted headset button -> co-pilot action mapping (single/double/triple tap NEXT, single/double tap PREVIOUS). */
@Serializable
public data class CoPilotButtonMapping(
  val singleNext: CoPilotTriggerAction = CoPilotTriggerAction.OpenMic,
  val singlePrevious: CoPilotTriggerAction = CoPilotTriggerAction.DefaultRewind,
  val doubleNext: CoPilotTriggerAction = CoPilotTriggerAction.AutoIdentify,
  val doublePrevious: CoPilotTriggerAction = CoPilotTriggerAction.CatchMeUp,
  val tripleNext: CoPilotTriggerAction = CoPilotTriggerAction.AutoIdentify,
)
