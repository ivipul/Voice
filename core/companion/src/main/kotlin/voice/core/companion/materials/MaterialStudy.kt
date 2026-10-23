package voice.core.companion.materials

import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable
import voice.core.ai.ClaudeClient
import voice.core.ai.ClaudeModels
import voice.core.companion.BookMaterials
import voice.core.companion.CompanionStores
import voice.core.companion.MaterialSection
import voice.core.companion.SceneCue
import voice.core.companion.Schema
import voice.core.companion.StepState
import voice.core.companion.companionJson
import voice.core.companion.firstCueMs
import voice.core.companion.scene.BookLine
import voice.core.companion.scene.LineWindow
import voice.core.companion.scene.bookLines
import voice.core.companion.scene.windowsOf
import voice.core.companion.setup.profileText
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.BookFiles
import voice.core.transcript.TranscriptRepository
import java.io.File

/**
 * Makes the book's companion PDFs usable: Claude reads each PDF once and writes down its sections with a faithful
 * plain-text extract, so later Extras and answers work from the extract instead of paying for the whole PDF again.
 * Once the book is transcribed, the small model finds where the narration refers to each section, which places the
 * section's chip on the player and unlocks its Extra at the right moment.
 */
@Inject
class MaterialStudy(
  private val bookRepository: BookRepository,
  private val transcriptRepository: TranscriptRepository,
  private val claude: ClaudeClient,
  private val stores: CompanionStores,
  private val bookFiles: BookFiles,
) {

  /**
   * Studies the PDFs not read yet, then links them to the transcript when it is ready. Does nothing without PDFs.
   * Returns whether a PDF was read for the first time.
   */
  @IgnorableReturnValue
  suspend fun run(bookId: BookId): Boolean {
    val materials = stores.materials.get(bookId)
    if (materials.files.isEmpty()) return false
    val transcribed = stores.setup.get(bookId).transcription.state == StepState.Done
    if (materials.files.all { it.studied } && (materials.linked || !transcribed)) return false
    stores.setup.update(bookId) { it.copy(materials = StepState.Running, materialsError = null) }
    try {
      val studiedNew = materials.files.any { !it.studied }
      study(bookId)
      if (transcribed) link(bookId)
      stores.setup.update(bookId) { it.copy(materials = StepState.Done) }
      return studiedNew
    } catch (e: Exception) {
      Logger.w(e, "Studying the PDFs failed for $bookId")
      stores.setup.update(bookId) { it.copy(materials = StepState.Failed, materialsError = e.message ?: e.javaClass.simpleName) }
      throw e
    }
  }

  private suspend fun study(bookId: BookId) {
    val profile = stores.profile.get(bookId)
    stores.materials.get(bookId).files.filterNot { it.studied }.forEach { file ->
      val answer = companionJson.decodeFromString(
        StudyAnswer.serializer(),
        claude.askJsonAboutPdf(
          system = STUDY_SYSTEM,
          prompt = profileText(profile) + "\nThe PDF \"${file.name}\" came with this audiobook. Write down its sections.",
          pdf = File(bookFiles.bookDir(bookId), file.path),
          schema = STUDY_SCHEMA,
        ),
      )
      val known = stores.materials.get(bookId).sections.size
      val sections = answer.sections.filter { it.title.isNotBlank() }.mapIndexed { index, section ->
        MaterialSection(
          // Short ids, because the small model has to repeat them back when it links sections to the transcript.
          id = "pdf${known + index + 1}",
          fileId = file.id,
          title = section.title.trim(),
          pages = section.pages,
          summary = section.summary,
          contentType = section.contentType,
          extract = section.extract,
          keyTerms = section.keyTerms.take(MAX_KEY_TERMS),
        )
      }
      stores.materials.update(bookId) { materials ->
        materials.copy(
          sections = materials.sections + sections,
          files = materials.files.map { if (it.id == file.id) it.copy(studied = true, error = null) else it },
          linked = false,
        )
      }
    }
  }

  private suspend fun link(bookId: BookId) {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    val materials = stores.materials.get(bookId)
    if (materials.sections.isEmpty()) return
    val lines = transcriptRepository.bookLines(book)
    val references = windowsOf(lines).flatMap { window ->
      val answer = companionJson.decodeFromString(
        LinkAnswer.serializer(),
        claude.askJson(
          system = LINK_SYSTEM,
          prompt = linkPrompt(materials, window),
          schema = LINK_SCHEMA,
          model = ClaudeModels.SCAN,
          maxTokens = LINK_MAX_TOKENS,
        ),
      )
      answer.references
    }
    val cues = materialCues(references, lines, materials.sections.map { it.id }.toSet())
    val linked = stores.materials.update(bookId) { it.copy(cues = cues, linked = true) }
    // Extras built before the transcript was ready now unlock where the narration first gets to their section.
    val unlockAt = linked.sections.mapNotNull { section -> section.extraId?.let { it to linked.firstCueMs(section.id) } }.toMap()
    stores.shelf.update(bookId) { shelf ->
      shelf.copy(extras = shelf.extras.map { extra -> unlockAt[extra.id]?.let { extra.copy(unlockAtMs = it) } ?: extra })
    }
  }

  private companion object {
    const val MAX_KEY_TERMS = 8
    const val LINK_MAX_TOKENS = 2_000L
  }
}

