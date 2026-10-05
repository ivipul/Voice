package voice.core.copilot

/** A snip as its Inventory card shows it: the short title the model writes on the first line, then the moment. */
data class SnipText(
  val title: String?,
  val text: String,
)

/** Splits off the title line. Snips from before titles were asked for, or a reply that skipped one, are only the moment. */
fun snipTextOf(snip: String): SnipText {
  val lines = snip.lines().map(String::trim).filter(String::isNotEmpty)
  val title = lines.firstOrNull()?.let(::cleanTitle)
  return if (lines.size > 1 && !title.isNullOrEmpty() && title.length <= MAX_TITLE_LENGTH) {
    SnipText(title = title, text = lines.drop(1).joinToString(separator = " "))
  } else {
    SnipText(title = null, text = snip.trim())
  }
}

private fun cleanTitle(line: String): String = line
  .replace(TITLE_LABEL, "")
  .trim { it.isWhitespace() || it in TITLE_DECORATION }
  .removeSuffix(".")

private val TITLE_LABEL = Regex("""^\W*title\s*:\s*""", RegexOption.IGNORE_CASE)
private const val TITLE_DECORATION = "#*_\"'“”"
private const val MAX_TITLE_LENGTH = 60
