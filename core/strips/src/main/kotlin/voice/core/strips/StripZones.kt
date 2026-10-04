package voice.core.strips

/**
 * A strip's range on a seek bar that covers [spanStartMs]..[spanEndMs] of the book, as fractions of that bar.
 * [startBookMs] is where tapping the zone should seek to: the strip's first frame, even if the zone is clipped.
 */
data class StripZone(
  val startFraction: Float,
  val endFraction: Float,
  val startBookMs: Long,
)

/** The book-time range a strip plays over: its first frame until the last frame's hold ends. */
fun StripManifest.rangeMs(): LongRange = frames.first().startMs until endMs()

/** The zones of the strips that overlap the span, clipped to it. */
fun List<AvailableStrip>.zonesWithin(
  spanStartMs: Long,
  spanEndMs: Long,
): List<StripZone> {
  val span = spanEndMs - spanStartMs
  if (span <= 0L) return emptyList()
  return mapNotNull { strip ->
    val start = strip.manifest.frames.first().startMs
    val end = strip.manifest.endMs()
    val clippedStart = maxOf(start, spanStartMs)
    val clippedEnd = minOf(end, spanEndMs)
    if (clippedStart >= clippedEnd) {
      null
    } else {
      StripZone(
        startFraction = (clippedStart - spanStartMs).toFloat() / span,
        endFraction = (clippedEnd - spanStartMs).toFloat() / span,
        startBookMs = start,
      )
    }
  }
}

/** Where a tap at [fraction] of the bar should seek to when it lands in a zone, else null. */
fun List<StripZone>.stripStartAt(fraction: Float): Long? =
  lastOrNull { fraction >= it.startFraction && fraction <= it.endFraction }?.startBookMs

/** The comic frame showing at a book position. */
data class ActiveStripFrame(
  val strip: AvailableStrip,
  val frameIndex: Int,
)

/** The latest-starting strip whose range contains [bookMs] and its current frame, or null outside every strip. */
fun List<AvailableStrip>.activeFrameAt(bookMs: Long): ActiveStripFrame? {
  val strip = filter { bookMs in it.manifest.rangeMs() }.maxByOrNull { it.manifest.frames.first().startMs } ?: return null
  val index = strip.manifest.frames.indexOfLast { it.startMs <= bookMs }
  return ActiveStripFrame(strip, index)
}
