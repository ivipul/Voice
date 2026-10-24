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
import voice.core.companion.StoredPalette
import voice.core.companion.companionJson
import voice.core.companion.setup.profileText
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.scanner.CoverSaver
import voice.core.transcript.BookFiles
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A book's look: the app's cream, red and black, the curated palette closest to its cover, or a cover Tandem draws
 * with Ideogram in such a palette (with the listener's own words added to the prompt). The book's original cover is kept
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

  /** The curated palette closest to the book's own cover. Returns false when the book has no cover to pick from. */
  @IgnorableReturnValue
  suspend fun useCoverColors(bookId: BookId): Boolean {
    restoreOriginalCover(bookId)
    val cover = bookRepository.get(bookId)?.content?.cover?.takeIf { it.isFile } ?: return false
    val palette = withContext(Dispatchers.IO) { decodeSmall(cover)?.let { closestCuratedPalette(it.colors()) } } ?: return false
    stores.look.update(bookId) { it.copy(source = LookSource.Cover, palette = palette.palette) }
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

  /**
   * Makes [cover], drawn earlier, the book's cover, with the palette it was drawn in (colors picked from it for
   * covers drawn before palettes were curated) and its chapter-name face.
   */
  suspend fun useGenerated(bookId: BookId, cover: GeneratedCover) {
    keepOriginalCover(bookId)
    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(cover.path) } ?: error("That cover is gone")
    coverSaver.save(bookId, bitmap)
    val palette = curatedPalette(cover.palette)?.palette
      ?: withContext(Dispatchers.Default) { closestCuratedPalette(bitmap.scale(SAMPLE_SIZE, SAMPLE_SIZE).colors()).palette }
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
   * Draws a cover and adds it to the book's history without using it. Claude looks at the book's own cover and
   * names its cast, poses and props in the prompt, and Ideogram gets it as a reference for those only, so the new one
   * reimagines it in a fresh composition. Ideogram 4.5 has no image weight: the prompt's wording decides how much of
   * the reference it copies.
   */
  private suspend fun draw(bookId: BookId, extraPrompt: String): GeneratedCover {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    stores.look.update(bookId) { it.copy(drawingSinceMs = System.currentTimeMillis(), extraCoverPrompt = extraPrompt) }
    try {
      val reference = referenceCover(bookId)
      val recent = stores.look.get(bookId).coverHistory.take(RECENT_PALETTES).map { it.palette }.filter { it.isNotEmpty() }.toSet()
      val choices = coverPaletteChoices(reference?.scale(SAMPLE_SIZE, SAMPLE_SIZE)?.colors(), recent)
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
            palettes = choices,
          ),
          schema = coverSchema(if (extraPrompt.isBlank()) choices else CURATED_PALETTES),
          maxTokens = 2_000,
          effort = OutputConfig.Effort.LOW,
          image = reference?.let { it.scale(CLAUDE_REFERENCE_SIZE, it.height * CLAUDE_REFERENCE_SIZE / it.width).png() },
        ),
      )
      val bytes = ideogram.generate(plan.prompt.trim(), ImageShape.Square, source = reference?.png())
      val file = withContext(Dispatchers.IO) {
        File(bookFiles.bookDir(bookId), "generated-cover-${System.currentTimeMillis()}.png").apply { writeBytes(bytes) }
      }
      val palette = curatedPalette(plan.palette) ?: choices.first()
      val cover = GeneratedCover(path = file.absolutePath, composition = plan.composition, font = plan.font, palette = palette.name)
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

  /** The book's own cover for Ideogram to restyle, at most 1024 pixels wide. Null without a cover. */
  private suspend fun referenceCover(bookId: BookId): Bitmap? = withContext(Dispatchers.IO) {
    val original = originalCoverFile(bookId).takeIf { it.isFile }
      ?: bookRepository.get(bookId)?.content?.cover?.takeIf { it.isFile && stores.look.get(bookId).source != LookSource.Generated }
      ?: return@withContext null
    val bitmap = BitmapFactory.decodeFile(original.path) ?: return@withContext null
    if (bitmap.width > REFERENCE_SIZE) bitmap.scale(REFERENCE_SIZE, bitmap.height * REFERENCE_SIZE / bitmap.width) else bitmap
  }

  private suspend fun Bitmap.png(): ByteArray = withContext(Dispatchers.IO) {
    ByteArrayOutputStream().also { val _ = compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
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

  private fun Bitmap.colors(): StoredPalette {
    val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    return paletteFromPixels(pixels, width)
  }

  private companion object {
    const val SAMPLE_SIZE = 64
    const val REFERENCE_SIZE = 1024

    // Enough for Claude to see the cover's subject and layout at about a quarter of the image tokens.
    const val CLAUDE_REFERENCE_SIZE = 512
    const val MAX_KEPT_COVERS = 12

    // A redraw avoids the palettes of the cover in use and the one before it.
    const val RECENT_PALETTES = 2
  }
}

/** The layouts a generated cover can take, all in the same screen-printed poster style. */
internal val COVER_COMPOSITIONS = mapOf(
  "stacked" to "Stacked title: two or three lines of huge title lettering in two inks, each line running the full " +
    "width, with the main figure standing in front of the letters and covering part of them.",
  "wall" to "Tall condensed wall: one ultra-condensed title word filling almost the full height behind the figure, " +
    "with the author's name in a thin, widely spaced row above it.",
  "giant" to "Giant letterform: one enormous letter or number from the title as the backdrop, the figure in front " +
    "of it, and the rest of the title small in a corner.",
  "serif" to "Tone-on-tone serif: a tall, high-contrast serif title in a darker or lighter shade of the background, " +
    "partly hidden by large figures in front.",
  "disc" to "Sun disc: the figure in profile or three-quarter view in front of one huge flat disc in the hot ink, the " +
    "title in one tall line above or beside it, and thin rules in the top corners.",
  "split" to "Diagonal split: one hard diagonal cuts the upper part of the canvas into the background color and a " +
    "block of ink or the hot ink; the figure sits on the seam, half lit, half in shadow, with a short, widely " +
    "spaced title in the lower third.",
  "card" to "Card: laid out like a playing card or a ticket, with the title's first letter in two corners, one " +
    "bold flat symbol or the figure, the title set vertically up one side or as one serif line, and a thin rule.",
)

/** The app's chapter name faces, and how a cover letters its title to match each one. */
internal val COVER_FONTS = mapOf(
  "anton" to "heavy condensed sans serif, like Anton",
  "condensed" to "ultra-condensed tall sans serif, like Big Shoulders Display",
  "serif" to "tall elegant high-contrast serif, like Instrument Serif",
  "classic" to "bold high-contrast didone serif, like Playfair Display",
)

internal val COVER_PROMPT_SYSTEM = buildString {
  append("You plan a square audiobook cover and write the prompt an image model draws it from. Every cover shares ")
  append("one style, a screen-printed graphic poster:\n")
  append("- Inks: three or four flat inks from the given palette and nothing else, with no gradients, like a screen ")
  append("print or risograph; a fine paper grain over everything and slightly rough ink edges.\n")
  append("- Drawing: bold graphic-novel illustration. Forms are carved by hard-edged blocks of near-black shadow, ")
  append("never soft shading, with confident ink lines or loose dry-brush hatching for texture. Cream or white is ")
  append("kept for small, sharp highlights such as eyes, jewelry, cuffs and lettering.\n")
  append("- Background: one flat palette color fills the canvas to every edge, with no scenery, gradient or photo, ")
  append("and the bottom edge stays plain so the cover melts into the player page of the same color.\n")
  append("- Type: the huge title lettering is the main graphic and stays readable, with the figure in front of the ")
  append("letters, covering part of them. The author's name goes in tiny, widely spaced capitals, editorial style. ")
  append("The title and the author's name are the only words on the cover: no tagline, series line, quote, award or ")
  append("narrator credit, unless the book can't be told apart without it, like the number of a book in a series.\n")
  append("- Restraint: lots of empty background, one hot ink doing the work, nothing glowing, no 3D, no ")
  append("photographic detail.\n")
  append("Fiction shows its main character as an expressive figure described by how they look; non-fiction shows ")
  append("one bold symbol, object or scene of its idea. Never name real people, brands or logos.\n")
  append("When you are shown the book's own cover, the new cover reimagines it: the same cast (each figure's look, ")
  append("outfit and signature pose or gesture, up to five figures), its key props and its theme and energy, ")
  append("recomposed boldly into one of the compositions from a new perspective, as flat cut-out figures on the flat ")
  append("background. Never recreate its layout, camera angle, background scene or colors.\n\nCompositions:\n")
  COVER_COMPOSITIONS.forEach { (id, text) -> append("- $id: $text\n") }
  append("\nTitle faces (the app sets chapter names in the same face):\n")
  COVER_FONTS.forEach { (id, text) -> append("- $id: $text\n") }
  append("\nPalettes (background / ink / hot ink / second ink):\n")
  CURATED_PALETTES.forEach { append("- ${it.name}: ${it.inks()}\n") }
  append("\nPick the composition, face and palette that suit the book, unless they are given. The prompt is one ")
  append("paragraph of at most 160 words: composition, lettering in the chosen face, figure or symbol, which ink ")
  append("goes where with its hex code, and style. Put every piece of cover text in quotes so the image model ")
  append("letters it exactly.")
}

/** The cover plan, with the palette limited to [palettes]. */
internal fun coverSchema(palettes: List<CuratedPalette>) = Schema.obj(
  "composition" to Schema.enum(COVER_COMPOSITIONS.keys.toList()),
  "font" to Schema.enum(COVER_FONTS.keys.toList()),
  "palette" to Schema.enum(palettes.map { it.name }),
  "prompt" to Schema.string(),
)

@Serializable
internal data class CoverPlan(
  val composition: String,
  val font: String,
  val palette: String = "",
  val prompt: String,
)

/**
 * What Claude gets to plan a cover from. With a [reference] cover, the new one is that cover restyled in [palette],
 * one of [palettes], so its cast and theme survive in a new composition.
 */
internal fun coverPromptRequest(
  book: String,
  visualStyle: String,
  extraPrompt: String,
  lastComposition: String,
  hasReference: Boolean,
  palettes: List<CuratedPalette> = emptyList(),
): String = buildString {
  append(book)
  if (visualStyle.isNotBlank()) append("Look of the book: $visualStyle\n")
  if (hasReference) {
    append("\nThe attached image is the book's current cover, and the image model gets it too. Use it as inspiration, ")
    append("not as a template: anyone who knows the original should recognize its cast and theme, but the picture is a ")
    append("new, starker and bolder poster with a different composition and perspective. Start the prompt with \"@img_1 ")
    append("is a reference for the characters, their poses and the props only: do not copy its layout, camera angle, ")
    append("background, colors or lettering.\" The image model follows words more than the picture, so then name ")
    append("each figure you keep concretely (what they are, their hair, outfit and signature pose or gesture, what ")
    append("they hold) and where each one goes in the new composition. Keep only the title and the author's name as ")
    append("text, and tell the image model to remove every other word on the old cover.\n")
  }
  val palette = palettes.singleOrNull()
  if (palette != null) {
    append("\nUse the ${palette.name} palette (${palette.inks()}): paint the cover in it, the original's main color ")
    append("becoming the background, its darks the ink and its brightest color the hot ink.")
  } else if (palettes.isNotEmpty()) {
    append("\nUse whichever of these palettes suits the book${if (hasReference) " and its cover" else ""} best, ")
    append("and paint the cover in it:\n")
    palettes.forEach { append("- ${it.name}: ${it.inks()}\n") }
  }
  if (palettes.isNotEmpty()) append(" Only if the listener asks for other colors, pick the palette that fits their words instead.\n")
  if (extraPrompt.isNotBlank()) append("\nThe listener wants: $extraPrompt\n")
  if (lastComposition.isNotBlank()) {
    append("\nThe last cover used the $lastComposition composition; pick a different one.\n")
  }
  append("\nPlan the cover.")
}

/** The palette's four inks as hex codes, in the order the cover prompt names them. */
internal fun CuratedPalette.inks(): String = with(palette) {
  listOf(background, content, highlight, accent).joinToString(" / ") { "#%06X".format(it and 0xFFFFFF) }
}
