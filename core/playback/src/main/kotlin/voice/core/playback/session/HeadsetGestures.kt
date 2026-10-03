package voice.core.playback.session

import voice.core.data.CoPilotButtonMapping
import voice.core.data.CoPilotTriggerAction

/** Counts consecutive presses of the same headset key, so single, double and triple taps can be told apart. */
internal class HeadsetPressCounter(private val maxGapMs: Long) {

  private var lastKeyCode: Int? = null
  private var lastPressAtMs: Long = 0L
  private var count: Int = 0

  /** Registers a press and returns how many presses of [keyCode] are in the current sequence. */
  fun press(keyCode: Int, atMs: Long): Int {
    count = if (keyCode == lastKeyCode && atMs - lastPressAtMs <= maxGapMs) count + 1 else 1
    lastKeyCode = keyCode
    lastPressAtMs = atMs
    return count
  }

  fun reset() {
    lastKeyCode = null
    count = 0
  }
}

/**
 * NEXT can be tapped up to three times; PREVIOUS only up to two, so its double tap can fire at
 * once instead of waiting to see whether a third tap follows.
 */
internal fun isLastPossiblePress(isNext: Boolean, presses: Int): Boolean =
  presses >= if (isNext) MAX_NEXT_PRESSES else MAX_PREVIOUS_PRESSES

internal fun CoPilotButtonMapping.actionFor(isNext: Boolean, presses: Int): CoPilotTriggerAction = when {
  isNext && presses >= MAX_NEXT_PRESSES -> tripleNext
  isNext && presses == 2 -> doubleNext
  isNext -> singleNext
  presses >= MAX_PREVIOUS_PRESSES -> doublePrevious
  else -> singlePrevious
}

private const val MAX_NEXT_PRESSES = 3
private const val MAX_PREVIOUS_PRESSES = 2
