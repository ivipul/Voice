package voice.core.companion.look

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.anthropic.models.messages.OutputConfig
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.ai.IdeogramClient
import voice.core.ai.ImageShape
import voice.core.companion.BookLook
import voice.core.companion.CompanionStores
import voice.core.companion.LookSource
import voice.core.companion.Schema
import voice.core.companion.companionJson
import voice.core.companion.setup.profileText
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.scanner.CoverSaver
import voice.core.transcript.BookFiles
import java.io.File

/**
 * A book's look: the app's cream, red and black, colors picked from its cover, or a cover Earwig draws with Ideogram
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
    val previous = stores.look.get(bookId)
    val plan = companionJson.decodeFromString(
      CoverPlan.serializer(),
      claude.askJson(
        system = COVER_PROMPT_SYSTEM,
        prompt = coverPromptRequest(
          book = profileText(profile).ifBlank { "Book: \"${book.content.name}\"" },
          visualStyle = profile.visualStyle,
          extraPrompt = extraPrompt,
          lastComposition = previous.coverComposition,
        ),
        schema = COVER_SCHEMA,
        maxTokens = 2_000,
        effort = OutputConfig.Effort.LOW,
      ),
    )
    val bytes = ideogram.generate(plan.prompt.trim(), ImageShape.Square)
    val bitmap = withContext(Dispatchers.Default) { checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Could not read the new cover" } }
    val file = withContext(Dispatchers.IO) {
      File(bookFiles.bookDir(bookId), "generated-cover-${System.currentTimeMillis()}.png").apply { writeBytes(bytes) }
    }
    coverSaver.save(bookId, bitmap)
    val palette = withContext(Dispatchers.Default) { bitmap.scale(SAMPLE_SIZE, SAMPLE_SIZE).let { paletteFromPixels(it.pixels(), it.width) } }
    val look = stores.look.update(bookId) {
      it.copy(
        source = LookSource.Generated,
        palette = palette,
        generatedCover = file.absolutePath,
        extraCoverPrompt = extraPrompt,
        coverComposition = plan.composition,
        displayFont = plan.font.takeIf { font -> font in COVER_FONTS } ?: it.displayFont,
      )
    }
    val previousCover = previous.generatedCover
    if (previousCover != null && previousCover != file.absolutePath) withContext(Dispatchers.IO) { File(previousCover).delete() }
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

/** The layouts a generated cover can take, all in the same poster style. */
internal val COVER_COMPOSITIONS = mapOf(
  "stacked" to "Stacked title: two or three lines of huge title lettering in two colors, each line running the full " +
    "width, with the main figure standing in front of the letters and covering part of them.",
  "wall" to "Tall condensed wall: one ultra-condensed title word filling almost the full height, a tiny kicker line " +
    "above it and a widely spaced sub-label under it, with the figure in front.",
  "rounded" to "Fat rounded title: very fat, rounded lettering centered across the top, a small widely spaced kicker " +
    "above it, and the figures with their soft cast shadows below.",
  "giant" to "Giant letterform: one enormous number or letter as the backdrop, the figure bursting through it, and a " +
    "small label in a corner.",
  "serif" to "Tone-on-tone serif: a tall, high-contrast serif title in a darker or lighter shade of the background, " +
    "partly hidden by large figures in front.",
  "editorial" to "Editorial poster: a bold two-color sans title across the top over one flat illustrated symbol of the " +
    "book's idea, with a soft glow behind it.",
)

/** The app's chapter name faces, and how a cover letters its title to match each one. */
internal val COVER_FONTS = mapOf(
  "anton" to "heavy condensed sans serif, like Anton",
  "condensed" to "ultra-condensed tall sans serif, like Big Shoulders Display",
  "serif" to "tall elegant high-contrast serif, like Instrument Serif",
  "classic" to "bold high-contrast didone serif, like Playfair Display",
  "rounded" to "fat rounded retro display letters, like Bagel Fat One",
)

internal val COVER_PROMPT_SYSTEM = buildString {
  append("You plan a square audiobook cover and write the prompt an image model draws it from. The covers share one ")
  append("poster style: flat vector illustration with simple cel shading, crisp shapes and a soft hard-edged cast ")
  append("shadow, with a touch of paper grain. One flat, saturated background color fills the whole canvas to every ")
  append("edge, with no scenery, gradient or photo behind; deep jewel tones are fine, never black. Use two or three ")
  append("more colors and one bright accent, chosen for the book's mood. The huge title lettering is the main ")
  append("graphic and stays readable; add the author's name as small, widely spaced capitals. Fiction shows its ")
  append("main character as an expressive, dynamic figure described by how they look; non-fiction shows a bold ")
  append("symbol, object or scene of its idea. Never name real people, brands or logos.\n\nCompositions:\n")
  COVER_COMPOSITIONS.forEach { (id, text) -> append("- $id: $text\n") }
  append("\nTitle faces (the app sets chapter names in the same face):\n")
  COVER_FONTS.forEach { (id, text) -> append("- $id: $text\n") }
  append("\nPick the composition and face that suit the book. The prompt is one paragraph of at most 110 words: ")
  append("composition, lettering in the chosen face, figure or symbol, background color and palette, and style. Put ")
  append("every piece of cover text in quotes so the image model letters it exactly.")
}

internal val COVER_SCHEMA = Schema.obj(
  "composition" to Schema.enum(COVER_COMPOSITIONS.keys.toList()),
  "font" to Schema.enum(COVER_FONTS.keys.toList()),
  "prompt" to Schema.string(),
)

@Serializable
internal data class CoverPlan(
  val composition: String,
  val font: String,
  val prompt: String,
)

internal fun coverPromptRequest(book: String, visualStyle: String, extraPrompt: String, lastComposition: String): String =
  buildString {
    append(book)
    if (visualStyle.isNotBlank()) append("Look of the book: $visualStyle\n")
    if (extraPrompt.isNotBlank()) append("\nThe listener wants: $extraPrompt\n")
    if (lastComposition.isNotBlank()) append("\nThe last cover used the $lastComposition composition; pick a different one.\n")
    append("\nPlan the cover.")
  }
