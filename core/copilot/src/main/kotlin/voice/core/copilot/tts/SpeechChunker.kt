package voice.core.copilot.tts

/**
 * Turns an answer, arriving either whole or as streamed text deltas, into pieces that are all
 * fetched at once and played back to back, so the first sentence can start while the rest is
 * still being generated or synthesized.
 *
 * Feed deltas with [feed] and call [finish] at the end; each call returns the pieces that became
 * ready. The first piece is the first complete sentence with at least [MIN_WORDS] words (cut at a
 * comma if it grows past [MAX_OPENER_CHARS] before the sentence ends). Later pieces are runs of
 * complete sentences of at least [PACK_CHARS] characters, never above [MAX_CHARS]; only the final
 * piece can be shorter. Abbreviations ("Mr.") and decimals ("3.5") do not end a sentence.
 */
class SpeechChunker {

  private val buffer = StringBuilder()
  private var pending = ""
  private var emittedAny = false

  fun feed(delta: String): List<String> {
    buffer.append(delta)
    val out = mutableListOf<String>()
    if (!emittedAny && pending.isEmpty()) emitEarlyOpener()?.let { out += it }
    for (sentence in completeSentences()) addSentence(sentence, out)
    return out
  }

  fun finish(): List<String> {
    val out = mutableListOf<String>()
    val tail = buffer.toString().trim()
    buffer.clear()
    if (tail.isNotEmpty()) tail.let(::capLength).forEach { addSentence(it, out) }
    if (pending.isNotEmpty()) {
      out += pending
      pending = ""
    }
    if (out.isNotEmpty()) emittedAny = true
    return out
  }

  /** A long first sentence still being written is cut at a comma so the first request goes out early. */
  private fun emitEarlyOpener(): String? {
    if (buffer.length <= MAX_OPENER_CHARS) return null
    val text = buffer.toString()
    val cut = clauseEnd.findAll(text.take(MAX_OPENER_CHARS)).map { it.range.first + 1 }
      .lastOrNull { words(text.take(it)) >= MIN_WORDS } ?: return null
    buffer.delete(0, cut)
    emittedAny = true
    return text.substring(0, cut).trim()
  }

  private fun completeSentences(): List<String> {
    val text = buffer.toString()
    val sentences = mutableListOf<String>()
    var start = 0
    for (match in sentenceBreak.findAll(text)) {
      val candidate = text.substring(start, match.range.first)
      if (endsWithAbbreviation(candidate)) continue
      sentences += candidate.trim()
      start = match.range.last + 1
    }
    buffer.delete(0, start)
    return sentences.filter { it.isNotEmpty() }.flatMap(::capLength)
  }

  private fun addSentence(sentence: String, out: MutableList<String>) {
    if (pending.isNotEmpty() && pending.length + 1 + sentence.length > MAX_CHARS) {
      out += pending
      emittedAny = true
      pending = ""
    }
    pending = if (pending.isEmpty()) sentence else "$pending $sentence"
    val ready = if (!emittedAny) words(pending) >= MIN_WORDS else pending.length >= PACK_CHARS
    if (ready) {
      var chunk = pending
      pending = ""
      if (!emittedAny) {
        val (head, tail) = shortenOpener(chunk)
        chunk = head
        if (tail != null) pending = tail
      }
      out += chunk
      emittedAny = true
    }
  }

  private fun shortenOpener(chunk: String): Pair<String, String?> {
    if (chunk.length <= MAX_OPENER_CHARS) return chunk to null
    val cut = clauseEnd.findAll(chunk.take(MAX_OPENER_CHARS)).map { it.range.first + 1 }
      .lastOrNull { words(chunk.take(it)) >= MIN_WORDS && words(chunk.drop(it)) >= MIN_WORDS }
      ?: return chunk to null
    return chunk.substring(0, cut).trim() to chunk.substring(cut).trim()
  }

  companion object {
    private const val MAX_CHARS = 200
    private const val PACK_CHARS = 100
    private const val MIN_WORDS = 8
    private const val MAX_OPENER_CHARS = 110
    // A sentence end counts only once whitespace follows it, so "3." then "5" is not split.
    private val sentenceBreak = Regex("(?<=[.!?])\\s+")
    private val abbreviations = setOf("mr.", "mrs.", "ms.", "dr.", "st.", "vs.", "prof.", "sr.", "jr.", "no.", "e.g.", "i.e.")
    private val initial = Regex("[A-Z]\\.")
    private val clauseEnd = Regex("[,;:](?=\\s)")

    fun split(text: String): List<String> {
      val chunker = SpeechChunker()
      return chunker.feed(text) + chunker.finish()
    }

    private fun endsWithAbbreviation(sentence: String): Boolean {
      val lastWord = sentence.trimEnd().substringAfterLast(' ')
      return lastWord.lowercase() in abbreviations || initial.matches(lastWord)
    }

    private fun words(text: String) = text.split(' ').count { it.isNotEmpty() }

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
  }
}
