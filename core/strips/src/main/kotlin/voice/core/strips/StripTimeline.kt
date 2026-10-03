package voice.core.strips

fun StripManifest.endMs(): Long = frames.last().startMs + lastFrameHoldMs

fun StripManifest.frameIndexAt(bookMs: Long): Int? {
  if (frames.isEmpty() || bookMs < frames.first().startMs || bookMs >= endMs()) return null
  return frames.indexOfLast { it.startMs <= bookMs }
}

fun StripManifest.segmentEndMs(index: Int): Long = frames.getOrNull(index + 1)?.startMs ?: endMs()

fun StripManifest.nextSeekMs(index: Int): Long? = frames.getOrNull(index + 1)?.startMs

fun StripManifest.previousSeekMs(index: Int): Long = frames[(index - 1).coerceAtLeast(0)].startMs
