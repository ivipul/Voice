package voice.core.playback.session

/**
 * Counts consecutive presses of the same headset key, each within [windowMs] of the previous one.
 * A different key, or a pause longer than the window, starts the count over at one.
 */
internal class HeadsetPressCounter(private val windowMs: Long) {

  private var lastKeyCode: Int? = null
  private var lastPressAtMs: Long = 0L
  private var count: Int = 0

  fun onPress(keyCode: Int, nowMs: Long): Int {
    val continuesSequence = keyCode == lastKeyCode && nowMs - lastPressAtMs <= windowMs
    count = if (continuesSequence) count + 1 else 1
    lastKeyCode = keyCode
    lastPressAtMs = nowMs
    return count
  }

  /** Forgets the sequence so the next press counts as the first of a new one. */
  fun reset() {
    lastKeyCode = null
    count = 0
  }
}
