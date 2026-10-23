package voice.core.companion.extras

import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.companion.BookKind
import voice.core.companion.BookProfile
import voice.core.companion.CompanionStores
import voice.core.companion.Extra
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaStatus
import voice.core.companion.SceneIndex
import voice.core.companion.Schema
import voice.core.companion.SnipPictureStyle
import voice.core.companion.companionJson
import voice.core.companion.firstCueMs
import voice.core.companion.setup.profileText
import voice.core.data.BookId
import java.util.UUID

/**
 * Writes shelf Extras with Claude: one self-contained page per idea, checked against the sandbox rules before it goes
 * on the shelf, and repaired when the viewer reports an error. Also turns snip picture ideas into an art direction.
 */
@Inject
class ExtraBuilder(
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
) {

  /** Builds [card] into an Extra on the book's shelf and returns it. */
  suspend fun buildShelfExtra(bookId: BookId, card: IdeaCard): Extra {
    val profile = stores.profile.get(bookId)
    val scene = stores.scene.get(bookId)
    val brief = briefOf(card)
    val html = validated(claude.write(system = BUILDER_SYSTEM, prompt = buildPrompt(profile, scene, brief)))
    val topicId = topicOf(html)?.takeIf { id -> scene.entries.any { it.id == id } }
    val extra = Extra(
      id = UUID.randomUUID().toString(),
      title = card.title,
      description = card.description,
      kind = card.kind,
      html = html,
      createdAtMs = System.currentTimeMillis(),
      // Asked for on the spot, so it is about what the listener already heard: open it at once.
      unlockAtMs = if (card.requestedAtMs != null) 0L else topicId?.let(scene::firstCueMs) ?: 0L,
      topicId = topicId,
      brief = brief,
    )
    stores.shelf.update(bookId) { it.copy(extras = it.extras + extra) }
    if (topicId != null) {
      stores.scene.update(bookId) { index ->
        index.copy(entries = index.entries.map { if (it.id == topicId && it.extraId == null) it.copy(extraId = extra.id) else it })
      }
    }
    return extra
  }

  /** Sends the page and the error the viewer saw back to Claude, and saves the fixed page in its place. */
  @IgnorableReturnValue
  suspend fun repair(bookId: BookId, extraId: String, error: String): Extra {
    val extra = checkNotNull(stores.shelf.get(bookId).extras.firstOrNull { it.id == extraId }) { "No Extra $extraId" }
    val fixed = validated(claude.write(system = BUILDER_SYSTEM, prompt = repairPrompt(extra, error)))
    val repaired = extra.copy(html = fixed, lastError = null)
    stores.shelf.update(bookId) { shelf -> shelf.copy(extras = shelf.extras.map { if (it.id == extraId) repaired else it }) }
    return repaired
  }

  /** Remembers the last error a page hit, so the viewer can offer to fix it. */
  suspend fun reportError(bookId: BookId, extraId: String, error: String?) {
    stores.shelf.update(bookId) { shelf ->
      shelf.copy(extras = shelf.extras.map { if (it.id == extraId) it.copy(lastError = error) else it })
    }
  }

  suspend fun delete(bookId: BookId, extraId: String) {
    stores.shelf.update(bookId) { shelf -> shelf.copy(extras = shelf.extras.filterNot { it.id == extraId }) }
    stores.scene.update(bookId) { index ->
      index.copy(entries = index.entries.map { if (it.extraId == extraId) it.copy(extraId = null) else it })
    }
    stores.ideas.update(bookId) { board ->
      board.copy(cards = board.cards.map { if (it.extraId == extraId) it.copy(extraId = null, status = IdeaStatus.Suggested) else it })
    }
  }

  /** Turns a snip picture idea into the art direction every snip picture of the book is drawn in. */
  @IgnorableReturnValue
  suspend fun buildSnipStyle(bookId: BookId, card: IdeaCard): SnipPictureStyle {
    val profile = stores.profile.get(bookId)
    val answer = companionJson.decodeFromString<StyleAnswer>(
      claude.askJson(system = STYLE_SYSTEM, prompt = stylePrompt(profile, card), schema = STYLE_SCHEMA),
    )
    val style = SnipPictureStyle(name = answer.name, direction = answer.direction)
    stores.look.update(bookId) { it.copy(snipStyle = style) }
    return style
  }

  private fun briefOf(card: IdeaCard): String = buildString {
    append("${card.title}: ${card.description}")
    if (card.kind.isNotBlank()) append(" (a ${card.kind})")
    if (card.why.isNotBlank()) append("\nWhy: ${card.why}")
    append(card.featureText())
  }
}

