package voice.core.copilot.tts

/**
 * Splits an answer into pieces that are all fetched at once and played back to back, so the first
 * sentence can start while the rest is still being synthesized. The first piece is a single
 * sentence (cut at a comma when it is long) for the fastest start; no piece is under
 * [MIN_WORDS] words, and later pieces are merged up to about [MAX_CHARS] characters.
 */
object SpeechChunker {

  private const val MAX_CHARS = 200
  private const val MIN_WORDS = 8
  private const val MAX_OPENER_CHARS = 110
  private val sentenceBreak = Regex("(?<=[.!?])\\s+")
  private val abbreviations = setOf("mr.", "mrs.", "ms.", "dr.", "st.", "vs.", "prof.", "sr.", "jr.", "no.", "e.g.", "i.e.")
  private val initial = Regex("[A-Z]\\.")
  private val clauseEnd = Regex("[,;:](?=\\s)")

  fun split(text: String): List<String> {
    val sentences = sentences(text).flatMap(::capLength)
    if (sentences.isEmpty()) return emptyList()
    val merged = mergeShort(sentences)
    val (opener, remainder) = shortenOpener(merged.first())
    val rest = listOfNotNull(remainder) + merged.drop(1)
    return listOf(opener) + pack(rest)
  }

  private fun sentences(text: String): List<String> {
    val parts = text.trim().split(sentenceBreak).map { it.trim() }.filter { it.isNotEmpty() }
    val sentences = mutableListOf<String>()
    for (part in parts) {
      val previous = sentences.lastOrNull()
      if (previous != null && endsWithAbbreviation(previous)) sentences[sentences.lastIndex] = "$previous $part" else sentences += part
    }
    return sentences
  }

  private fun endsWithAbbreviation(sentence: String): Boolean {
    val lastWord = sentence.substringAfterLast(' ')
    return lastWord.lowercase() in abbreviations || initial.matches(lastWord)
  }

  /** A sentence longer than the cap is cut at the last clause break, else the last space, before it. */
  private fun capLength(sentence: String): List<String> {
    val pieces = mutableListOf<String>()
    var remaining = sentence
    while (remaining.length > MAX_CHARS) {
      val window = remaining.take(MAX_CHARS)
      val cut = clauseEnd.findAll(window).lastOrNull()?.range?.first?.plus(1)
        ?: window.lastIndexOf(' ').takeIf { it > 0 }
        ?: MAX_CHARS
      pieces += remaining.substring(0, cut).trim()
      remaining = remaining.substring(cut).trim()
    }
    if (remaining.isNotEmpty()) pieces += remaining
    return pieces
  }

  private fun words(text: String) = text.split(' ').count { it.isNotEmpty() }

  private fun mergeShort(sentences: List<String>): List<String> {
    val merged = mutableListOf<String>()
    var carry = ""
    for (sentence in sentences) {
      val candidate = if (carry.isEmpty()) sentence else "$carry $sentence"
      if (words(candidate) < MIN_WORDS) carry = candidate else {
        merged += candidate
        carry = ""
      }
    }
    if (carry.isNotEmpty()) {
      if (merged.isEmpty()) merged += carry else merged[merged.lastIndex] = "${merged.last()} $carry"
    }
    return merged
  }

  // A long first sentence is cut at a comma so the first request, and so the wait, stays short.
  private fun shortenOpener(sentence: String): Pair<String, String?> {
    if (sentence.length <= MAX_OPENER_CHARS) return sentence to null
    val cut = clauseEnd.findAll(sentence.take(MAX_OPENER_CHARS)).map { it.range.first + 1 }
      .lastOrNull { words(sentence.take(it)) >= MIN_WORDS && words(sentence.drop(it)) >= MIN_WORDS }
      ?: return sentence to null
    return sentence.substring(0, cut).trim() to sentence.substring(cut).trim()
  }

  private fun pack(sentences: List<String>): List<String> {
    val chunks = mutableListOf<String>()
    var current = ""
    for (sentence in sentences) {
      current = when {
        current.isEmpty() -> sentence
        current.length + 1 + sentence.length <= MAX_CHARS -> "$current $sentence"
        else -> {
          chunks += current
          sentence
        }
      }
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
  }
}
