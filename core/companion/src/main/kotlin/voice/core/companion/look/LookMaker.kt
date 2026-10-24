package voice.core.companion.look

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.anthropic.models.messages.OutputConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.ai.IdeogramClient
import voice.core.ai.ImageShape
import voice.core.companion.BookLook
import voice.core.companion.CompanionStores
import voice.core.companion.GeneratedCover
import voice.core.companion.LookSource
import voice.core.companion.Schema
import voice.core.companion.companionJson
import voice.core.companion.setup.profileText
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.scanner.CoverSaver
import voice.core.transcript.BookFiles
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A book's look: the app's cream, red and black, colors picked from its cover, or a cover Earwig draws with Ideogram
 * (with the listener's own words added to the prompt) and colors picked from that. The book's original cover is kept
 * so it can always come back.
 */
@SingleIn(AppScope::class)
@Inject
class LookMaker(
  private val bookRepository: BookRepository,
  private val stores: CompanionStores,
  private val coverSaver: CoverSaver,
  private val bookFiles: BookFiles,
  private val claude: ClaudeClient,
  private val ideogram: IdeogramClient,
) {

  private val drawLock = Mutex()

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

  /**
   * Picking the New cover option: the newest drawn cover becomes the book's cover again. Only when none was drawn
   * yet does it draw one, and a draw already running (like the one setup starts ahead of time) is waited for.
   */
  suspend fun useLatestGenerated(bookId: BookId) {
    val latest = drawLock.withLock {
      stores.look.get(bookId).coverHistory.firstOrNull() ?: draw(bookId, stores.look.get(bookId).extraCoverPrompt)
    }
    useGenerated(bookId, latest)
  }

  /** Draws a new cover with the listener's [extraPrompt] added, makes it the book's cover and takes its colors. */
  suspend fun generateCover(bookId: BookId, extraPrompt: String) {
    val cover = drawLock.withLock { draw(bookId, extraPrompt) }
    useGenerated(bookId, cover)
  }

  /** Draws a first cover ahead of time, so it is ready when the listener looks; the book keeps its look meanwhile. */
  suspend fun prepareCover(bookId: BookId) {
    if (!ideogram.isConfigured() || !claude.isConfigured()) return
    drawLock.withLock {
      val look = stores.look.get(bookId)
      if (look.coverHistory.isEmpty()) {
        val _ = draw(bookId, look.extraCoverPrompt)
      }
    }
  }

  /** Makes [cover], drawn earlier, the book's cover, with its colors and chapter-name face. */
  suspend fun useGenerated(bookId: BookId, cover: GeneratedCover) {
    keepOriginalCover(bookId)
    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(cover.path) } ?: error("That cover is gone")
    coverSaver.save(bookId, bitmap)
    val palette = withContext(Dispatchers.Default) { bitmap.scale(SAMPLE_SIZE, SAMPLE_SIZE).let { paletteFromPixels(it.pixels(), it.width) } }
    stores.look.update(bookId) {
      it.copy(
        source = LookSource.Generated,
        palette = palette,
        generatedCover = cover.path,
        displayFont = cover.font.takeIf { font -> font in COVER_FONTS } ?: it.displayFont,
      )
    }
  }

  /**
   * Draws a cover and adds it to the book's history without using it. The book's own cover goes along as the image
   * to retheme, so the new one keeps its essence.
   */
  private suspend fun draw(bookId: BookId, extraPrompt: String): GeneratedCover {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    stores.look.update(bookId) { it.copy(drawingSinceMs = System.currentTimeMillis(), extraCoverPrompt = extraPrompt) }
    try {
      val reference = referenceCover(bookId)
      val profile = stores.profile.get(bookId)
      val plan = companionJson.decodeFromString(
        CoverPlan.serializer(),
        claude.askJson(
          system = COVER_PROMPT_SYSTEM,
          prompt = coverPromptRequest(
            book = profileText(profile).ifBlank { "Book: \"${book.content.name}\"" },
            visualStyle = profile.visualStyle,
            extraPrompt = extraPrompt,
            lastComposition = stores.look.get(bookId).coverComposition,
            hasReference = reference != null,
          ),
          schema = COVER_SCHEMA,
          maxTokens = 2_000,
          effort = OutputConfig.Effort.LOW,
        ),
      )
      val bytes = ideogram.generate(plan.prompt.trim(), ImageShape.Square, source = reference)
      val file = withContext(Dispatchers.IO) {
        File(bookFiles.bookDir(bookId), "generated-cover-${System.currentTimeMillis()}.png").apply { writeBytes(bytes) }
      }
      val cover = GeneratedCover(path = file.absolutePath, composition = plan.composition, font = plan.font)
      val look = stores.look.update(bookId) {
        it.copy(coverComposition = plan.composition, generatedCovers = listOf(cover) + it.coverHistory)
      }
      dropOldCovers(bookId, look)
      return cover
    } finally {
      stores.look.update(bookId) { it.copy(drawingSinceMs = null) }
    }
  }

  /** Keeps the newest covers and the one in use; older files are deleted. */
  private suspend fun dropOldCovers(bookId: BookId, look: BookLook) {
    val dropped = look.generatedCovers.drop(MAX_KEPT_COVERS).filter { it.path != look.generatedCover }
    if (dropped.isEmpty()) return
    stores.look.update(bookId) { current -> current.copy(generatedCovers = current.generatedCovers - dropped.toSet()) }
    withContext(Dispatchers.IO) { dropped.forEach { File(it.path).delete() } }
  }

  /** The book's own cover as PNG bytes for Ideogram to retheme, at most 1024 pixels wide. Null without a cover. */
  private suspend fun referenceCover(bookId: BookId): ByteArray? = withContext(Dispatchers.IO) {
    val original = originalCoverFile(bookId).takeIf { it.isFile }
      ?: bookRepository.get(bookId)?.content?.cover?.takeIf { it.isFile && stores.look.get(bookId).source != LookSource.Generated }
      ?: return@withContext null
    val bitmap = BitmapFactory.decodeFile(original.path) ?: return@withContext null
    val scaled = if (bitmap.width > REFERENCE_SIZE) bitmap.scale(REFERENCE_SIZE, bitmap.height * REFERENCE_SIZE / bitmap.width) else bitmap
    ByteArrayOutputStream().also { val _ = scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
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
    const val REFERENCE_SIZE = 1024
    const val MAX_KEPT_COVERS = 12
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
  append("edge, with no scenery, gradient or photo behind, and the bottom edge stays mostly that plain color so the ")
  append("cover fades into the player page of the same color; deep jewel tones are fine, never black. Use two or three ")
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

internal fun coverPromptRequest(
  book: String,
  visualStyle: String,
  extraPrompt: String,
  lastComposition: String,
  hasReference: Boolean,
): String = buildString {
  append(book)
  if (visualStyle.isNotBlank()) append("Look of the book: $visualStyle\n")
  if (hasReference) {
    append("\nThe image model gets the book's current cover and edits it. Write the prompt as an instruction to ")
    append("retheme that cover into the poster style: keep its title text, its main subject or symbol and the feel of ")
    append("its colors, and redraw everything in flat vector on one flat background color.\n")
  }
  if (extraPrompt.isNotBlank()) append("\nThe listener wants: $extraPrompt\n")
  if (lastComposition.isNotBlank()) append("\nThe last cover used the $lastComposition composition; pick a different one.\n")
  append("\nPlan the cover.")
}
