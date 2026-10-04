package voice.core.xray

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SingleFlightCacheTest {

  @Test
  fun `a value is loaded once and then remembered`() = runTest {
    val cache = SingleFlightCache<String, String>()
    var loads = 0

    val first = cache.get("a") { loads++; "value" }
    val second = cache.get("a") { loads++; "other" }

    assertEquals("value", first)
    assertEquals("value", second)
    assertEquals(1, loads)
  }

  @Test
  fun `a caller that arrives while a load is running waits for it instead of loading again`() = runTest {
    val cache = SingleFlightCache<String, String>()
    val release = CompletableDeferred<Unit>()
    var loads = 0

    val warmUp = async(UnconfinedTestDispatcher(testScheduler)) {
      cache.get("a") {
        loads++
        release.await()
        "value"
      }
    }
    val tap = async(UnconfinedTestDispatcher(testScheduler)) { cache.get("a") { loads++; "second load" } }
    release.complete(Unit)

    assertEquals("value", warmUp.await())
    assertEquals("value", tap.await())
    assertEquals(1, loads)
  }

  @Test
  fun `nothing found is not remembered, so a file that shows up later is picked up`() = runTest {
    val cache = SingleFlightCache<String, String>()
    var found: String? = null

    assertNull(cache.get("a") { found })
    found = "now there"

    assertEquals("now there", cache.get("a") { found })
  }

  @Test
  fun `keys are remembered separately`() = runTest {
    val cache = SingleFlightCache<String, Int>()

    assertEquals(1, cache.get("a") { 1 })
    assertEquals(2, cache.get("b") { 2 })
    assertEquals(1, cache.get("a") { 3 })
  }
}
