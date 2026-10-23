package voice.core.companion.look

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.ai.ClaudeClient
import voice.core.ai.IdeogramClient
import voice.core.ai.ImageShape
import voice.core.companion.BookLook
import voice.core.companion.CompanionStores
import voice.core.companion.LookSource
import voice.core.companion.setup.profileText
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.scanner.CoverSaver
import voice.core.transcript.BookFiles
import java.io.File

/**
 * A book's look: the app's periwinkle and navy, colors picked from its cover, or a cover Earwig draws with Ideogram
 * (with the listener's own words added to the prompt) and colors picked from that. The book's original cover is kept
 * so it can always come back.
 */
@Inject
class LookMaker(
  private val bookRepository: BookRepository,
  private val stores: CompanionStores,
  private val coverSaver: CoverSaver,
  private val bookFiles: BookFiles,
  private val claude: ClaudeClient,
  private val ideogram: IdeogramClient,
) {

  suspend fun useDefault(bookId: BookId) {
    restoreOriginalCover(bookId)
    stores.look.update(bookId) { it.copy(source = LookSource.Default) }
  }

  /** Colors from the book's own cover. Returns false when the book has no cover to pick from. */
  @IgnorableReturnValue
  suspend fun useCoverColors(bookId: BookId): Boolean {
    restoreOriginalCover(bookId)
    val cover = bookRepository.get(bookId)?.content?.cover?.takeIf { it.isFile } ?: return false
    val palette = withContext(Dispatchers.Default) { decodeSmall(cover)?.let { paletteFromPixels(it.pixels(), it.width) } } ?: return false
    stores.look.update(bookId) { it.copy(source = LookSource.Cover, palette = palette) }
    return true
  }

  /** Draws a new cover with the listener's [extraPrompt] added, makes it the book's cover and takes its colors. */
  @IgnorableReturnValue
  suspend fun generateCover(bookId: BookId, extraPrompt: String): BookLook {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    keepOriginalCover(bookId)
    val profile = stores.profile.get(bookId)
    val prompt = claude.ask(
      system = COVER_PROMPT_SYSTEM,
      prompt = coverPromptRequest(profileText(profile).ifBlank { "Book: \"${book.content.name}\"" }, profile.visualStyle, extraPrompt),
      maxTokens = 600,
    )
    val bytes = ideogram.generate(prompt.trim(), ImageShape.Square)
    val bitmap = withContext(Dispatchers.Default) { checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Could not read the new cover" } }
    val file = withContext(Dispatchers.IO) {
      File(bookFiles.bookDir(bookId), "generated-cover-${System.currentTimeMillis()}.png").apply { writeBytes(bytes) }
    }
    coverSaver.save(bookId, bitmap)
    val palette = withContext(Dispatchers.Default) { bitmap.scale(SAMPLE_SIZE, SAMPLE_SIZE).let { paletteFromPixels(it.pixels(), it.width) } }
    val previous = stores.look.get(bookId).generatedCover
    val look = stores.look.update(bookId) {
      it.copy(source = LookSource.Generated, palette = palette, generatedCover = file.absolutePath, extraCoverPrompt = extraPrompt)
    }
    if (previous != null && previous != file.absolutePath) withContext(Dispatchers.IO) { File(previous).delete() }
    return look
  }

  private suspend fun keepOriginalCover(bookId: BookId) = withContext(Dispatchers.IO) {
    val original = originalCoverFile(bookId)
    if (original.isFile || stores.look.get(bookId).source == LookSource.Generated) return@withContext
    bookRepository.get(bookId)?.content?.cover?.takeIf { it.isFile }?.copyTo(original, overwrite = true)
  }

  private suspend fun restoreOriginalCover(bookId: BookId) {
    if (stores.look.get(bookId).source != LookSource.Generated) return
    val original = originalCoverFile(bookId)
    val bitmap = withContext(Dispatchers.IO) { if (original.isFile) BitmapFactory.decodeFile(original.path) else null } ?: return
    coverSaver.save(bookId, bitmap)
  }

  private fun originalCoverFile(bookId: BookId) = File(bookFiles.bookDir(bookId), "original-cover")

  private fun decodeSmall(file: File): Bitmap? = BitmapFactory.decodeFile(file.path)?.scale(SAMPLE_SIZE, SAMPLE_SIZE)

  private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

  private companion object {
    const val SAMPLE_SIZE = 64
  }
}

internal const val COVER_PROMPT_SYSTEM = "You write prompts for an image model that draws audiobook covers. Reply " +
  "with the prompt only, one paragraph of at most 90 words. Describe a striking square cover: subject, composition, " +
  "palette and style, with the title set large and legible. Keep the background light or boldly colored, never " +
  "dark. Never name real people, and describe characters by how they look instead of naming them."

internal fun coverPromptRequest(book: String, visualStyle: String, extraPrompt: String): String = buildString {
  append(book)
  if (visualStyle.isNotBlank()) append("Look of the book: $visualStyle\n")
  if (extraPrompt.isNotBlank()) append("\nThe listener wants: $extraPrompt\n")
  append("\nWrite the cover prompt. Put the title text in quotes so the image model letters it.")
}
