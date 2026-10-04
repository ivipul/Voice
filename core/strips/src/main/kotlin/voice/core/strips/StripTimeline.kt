package voice.core.strips

/** How long the full strip image stays up as the story's final frame before the story closes. */
const val STRIP_FRAME_HOLD_MS = 7_000L

/** When the last comic frame's hold ends. */
fun StripManifest.endMs(): Long = frames.last().startMs + lastFrameHoldMs

fun StripManifest.hasStripFrame(): Boolean = stripImage != null

/** The comic frames plus the full strip image when there is one. */
val StripManifest.frameCount: Int get() = frames.size + if (hasStripFrame()) 1 else 0

/** When the story closes: after the last comic frame's hold, plus the full strip's hold if there is one. */
fun StripManifest.storyEndMs(): Long = endMs() + if (hasStripFrame()) STRIP_FRAME_HOLD_MS else 0L

/** The frame shown at [bookMs]; the full strip image is the index after the last comic frame. */
fun StripManifest.frameIndexAt(bookMs: Long): Int? {
  if (frames.isEmpty() || bookMs < frames.first().startMs || bookMs >= storyEndMs()) return null
  return if (bookMs < endMs()) frames.indexOfLast { it.startMs <= bookMs } else frames.size
}

fun StripManifest.segmentEndMs(index: Int): Long = when {
  index < frames.lastIndex -> frames[index + 1].startMs
  index == frames.lastIndex -> endMs()
  else -> storyEndMs()
}

fun StripManifest.nextSeekMs(index: Int): Long? = when {
  index < frames.lastIndex -> frames[index + 1].startMs
  index == frames.lastIndex && hasStripFrame() -> endMs()
  else -> null
}

fun StripManifest.previousSeekMs(index: Int): Long =
  if (index >= frames.size) frames.last().startMs else frames[(index - 1).coerceAtLeast(0)].startMs

fun AvailableStrip.isUnlockedAt(bookMs: Long): Boolean = bookMs >= manifest.frames.first().startMs
