package voice.core.copilot

import voice.core.data.Book
import voice.core.data.CoPilotVoiceSettings
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manually-maintained list of terms Gemini should avoid volunteering unless the user asks
 * about them directly - a lightweight guard against the co-pilot spoiling a plot point the
 * user hasn't reached yet. Empty by default; add entries as specific spoilers come up.
 */
val spoilerBlocklist: List<String> = emptyList()

/**
 * The book title + current timestamp fact is injected as a hard system-prompt fact on every
 * call, regardless of whether the Jev router decided the question itself needs the local
 * transcript - it grounds Gemini in what the user is listening to even for lore questions
 * answered from Gemini's general knowledge.
 */
private fun bookContextFact(book: Book): String {
  val timestamp = book.position.milliseconds.toString()
  return "The user is listening to \"${book.content.name}\" and is currently at timestamp $timestamp in the book."
}

private fun spoilerGuard(): String {
  if (spoilerBlocklist.isEmpty()) return ""
  return " Avoid mentioning or spoiling the following unless the user explicitly asks about them: " +
    spoilerBlocklist.joinToString(", ") + "."
}

/**
 * System prompt for a spoken answer (Open Mic, Auto-Identify, Catch-Me-Up): a short,
 * TTS-friendly response grounded in the current book and position, in the voice [style] sets.
 */
internal fun spokenAnswerSystemPrompt(book: Book, task: String, style: AnswerStyle): String =
  "${style.persona} ${bookContextFact(book)} $task ${style.rules}${spoilerGuard()}"

/**
 * System prompt for the Feed's typed Ask flow: same persona and length constraint as the spoken
 * answer, since Ask questions are themselves usually captured via speech and displayed as short
 * chat bubbles.
 */
internal fun askSystemPrompt(book: Book, style: AnswerStyle): String =
  "${style.persona} You are answering a listener's question about the book they're listening to. " +
    "${bookContextFact(book)} ${style.rules}${spoilerGuard()}"

/**
 * System prompt for Snip & Synthesize's text capture: not a summary, an evocative freeze-frame
 * of the single most vivid beat in the excerpt - a striking line of dialogue, a sudden action,
 * an emotional gut-punch - written the way you'd caption a comic panel, not a bullet list.
 */
internal fun snipHighlightSystemPrompt(book: Book, settings: CoPilotVoiceSettings, flavor: SystemFlavor): String =
  if (settings.useSystemAiVoice) {
    "${bookContextFact(book)} ${systemAiSnipRules(settings, flavor)}${spoilerGuard()}"
  } else {
    plainSnipHighlightSystemPrompt(book)
  }

/** Every snip opens with a title line, which [snipTextOf] splits off for the Inventory's snip cards. */
internal const val SNIP_TITLE_RULE = "Start with a short title for the moment, 2 to 6 words, alone on the first " +
  "line with no label, quotes or markdown."

private fun plainSnipHighlightSystemPrompt(book: Book): String =
  "You are capturing the single most vivid, emotionally charged moment from a short excerpt of an " +
    "audiobook, for the listener's own notes. ${bookContextFact(book)} $SNIP_TITLE_RULE Then, on the next " +
    "line, write 1-2 sentences that capture the feeling, urgency, or drama of that moment - it might be a " +
    "line of dialogue, a sudden action, or an emotional beat. Write it as a vivid snapshot of that instant, " +
    "not a summary of the excerpt: no bullet points, no meta-commentary, no preamble - just the title and " +
    "the moment itself, evocatively described." +
    spoilerGuard()

fun userQuestionPrompt(question: String, transcriptWindow: String?): String =
  if (transcriptWindow != null) {
    "Recent excerpt from the book:\n\"\"\"\n$transcriptWindow\n\"\"\"\n\nQuestion: $question"
  } else {
    question
  }

fun transcriptOnlyPrompt(transcriptWindow: String): String =
  "Excerpt from the book:\n\"\"\"\n$transcriptWindow\n\"\"\""

/**
 * Auto-Identify's prompt: up to ~30 minutes of context (so Gemini knows who/what is involved)
 * plus the last 60 seconds called out separately as the thing to actually explain. The system
 * prompt's task text is what tells Gemini to prioritize the 60s block over the context.
 */
fun autoIdentifyPrompt(last30Minutes: String?, last60Seconds: String): String {
  val contextBlock = if (last30Minutes != null) {
    "Context - roughly the last 30 minutes of the book, for background only:\n\"\"\"\n$last30Minutes\n\"\"\"\n\n"
  } else {
    ""
  }
  return contextBlock +
    "What to explain - the last 60 seconds:\n\"\"\"\n$last60Seconds\n\"\"\""
}
