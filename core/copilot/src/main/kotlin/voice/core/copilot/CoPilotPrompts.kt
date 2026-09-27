package voice.core.copilot

import voice.core.data.Book
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manually-maintained list of terms Gemini should avoid volunteering unless the user asks
 * about them directly - a lightweight guard against the co-pilot spoiling a plot point the
 * user hasn't reached yet. Empty by default; add entries as specific spoilers come up.
 */
val spoilerBlocklist: List<String> = emptyList()

private const val SPOKEN_ANSWER_STYLE = "Keep your answer under 15-20 seconds when spoken (roughly 40-60 words), " +
  "and don't ramble or add extra detail beyond what was asked. Respond in plain, natural spoken language only: " +
  "no markdown, no headings, no bullet points, no asterisks, no URLs or links, no code, and no text that " +
  "wouldn't make sense read aloud by a text-to-speech engine."

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
 * TTS-friendly response grounded in the current book and position.
 */
fun spokenAnswerSystemPrompt(book: Book, task: String): String =
  "You are a voice assistant for an audiobook app. ${bookContextFact(book)} $task $SPOKEN_ANSWER_STYLE${spoilerGuard()}"

/**
 * System prompt for the Feed's typed Ask flow: same voice-assistant framing and length
 * constraint as the spoken answer, since Ask questions are themselves usually captured via
 * speech and displayed as short chat bubbles.
 */
fun askSystemPrompt(book: Book): String =
  "You are a voice assistant for an audiobook app, answering a listener's question about " +
    "the book they're listening to. ${bookContextFact(book)} $SPOKEN_ANSWER_STYLE${spoilerGuard()}"

/**
 * System prompt for Snip & Synthesize: a short written (not spoken) 3-bullet extraction,
 * since this is saved silently to the Feed rather than read aloud.
 */
fun snipSystemPrompt(book: Book): String =
  "You are summarizing a short excerpt of an audiobook for the listener's own notes. " +
    "${bookContextFact(book)} Extract the core insights from the excerpt into exactly 3 short bullet points " +
    "(one line each, starting with \"- \"), covering only what happened in the excerpt. No preamble, " +
    "no closing remarks, nothing beyond the 3 bullets.${spoilerGuard()}"

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
