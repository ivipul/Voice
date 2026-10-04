package voice.core.xray

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remembers what [load] found for a key. Callers that ask while a load is running wait for it instead of starting
 * another, so a warm-up and a tap that come together do the slow work once. A key that found nothing is looked up
 * again next time, in case the file shows up later.
 */
internal class SingleFlightCache<K : Any, V : Any> {

  private val mutex = Mutex()
  private val values = mutableMapOf<K, V>()

  suspend fun get(
    key: K,
    load: suspend () -> V?,
  ): V? = mutex.withLock {
    values[key] ?: load()?.also { values[key] = it }
  }
}
