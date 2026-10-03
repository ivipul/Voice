package voice.core.copilot

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.store.SystemAiKeepCleanStore
import voice.core.data.store.SystemAiVoiceStore

/** How co-pilot answers are worded: plain, or as the System AI from Dungeon Crawler Carl. */
data class VoiceStyle(
  val systemAiVoice: Boolean,
  val keepItClean: Boolean,
) {

  companion object {
    val Plain = VoiceStyle(systemAiVoice = false, keepItClean = false)
  }
}

/** Reads the current [VoiceStyle] at call time, so a settings change applies to the next answer. */
fun interface VoiceStyleSource {
  suspend fun current(): VoiceStyle
}

@ContributesBinding(AppScope::class)
@Inject
class DataStoreVoiceStyleSource(
  @SystemAiVoiceStore
  private val systemAiVoiceStore: DataStore<Boolean>,
  @SystemAiKeepCleanStore
  private val keepCleanStore: DataStore<Boolean>,
) : VoiceStyleSource {

  override suspend fun current(): VoiceStyle = VoiceStyle(
    systemAiVoice = systemAiVoiceStore.data.first(),
    keepItClean = keepCleanStore.data.first(),
  )
}
