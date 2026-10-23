package voice.core.copilot

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.ai.ClaudeClient
import voice.core.ai.IdeogramClient
import voice.core.ai.ImageShape
import voice.core.companion.BookProfile
import voice.core.companion.CompanionStores
import voice.core.companion.SnipPictureStyle
import voice.core.data.BookId
import voice.core.logging.api.Logger
import java.io.File

/**
 * Extension point 1: draws a picture for a snip in the style the book's snip picture Extra chose (a sketchnote, an
 * infographic, a scene illustration). Claude turns the snip into an image prompt in that style, Ideogram draws it,
 * and it lands on the snip card when ready. Any failure just leaves the snip as text.
 */
@SingleIn(AppScope::class)
@Inject
class SnipPictureRunner(
  private val context: Context,
  private val claudeClient: ClaudeClient,
  private val ideogramClient: IdeogramClient,
  private val stores: CompanionStores,
  private val coPilotRepository: CoPilotRepository,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** The style to draw [bookId]'s snips in, or null when the book has none or there is no Ideogram key. */
  suspend fun styleFor(bookId: BookId): SnipPictureStyle? {
    if (!ideogramClient.isConfigured()) return null
    return stores.look.get(bookId).snipStyle?.takeIf { it.direction.isNotBlank() }
  }

  fun start(
    bookId: BookId,
    messageId: String,
    snip: String,
    style: SnipPictureStyle,
  ) {
    scope.launch {
      val path = try {
        val profile = stores.profile.get(bookId)
        val prompt = claudeClient.ask(
          system = PICTURE_PROMPT_SYSTEM,
          prompt = picturePromptRequest(snip, style, profile),
          maxTokens = 600,
        )
        val bytes = ideogramClient.generate(prompt.trim(), ImageShape.Portrait)
        withContext(Dispatchers.IO) { save(messageId, bytes) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "Snip picture failed")
        null
      }
      coPilotRepository.updateMessage(bookId, messageId) { it.copy(imagePath = path, isGeneratingImage = false) }
      // The snip may have been deleted while its picture was being drawn; then nothing points at the file.
      val snipExists = coPilotRepository.allMessagesByBook.value[bookId].orEmpty().any { it.id == messageId }
      if (path != null && !snipExists) File(path).delete()
    }
  }

  private fun save(messageId: String, bytes: ByteArray): String {
    val dir = File(context.filesDir, PICTURE_DIR).apply { mkdirs() }
    val file = File(dir, "$messageId.png")
    file.writeBytes(bytes)
    return file.absolutePath
  }

  private companion object {
    const val PICTURE_DIR = "snip-pictures"
  }
}

internal const val PICTURE_PROMPT_SYSTEM = "You write prompts for an image model. Reply with the prompt only, one " +
  "paragraph of at most 80 words, no preamble. Describe the picture concretely: subject, composition, colors and " +
  "style. Never name real people or characters from books or films; describe how they look instead. Keep any " +
  "words in the picture to a few short labels."

internal fun picturePromptRequest(snip: String, style: SnipPictureStyle, profile: BookProfile): String = buildString {
  append("Write an image prompt for a picture that captures this snip from an audiobook.\n\n")
  append("Snip:\n\"\"\"\n$snip\n\"\"\"\n\n")
  append("Picture style (${style.name}): ${style.direction}\n")
  if (profile.visualStyle.isNotBlank()) append("The book's look: ${profile.visualStyle}\n")
  if (profile.genre.isNotBlank()) append("Genre: ${profile.genre}\n")
}
