package voice.core.copilot.tts

/**
 * Splits an answer into pieces that are fetched and played one after another, so the first
 * sentence starts playing while the rest is still being synthesized. The first piece is kept to
 * one sentence for the fastest start; later pieces are merged up to [MAX_CHARS] to limit requests.
 */
object SpeechChunker {

  private const val MAX_CHARS = 220
  private const val MIN_CHARS = 40
  private const val MAX_OPENER_CHARS = 100
  private const val MIN_OPENER_CHARS = 30
  private val clauseBreak = Regex("[,;:](?=\\s)")
  private val sentenceBreak = Regex("(?<=[.!?])\\s+")

  fun split(text: String): List<String> {
    val sentences = text.trim().split(sentenceBreak).map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    var first = sentences.first()
    var rest = sentences.drop(1)
    // A very short opener ("Okay.") reads better joined to the next sentence.
    if (first.length < MIN_CHARS && rest.isNotEmpty()) {
      first = "$first ${rest.first()}"
      rest = rest.drop(1)
    }
    val (opener, remainder) = splitLongOpener(first)
    chunks += opener
    if (remainder != null) rest = listOf(remainder) + rest
    var current = ""
    for (sentence in rest) {
      current = if (current.isEmpty()) sentence else if (current.length + 1 + sentence.length <= MAX_CHARS) "$current $sentence" else {
        chunks += current
        sentence
      }
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
  }

  // A long first sentence is cut at a comma/colon so the first request, and so the wait, stays short.
  private fun splitLongOpener(sentence: String): Pair<String, String?> {
    if (sentence.length <= MAX_OPENER_CHARS) return sentence to null
    val cut = clauseBreak.findAll(sentence.take(MAX_OPENER_CHARS)).map { it.range.first }.lastOrNull { it + 1 >= MIN_OPENER_CHARS }
      ?: return sentence to null
    return sentence.substring(0, cut + 1) to sentence.substring(cut + 1).trim()
  }
}