/** How long a section's chip stays on the player after the narration refers to it. */
internal const val MATERIAL_CHIP_MS = 4 * 60 * 1000L

/**
 * Turns the lines Claude picked into chip times: each reference shows from its line for [MATERIAL_CHIP_MS], and
 * references to the same section that overlap are merged. Unknown sections and line numbers are dropped.
 */
internal fun materialCues(
  references: List<LinkReference>,
  lines: List<BookLine>,
  sectionIds: Set<String>,
): List<SceneCue> = references
  .filter { it.section in sectionIds && it.line in lines.indices }
  .groupBy { it.section }
  .flatMap { (section, refs) ->
    val ranges = refs.map { lines[it.line].startMs }.sorted().map { it..(it + MATERIAL_CHIP_MS) }
    val merged = mutableListOf<LongRange>()
    ranges.forEach { range ->
      val last = merged.lastOrNull()
      if (last != null && range.first <= last.last) merged[merged.lastIndex] = last.first..maxOf(last.last, range.last) else merged += range
    }
    merged.map { SceneCue(entry = section, startMs = it.first, endMs = it.last) }
  }
  .sortedBy { it.startMs }

private fun linkPrompt(materials: BookMaterials, window: LineWindow): String = buildString {
  append("Sections of the PDF that came with the book:\n")
  materials.sections.forEach { section ->
    append("- ${section.id}: ${section.title}")
    if (section.keyTerms.isNotEmpty()) append(" (${section.keyTerms.joinToString()})")
    append(". ${section.summary}\n")
  }
  append("\nTranscript lines:\n")
  window.lines.forEachIndexed { i, line -> append("[${window.firstIndex + i}] ${line.text}\n") }
}

@Serializable
internal data class StudyAnswer(val sections: List<StudySection>)

@Serializable
internal data class StudySection(
  val title: String,
  val pages: String,
  val summary: String,
  val contentType: String,
  val extract: String,
  val keyTerms: List<String>,
)

@Serializable
internal data class LinkAnswer(val references: List<LinkReference>)

@Serializable
internal data class LinkReference(
  val section: String,
  val line: Int,
)

internal const val STUDY_SYSTEM = "You read the PDF that comes with an audiobook (worksheets, plans, tables, " +
  "figures, recipes, checklists) so an app can turn it into interactive tools that play alongside the narration. " +
  "Split it into its meaningful sections, at most 30, in order. For each: title as the PDF names it; pages like " +
  "\"12-14\"; summary in one or two sentences on what it is for; contentType, one word (checklist, table, " +
  "framework, exercise, diagram, list, recipe, reference, plan); extract, the section's actual information in " +
  "plain text, faithful to the PDF, keeping list items, steps, table rows and numbers (at most 250 words, so " +
  "summarize prose but keep data exact); keyTerms, up to 8 words or phrases the narrator would likely say when " +
  "referring to it. Skip covers, copyright pages and blank pages."

internal const val LINK_SYSTEM = "You find where an audiobook's narration refers to the PDF that comes with it. " +
  "Given the PDF's sections and numbered transcript lines, list each place where the narrator points the listener " +
  "to a section (\"see the worksheet in the PDF\", \"the table in the accompanying material\") or walks through " +
  "what a section holds. line is the number of the line where that starts. Only clear references, at most one per " +
  "section per few minutes; an empty list is fine."

internal val STUDY_SCHEMA = Schema.obj(
  "sections" to Schema.array(
    Schema.obj(
      "title" to Schema.string(),
      "pages" to Schema.string(),
      "summary" to Schema.string(),
      "contentType" to Schema.string(),
      "extract" to Schema.string(),
      "keyTerms" to Schema.array(Schema.string()),
    ),
  ),
)

internal val LINK_SCHEMA = Schema.obj(
  "references" to Schema.array(
    Schema.obj(
      "section" to Schema.string("The section id."),
      "line" to Schema.integer(),
    ),
  ),
)
