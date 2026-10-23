package voice.core.data

import kotlinx.serialization.Serializable

/**
 * The listener's own API keys, typed into Settings and kept only on the device. Nothing is built into the app, so
 * anyone can run it with their own accounts. A blank key turns the features that need it off.
 */
@Serializable
public data class ApiKeys(
  /** Claude: answers, snips, recaps, book research, Extras. */
  val claude: String = "",
  /** Ideogram: snip pictures and custom covers. */
  val ideogram: String = "",
  /** Deepgram: transcribing a book when it is added. */
  val deepgram: String = "",
)
