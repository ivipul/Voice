package voice.core.companion.setup

import voice.core.companion.IdeaFeature

/**
 * What the listener asks to change on a built idea, in words for Claude: the parts they ticked or unticked since
 * [before], then what they [typed].
 */
fun changeRequest(before: List<IdeaFeature>, after: List<IdeaFeature>, typed: String): String {
  val wasIncluded = before.associate { it.label to it.included }
  val added = after.filter { it.included && wasIncluded[it.label] == false }.map { it.label }
  val removed = after.filter { !it.included && wasIncluded[it.label] == true }.map { it.label }
  return buildList {
    if (added.isNotEmpty()) add("Add these parts: ${added.joinToString("; ")}.")
    if (removed.isNotEmpty()) add("Remove these parts: ${removed.joinToString("; ")}.")
    if (typed.isNotBlank()) add(typed.trim())
  }.joinToString("\n")
}
