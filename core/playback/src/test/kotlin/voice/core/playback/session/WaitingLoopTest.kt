package voice.core.playback.session

import kotlin.test.Test
import kotlin.test.assertEquals

class WaitingLoopTest {

  private class FakePlayback : WaitingLoopPlayback {
    val calls = mutableListOf<String>()
    override fun start() { calls += "start" }
    override fun fadeOutAndRelease() { calls += "fade" }
    override fun releaseNow() { calls += "release" }
  }

  private val playback = FakePlayback()
  private val loop = WaitingLoop(playback)

  @Test
  fun `start then first audio fades out`() {
    loop.start()
    loop.stopWithFade()

    assertEquals(listOf("start", "fade"), playback.calls)
  }

  @Test
  fun `starting twice starts once`() {
    loop.start()
    loop.start()

    assertEquals(listOf("start"), playback.calls)
  }

  @Test
  fun `stopping when idle does nothing`() {
    loop.stopWithFade()
    loop.stopNow()

    assertEquals(emptyList(), playback.calls)
  }

  @Test
  fun `a failure or interrupt releases at once and a later fade does nothing`() {
    loop.start()
    loop.stopNow()
    loop.stopWithFade()
    loop.stopNow()

    assertEquals(listOf("start", "release"), playback.calls)
  }

  @Test
  fun `after fading out it can start again for the next flow`() {
    loop.start()
    loop.stopWithFade()
    loop.start()
    loop.stopNow()

    assertEquals(listOf("start", "fade", "start", "release"), playback.calls)
  }
}
