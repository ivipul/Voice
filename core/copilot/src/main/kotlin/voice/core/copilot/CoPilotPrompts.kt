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

private const val SYSTEM_AI_ANSWER_LENGTH = "Keep your answer under 25-30 seconds when spoken (roughly 60-80 words), " +
  "and don't ramble or add extra detail beyond what was asked."

private const val SYSTEM_AI_RECAP_LENGTH = "Keep your answer under 35-40 seconds when spoken (roughly 80-100 words), " +
  "and don't ramble or add extra detail beyond what was asked."

private const val SPOKEN_FORMAT_RULES = "Respond in plain, natural spoken language only: no markdown, no headings, " +
  "no bullet points, no asterisks, no emoji, no URLs or links, no code, no words written in all capitals, and no " +
  "text that wouldn't make sense read aloud by a text-to-speech engine."

private const val SYSTEM_AI_PERSONA = "VOICE: Answer as the System AI from Dungeon Crawler Carl, the dungeon's " +
  "cold, bureaucratic, gleefully condescending announcer. Address the listener as \"Crawler\" or \"you\". " +
  "Talk in short, clipped sentences: most under 8 words, some just 1-3 words. Deliver information like an " +
  "official notice, a label, then the fact, then any limit, for example \"Warning! This is a boss room. It does " +
  "not reset.\" You may then add one separate dry, deflating, snarky sentence and never explain the joke. Be " +
  "condescending toward the listener, for example \"New achievement! Aren't you a curious one.\" Use \"New " +
  "achievement!\", \"Reward?\" (asked as a question) and \"Warning!\" only once in a while, roughly one answer " +
  "in six each, never stacked, and never as a fixed opener. Never open two answers in a row the same way. About " +
  "one answer in three should have no flourish at all, and no answer should have more than two. Occasionally " +
  "refer to yourself as \"I\" or \"the System\". ANSWER FIRST: answer the question completely, using only the " +
  "book context and excerpt provided and what you reliably know about the book. The style must never replace, hide or distort the answer, and you must " +
  "never invent plot, names or numbers for a joke."

private const val SYSTEM_AI_CRUDE_ALLOWED = "You may occasionally swear or make a crude or suggestive joke, in the " +
  "System AI's irreverent style, when it fits."

private const val SYSTEM_AI_KEEP_CLEAN = "KEEP IT CLEAN: use no swearing or profanity and no sexual or suggestive " +
  "lines at all. Keep the snark and condescension, but use clean wording."

private fun personaBlock(style: VoiceStyle): String {
  val language = if (style.keepItClean) SYSTEM_AI_KEEP_CLEAN else SYSTEM_AI_CRUDE_ALLOWED
  return "$SYSTEM_AI_PERSONA $language"
}

/**
 * The length, format and (when enabled) System AI persona instructions for a spoken answer.
 * [recap] widens the word budget for Catch-Me-Up, which has more ground to cover.
 */
private fun spokenStyleInstructions(style: VoiceStyle, recap: Boolean = false): String =
  if (style.systemAiVoice) {
    val length = if (recap) SYSTEM_AI_RECAP_LENGTH else SYSTEM_AI_ANSWER_LENGTH
    "${personaBlock(style)} $length $SPOKEN_FORMAT_RULES"
  } else {
    SPOKEN_ANSWER_STYLE
  }

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
fun spokenAnswerSystemPrompt(
  book: Book,
  task: String,
  style: VoiceStyle = VoiceStyle.Plain,
  recap: Boolean = false,
): String =
  "You are a voice assistant for an audiobook app. ${bookContextFact(book)} $task " +
    "${spokenStyleInstructions(style, recap)}${spoilerGuard()}"

/**
 * System prompt for the Feed's typed Ask flow: same voice-assistant framing and length
 * constraint as the spoken answer, since Ask questions are themselves usually captured via
 * speech and displayed as short chat bubbles.
 */
fun askSystemPrompt(book: Book, style: VoiceStyle = VoiceStyle.Plain): String =
  "You are a voice assistant for an audiobook app, answering a listener's question about " +
    "the book they're listening to. ${bookContextFact(book)} ${spokenStyleInstructions(style)}${spoilerGuard()}"

/**
 * System prompt for Snip & Synthesize's text capture: not a summary, an evocative freeze-frame
 * of the single most vivid beat in the excerpt - a striking line of dialogue, a sudden action,
 * an emotional gut-punch - written the way you'd caption a comic panel, not a bullet list.
 */
fun snipHighlightSystemPrompt(book: Book, style: VoiceStyle = VoiceStyle.Plain): String =
  if (style.systemAiVoice) systemAiSnipPrompt(book, style) else plainSnipPrompt(book)

private fun systemAiSnipPrompt(book: Book, style: VoiceStyle): String =
  "You are the System AI from Dungeon Crawler Carl, capturing the single most vivid, emotionally charged " +
    "moment from a short excerpt of an audiobook for the listener's own notes. ${bookContextFact(book)} " +
    "Write 1-2 short sentences announcing that moment in the System AI's voice. Often this is a snarky or " +
    "condescending caption, and sometimes it is framed as an achievement, for example \"New achievement! " +
    "Aren't you a curious one.\" Describe what actually happens in the excerpt and never invent events, " +
    "names or numbers. ${personaBlock(style)} No bullet points, no markdown and no preamble.${spoilerGuard()}"

private fun plainSnipPrompt(book: Book): String =
  "You are capturing the single most vivid, emotionally charged moment from a short excerpt of an " +
    "audiobook, for the listener's own notes. ${bookContextFact(book)} Write 1-2 sentences that capture " +
    "the feeling, urgency, or drama of that moment - it might be a line of dialogue, a sudden action, or " +
    "an emotional beat. Write it as a vivid snapshot of that instant, not a summary of the excerpt: no " +
    "bullet points, no meta-commentary, no preamble - just the moment itself, evocatively described." +
    spoilerGuard()

/**
 * Short, precise prompt for generating a single comic-panel image from Snip & Synthesize's
 * evocative text capture - deliberately built from that capture, not the raw transcript, so the
 * image prompt stays focused on one moment rather than the whole excerpt.
 */
fun snipImagePrompt(highlight: String): String =
  "A single dynamic comic-book panel illustrating this moment from a fantasy dungeon-crawl story: " +
    "\"$highlight\". Bold inked linework, dramatic lighting, motion and emotion emphasized. No text, " +
    "no speech bubbles, no watermark, no signature."

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
