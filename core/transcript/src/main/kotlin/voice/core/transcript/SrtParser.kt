package voice.core.transcript

/** Reads SubRip subtitles (`.srt`), which some tools export as a book's transcript. */
object SrtParser {

  private val timing = Regex("""(\d+):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{1,3})""")

  fun parse(text: String): List<TranscriptCue> = text
    .replace("\r\n", "\n")
    .split(Regex("\\n\\s*\\n"))
    .mapNotNull { block ->
      val lines = block.trim().lines()
      val timingIndex = lines.indexOfFirst { timing.containsMatchIn(it) }
      if (timingIndex < 0) return@mapNotNull null
      val match = timing.find(lines[timingIndex]) ?: return@mapNotNull null
      val words = lines.drop(timingIndex + 1).joinToString(" ") { it.trim() }.replace(Regex("<[^>]+>"), "").trim()
      if (words.isEmpty()) return@mapNotNull null
      val g = match.groupValues
      TranscriptCue(
        timestampStartMs = millis(g[1], g[2], g[3], g[4]),
        timestampEndMs = millis(g[5], g[6], g[7], g[8]),
        text = words,
      )
    }
    .sortedBy { it.timestampStartMs }

  private fun millis(hours: String, minutes: String, seconds: String, fraction: String): Long =
    ((hours.toLong() * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000 + fraction.padEnd(3, '0').toLong()
}

/** The file names a transcript of [audioName] can have beside it, most common first. */
fun transcriptNamesFor(audioName: String): List<String> {
  val base = audioName.substringBeforeLast('.')
  return listOf("$base.json", "$audioName.json", "$base.srt", "$audioName.srt").distinct()
}

/** Whether [fileName] is a transcript of [audioName]: `Book.json`, `Book.m4b.json`, `Book.srt` or `Book.m4b.srt`. */
fun isTranscriptFor(fileName: String, audioName: String): Boolean =
  transcriptNamesFor(audioName).any { it.equals(fileName, ignoreCase = true) }
