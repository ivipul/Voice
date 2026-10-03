package voice.core.copilot

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceStyleSourceTest {

  private val systemAiVoiceStore = MemoryDataStore(true)
  private val keepCleanStore = MemoryDataStore(false)
  private val source = DataStoreVoiceStyleSource(systemAiVoiceStore, keepCleanStore)

  @Test
  fun `defaults to the System AI voice with swearing allowed`() = runTest {
    assertEquals(VoiceStyle(systemAiVoice = true, keepItClean = false), source.current())
  }

  @Test
  fun `reads setting changes on the next call`() = runTest {
    keepCleanStore.updateData { true }
    assertEquals(VoiceStyle(systemAiVoice = true, keepItClean = true), source.current())

    systemAiVoiceStore.updateData { false }
    assertEquals(VoiceStyle(systemAiVoice = false, keepItClean = true), source.current())
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {

  private val value = MutableStateFlow(initial)

  override val data: Flow<T> get() = value

  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    return value.updateAndGet { transform(it) }
  }
}
