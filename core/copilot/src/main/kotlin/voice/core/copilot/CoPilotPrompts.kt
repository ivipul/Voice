package voice.core.copilot

import voice.core.companion.BookKind
import voice.core.companion.BookProfile
import voice.core.data.Book
import kotlin.time.Duration.Companion.milliseconds

/**
 * Grounds every answer in the book and the listener's position. The profile, when the book has been researched,
 * tells Claude what kind of book it is; the spoiler rule keeps it from volunteering anything past the position.
 */
internal fun bookContext(book: Book, profile: BookProfile?): String {
  val author = book.content.author?.takeIf { it.isNotBlank() }?.let { " by $it" }.orEmpty()
  val kind = when (profile?.kind) {
    BookKind.Fiction -> " It is fiction."
    BookKind.NonFiction -> " It is non-fiction."
    null -> ""
  }
  val about = profile?.summary?.takeIf { it.isNotBlank() }?.let { " About the book: $it" }.orEmpty()
  return "The listener is listening to the audiobook \"${book.content.name}\"$author.$kind$about " +
    "They are at ${book.position.milliseconds.spoken()} of ${book.duration.milliseconds.spoken()}. " +
    "Never reveal or hint at anything that happens later in the book than their position."
}

private fun kotlin.time.Duration.spoken(): String = toComponents { hours, minutes, _, _ ->
  if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

/** Spoken answers are read out by the phone's voice, so they have to sound right without any formatting. */
internal const val SPOKEN_RULES = "Answer in two to four short sentences that sound natural read aloud. Plain text " +
  "only: no markdown, no lists, no headings, no emoji. Base the answer on the excerpt; if the excerpt doesn't " +
  "cover the question, say so briefly and answer from what is generally known about the book up to this point."

internal fun askSystemPrompt(book: Book, profile: BookProfile?): String =
  "You are a friendly companion who knows the audiobook the listener is hearing and answers their questions " +
    "about it. ${bookContext(book, profile)} $SPOKEN_RULES"

internal fun autoIdentifySystemPrompt(book: Book, profile: BookProfile?): String {
  val task = if (profile?.kind == BookKind.NonFiction) {
    "Explain the idea being discussed right now, based on the last minute shown below, so the listener can " +
      "follow along. Use the older context only to understand it."
  } else {
    "Explain what is happening right now and who is involved, based on the last minute shown below. Use the " +
      "older context only to understand who and what is involved; don't summarize it."
  }
  return "You are a companion for an audiobook listener who just asked \"what's going on?\". " +
    "${bookContext(book, profile)} $task $SPOKEN_RULES"
}

internal fun catchUpSystemPrompt(book: Book, profile: BookProfile?): String {
  val focus = if (profile?.kind == BookKind.NonFiction) "the key points and ideas" else "what happened"
  return "You are a companion for an audiobook listener. ${bookContext(book, profile)} Give a spoken recap of " +
    "$focus in the last 30 minutes of the book, shown below. Start with the exact words \"In the last 30 " +
    "minutes,\" and continue from there. Use up to six sentences. Plain text only: no markdown, no lists."
}

/** Every snip opens with a title line, which [snipTextOf] splits off for the Notebook's snip cards. */
internal const val SNIP_TITLE_RULE = "Start with a short title for the moment, 2 to 6 words, alone on the first " +
  "line with no label, quotes or markdown."

internal fun snipSystemPrompt(book: Book, profile: BookProfile?): String {
  val what = if (profile?.kind == BookKind.Fiction) {
    "the moment in the story: what happens, who is involved and why it matters"
  } else {
    "the concept or idea being discussed: what it is, and the key point the author makes about it"
  }
  return "The listener tapped Snip to keep a note of what they are hearing right now. " +
    "${bookContext(book, profile)} The excerpt comes in two parts: what was said just before the snip, and the " +
    "few seconds just after it. Summarize $what. Weight the lines closest to the snip point the most; older " +
    "lines are only context. $SNIP_TITLE_RULE Then, on the next line, write two or three plain sentences. " +
    "No bullet points, no preamble, no markdown."
}

fun userQuestionPrompt(question: String, transcriptWindow: String?): String =
  if (transcriptWindow != null) {
    "Recent excerpt from the book (the last 30 minutes):\n\"\"\"\n$transcriptWindow\n\"\"\"\n\nQuestion: $question"
  } else {
    "There is no transcript yet, so answer from what is generally known about the book.\n\nQuestion: $question"
  }

fun transcriptOnlyPrompt(transcriptWindow: String): String =
  "Excerpt from the book:\n\"\"\"\n$transcriptWindow\n\"\"\""

fun snipPrompt(before: String, after: String?): String = buildString {
  append("Just before the snip:\n\"\"\"\n$before\n\"\"\"\n")
  if (!after.isNullOrBlank()) append("\nJust after the snip:\n\"\"\"\n$after\n\"\"\"\n")
}

/**
 * Auto-Identify's prompt: up to 30 minutes of context plus the last 60 seconds called out separately as the thing
 * to actually explain.
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
