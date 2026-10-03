package voice.core.copilot.frame

/**
 * Finds proper names of known characters in text bound for an image prompt. Names must never
 * reach Ideogram: the look reference images carry identity, and names pull in generic
 * (and sometimes wrong) fan-art likenesses.
 */
internal class NameGuard(names: Collection<String>) {

  private val patterns: List<Pair<String, Regex>> = names
    .flatMap { name -> listOf(name) + name.split(' ', '-').filter { it.length >= MIN_TOKEN_LENGTH && it.lowercase() !in TITLE_WORDS } }
    .distinct()
    .map { it to Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

  fun firstViolation(text: String): String? = patterns.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first

  private companion object {
    const val MIN_TOKEN_LENGTH = 3
    val TITLE_WORDS = setOf("princess", "the", "mrs", "mr", "dr", "sir", "lady", "lord")
  }
}

/**
 * Ideogram's safety filter rejected a look note ("eight nipples") outright, so anything bound
 * for a prompt is stripped of clauses containing blocked terms before it is sent.
 */
internal object PromptSanitizer {

  private val blocked = Regex(
    "\\b(nipple|nipples|breast|breasts|genital|genitals|genitalia|penis|vagina|naked|nude|nudity|sex|sexual|sex doll|erotic|gore|gory|decapitat\\w*|dismember\\w*)\\b",
    RegexOption.IGNORE_CASE,
  )

  fun clean(text: String): String {
    val kept = text.split(Regex("(?<=[,;.])\\s+")).filterNot { blocked.containsMatchIn(it) }
    return kept.joinToString(" ").trim().trimEnd(',', ';')
  }
}
