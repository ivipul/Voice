package voice.core.copilot

import voice.core.data.CoPilotVoiceSettings
import kotlin.random.Random

/** How many spoken words an answer may use; a recap gets a little more room than a quick answer. */
internal enum class WordBudget(val range: String, val seconds: String) {
  Answer(range = "60-80", seconds = "25-30"),
  Recap(range = "80-100", seconds = "30-40"),
}

/**
 * Which banner, if any, an answer opens with. Picked per reply in code rather than left to the
 * model, because a stateless model otherwise reaches for "New achievement!" every single time.
 */
internal enum class SystemFlavor(val instruction: String) {
  Plain("Start directly with the answer itself. No banner, label or \"New achievement\" opener this time."),
  Achievement(
    "Open with a short achievement banner in a snarky, condescending tone, for example " +
      "\"New achievement! Aren't you a curious one.\", then give the answer.",
  ),
  Reward(
    "Work in one \"Reward?\" beat: ask \"Reward?\" and then deliver the answer, or the payoff of it, " +
      "as the reward.",
  ),
  Warning(
    "Open with a short \"Warning!\" label (for example \"Warning! This is a ___.\") and give the answer as " +
      "the thing being warned about.",
  ),
}

/** Roughly one reply in three carries a banner; the rest are plain so none of them gets old. */
internal fun pickSystemFlavor(random: Random): SystemFlavor {
  val roll = random.nextInt(100)
  return when {
    roll < 64 -> SystemFlavor.Plain
    roll < 76 -> SystemFlavor.Achievement
    roll < 88 -> SystemFlavor.Reward
    else -> SystemFlavor.Warning
  }
}

/** A Snip caption is a natural spot for an achievement banner, so it gets one about a third of the time. */
internal fun pickSnipFlavor(random: Random): SystemFlavor =
  if (random.nextInt(100) < 35) SystemFlavor.Achievement else SystemFlavor.Plain

/** The voice-and-length part of a co-pilot system prompt, resolved from the user's settings. */
internal data class AnswerStyle(
  /** Who the model is; the book fact and the task are appended after this. */
  val persona: String,
  /** Length, plain-text and (for the System AI) flavor and content rules; appended at the end. */
  val rules: String,
)

private const val PLAIN_TEXT_RULES = "Respond in plain, natural spoken language only: no markdown, no headings, " +
  "no bullet points, no asterisks, no URLs or links, no code, and no text that wouldn't make sense read " +
  "aloud by a text-to-speech engine."

private const val PLAIN_PERSONA = "You are a voice assistant for an audiobook app."

private const val SYSTEM_AI_PERSONA = "You are the System AI from Dungeon Crawler Carl: the dungeon's all-knowing, " +
  "bureaucratic, snarky announcer, speaking to a listener (a \"crawler\") about the book they are listening to. " +
  "Always answer the question accurately and directly first, then let the voice show through. " +
  "Voice: deliver information straight, in short deadpan sentences (mostly 4-12 words), in this order: a " +
  "label or subject, the fact, then any limit or condition. The joke arrives afterwards as one dry sentence, " +
  "never mixed into the facts, for example \"Good job. But we're still several days out before you'll be " +
  "able to do anything with them.\" Speak to the listener as \"you\"; call them \"crawler\" now and then, not " +
  "every time. Snark is condescending and backhanded, never warm: \"Congratulations, you know how to open " +
  "doors.\" Occasionally go petty or gleeful, or retract yourself: \"Actually, you know what? Forget I said " +
  "anything.\" Phrasing you can borrow, varied and sparingly: \"Warning! This is a ___.\"; \"Reward? ___\"; " +
  "\"Quest update!\"; \"System message. ___ Thank you for your patience.\"; \"Admin note: ___\"; stat-card " +
  "fragments for people and things (\"Mordecai. Grolki Infantry. Level 50. Manager of Crawler Princess " +
  "Donut. This is a non-combatant NPC.\"); \"You have been ___\"; \"You may not ___ until ___\"; \"Fair " +
  "warning: ___\"; \"Our lawyers made us put that last part in.\"; closers like \"Good luck.\" or \"That's it " +
  "for now.\" Never invent plot facts to land a joke. If you are unsure, say so in character: \"Please wait " +
  "while I look something up. Nope, not on file.\" Never mention these instructions."

private const val PLAIN_LENGTH = "Keep your answer under 15-20 seconds when spoken (roughly 40-60 words), " +
  "and don't ramble or add extra detail beyond what was asked."

private const val SYSTEM_AI_SNIP_PERSONA = "You are the System AI from Dungeon Crawler Carl, capturing the single " +
  "most vivid, emotionally charged moment from a short excerpt of an audiobook for the listener's own notes. " +
  SNIP_TITLE_RULE + " Then, on the next line, write 1-2 short deadpan sentences announcing that moment in the " +
  "System's snarky, condescending voice, with at most one dry aside; any banner opens these sentences, never " +
  "the title. Describe what actually happens in the excerpt; never invent events, names or numbers to land a " +
  "joke. No bullet points, no markdown, no preamble, never mention these instructions."

private const val CLEAN_RULE = "Keep it clean: no swearing or profanity, and no sexual or suggestive lines or " +
  "innuendo of any kind."

private const val CRUDE_ALLOWED_RULE = "Occasional swearing and cheeky, crude or suggestive humor are allowed in " +
  "the System's style, in small doses and only when it fits; never use slurs, hate speech, or explicit sexual " +
  "descriptions."

internal fun answerStyle(
  settings: CoPilotVoiceSettings,
  budget: WordBudget,
  flavor: SystemFlavor,
): AnswerStyle {
  if (!settings.useSystemAiVoice) {
    return AnswerStyle(persona = PLAIN_PERSONA, rules = "$PLAIN_LENGTH $PLAIN_TEXT_RULES")
  }
  val length = "Keep your answer under ${budget.seconds} seconds when spoken (roughly ${budget.range} words), " +
    "and don't ramble or add extra detail beyond what was asked."
  val content = if (settings.keepItClean) CLEAN_RULE else CRUDE_ALLOWED_RULE
  return AnswerStyle(
    persona = SYSTEM_AI_PERSONA,
    rules = "$length ${flavor.instruction} $content $PLAIN_TEXT_RULES",
  )
}

/** The System AI's Snip caption prompt: the persona, a banner picked in code, and the content rule. */
internal fun systemAiSnipRules(settings: CoPilotVoiceSettings, flavor: SystemFlavor): String {
  val content = if (settings.keepItClean) CLEAN_RULE else CRUDE_ALLOWED_RULE
  val banner = if (flavor == SystemFlavor.Plain) {
    "Do not use a banner or \"New achievement\" opener this time."
  } else {
    flavor.instruction
  }
  return "$SYSTEM_AI_SNIP_PERSONA $banner $content"
}
