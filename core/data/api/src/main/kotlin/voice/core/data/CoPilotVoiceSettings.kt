package voice.core.data

import kotlinx.serialization.Serializable

/**
 * How the co-pilot talks. [useSystemAiVoice] answers in the Dungeon Crawler Carl System AI's
 * snarky, deadpan voice; [keepItClean] only applies on top of that and forbids swearing and
 * sexual lines, for when the app is being shown to other people.
 */
@Serializable
public data class CoPilotVoiceSettings(
  val useSystemAiVoice: Boolean = true,
  val keepItClean: Boolean = false,
)