/** Takes the page out of Claude's reply and checks the sandbox rules; throws with the reason when it breaks them. */
internal fun validated(reply: String): String {
  val html = extractHtml(reply)
  check(html.length <= MAX_PAGE_CHARS) { "The page came out too big (${html.length / 1000} KB)" }
  check(Regex("<body", RegexOption.IGNORE_CASE).containsMatchIn(html)) { "The page has no body" }
  val external = Regex("""<(script|link|img|iframe)[^>]+(src|href)\s*=\s*["']?\s*(https?:)?//""", RegexOption.IGNORE_CASE)
  check(!external.containsMatchIn(html)) { "The page loads files from the internet, which the sandbox blocks" }
  return html
}

internal fun extractHtml(reply: String): String {
  val fenced = Regex("```(?:html)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(reply)?.groupValues?.get(1)
  val text = (fenced ?: reply).trim()
  val start = Regex("<!doctype html|<html", RegexOption.IGNORE_CASE).find(text)?.range?.first ?: 0
  val end = text.lastIndexOf("</html>", ignoreCase = true).takeIf { it >= 0 }?.plus("</html>".length) ?: text.length
  return text.substring(start, end)
}

/** The topic a page says it belongs to, from its `<meta name="earwig-topic">`. */
internal fun topicOf(html: String): String? = Regex(
  """<meta\s+name=["']earwig-topic["']\s+content=["']([^"']+)["']""",
  RegexOption.IGNORE_CASE,
).find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "none" }

private const val MAX_PAGE_CHARS = 400_000

internal val BUILDER_SYSTEM = "You build small, polished, interactive companion pages (Extras) for an audiobook " +
  "app. Each page does one thing well for this particular book, looks designed rather than generated, and works " +
  "the first time. Write the complete page in one ```html block and nothing else around it. Never reveal story " +
  "events beyond what the listener has heard: anything about the plot comes from earwig.transcript() or " +
  "earwig.ask() at runtime, never baked into the page.\n\n$EXTRA_CONTRACT"

internal fun buildPrompt(profile: BookProfile, scene: SceneIndex, brief: String): String = buildString {
  append(profileText(profile))
  if (profile.visualStyle.isNotBlank()) append("Look of the book: ${profile.visualStyle}\n")
  append("\nBuild this Extra:\n$brief\n")
  if (scene.entries.isNotEmpty()) {
    val label = if (scene.kind == BookKind.Fiction) "characters" else "topics"
    append("\nThe book's $label, by id. If the page is about one of them, add ")
    append("<meta name=\"earwig-topic\" content=\"ID\"> to its head so it unlocks when the book reaches it:\n")
    scene.entries.take(MAX_TOPICS_IN_PROMPT).forEach { append("- ${it.id}: ${it.name}\n") }
  }
}

internal fun repairPrompt(extra: Extra, error: String): String = buildString {
  append("This Extra page hit an error in the app. Fix it and return the whole corrected page.\n\n")
  append("What it is for: ${extra.brief.ifBlank { extra.title + ": " + extra.description }}\n\n")
  append("Error: $error\n\nThe page:\n```html\n${extra.html}\n```")
}

private const val MAX_TOPICS_IN_PROMPT = 60

@Serializable
internal data class StyleAnswer(
  val name: String,
  val direction: String,
)

internal const val STYLE_SYSTEM = "You write art direction for the pictures an audiobook app draws for snipped " +
  "moments. Given a book and the style idea, name the style in 2 to 4 words and write the direction: one or two " +
  "sentences for an image model on medium, composition, palette and how much text (keep words to a few short " +
  "labels). Never name the book, its author or its characters."

internal fun stylePrompt(profile: BookProfile, card: IdeaCard): String =
  profileText(profile) + "\nLook of the book: ${profile.visualStyle}\n\nStyle idea: ${card.title}: ${card.description}" +
    card.featureText()

/** The parts the listener kept ticked on the card, and the ones they unticked. */
internal fun IdeaCard.featureText(): String = buildString {
  if (includedFeatures.isNotEmpty()) append("\nInclude: ${includedFeatures.joinToString("; ")}")
  if (leftOutFeatures.isNotEmpty()) append("\nLeave out: ${leftOutFeatures.joinToString("; ")}")
}

internal val STYLE_SCHEMA = Schema.obj(
  "name" to Schema.string(),
  "direction" to Schema.string(),
)
