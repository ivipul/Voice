package voice.core.companion.work

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.companion.BookMaterials
import voice.core.companion.CompanionStores
import voice.core.companion.StepState
import voice.core.companion.TranscriptionState
import voice.core.companion.look.LookMaker
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.core.transcript.BookFiles
import voice.core.transcript.TranscriptRepository
import java.io.File

/**
 * Takes a book back to before its setup: stops its jobs, puts its own cover back and forgets everything Earwig made
 * for it. The audio and the listening position stay. Kept files (transcripts and added PDFs) let a new setup skip
 * transcription and study the PDFs again.
 */
@Inject
class BookReset(
  private val runner: BookSetupRunner,
  private val stores: CompanionStores,
  private val lookMaker: LookMaker,
  private val bookRepository: BookRepository,
  private val bookFiles: BookFiles,
  private val transcriptRepository: TranscriptRepository,
) {

  suspend fun reset(bookId: BookId, keepFiles: Boolean) {
    runner.cancelAll(bookId)
    try {
      lookMaker.useDefault(bookId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Logger.w(e, "Could not put the original cover back for $bookId")
    }
    if (keepFiles) {
      val pdfs = stores.materials.get(bookId).files
      val keptPdfs = withContext(Dispatchers.IO) { moveOut(File(bookFiles.bookDir(bookId), MATERIALS_DIR)) }
      stores.clear(bookId)
      val _ = withContext(Dispatchers.IO) { keptPdfs?.renameTo(File(bookFiles.bookDir(bookId), MATERIALS_DIR)) }
      if (keptPdfs != null && pdfs.isNotEmpty()) {
        stores.materials.set(bookId, BookMaterials(files = pdfs.map { it.copy(studied = false, error = null) }))
      }
      if (hasAllTranscripts(bookId)) {
        stores.setup.update(bookId) { it.copy(transcription = TranscriptionState(state = StepState.Done)) }
      }
    } else {
      stores.clear(bookId)
      deleteTranscripts(bookId)
    }
  }

  /** Moves [dir] beside the book folders, so clearing the book's folder leaves it alone. */
  private fun moveOut(dir: File): File? {
    if (!dir.isDirectory) return null
    val parked = File(dir.parentFile?.parentFile, "${dir.parentFile?.name}-$MATERIALS_DIR")
    val _ = parked.deleteRecursively()
    return parked.takeIf { dir.renameTo(it) }
  }

  private suspend fun hasAllTranscripts(bookId: BookId): Boolean {
    val chapters = bookRepository.get(bookId)?.chapters.orEmpty()
    return chapters.isNotEmpty() && withContext(Dispatchers.IO) { chapters.all { bookFiles.transcriptFile(it.id).isFile } }
  }

  private suspend fun deleteTranscripts(bookId: BookId) {
    val chapters = bookRepository.get(bookId)?.chapters.orEmpty()
    withContext(Dispatchers.IO) {
      chapters.forEach { chapter ->
        val _ = bookFiles.transcriptFile(chapter.id).delete()
        val _ = bookFiles.transcriptionWorkDir(chapter.id).deleteRecursively()
      }
    }
    chapters.forEach { transcriptRepository.invalidate(it.id) }
  }

  private companion object {
    const val MATERIALS_DIR = "materials"
  }
}
