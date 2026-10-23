package voice.core.companion.setup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.companion.CompanionStores
import voice.core.companion.MaterialFile
import voice.core.companion.StepState
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.logging.api.Logger
import voice.core.transcript.BookFiles
import voice.core.transcript.SrtParser
import voice.core.transcript.TranscriptCue
import voice.core.transcript.TranscriptParser
import voice.core.transcript.TranscriptRepository
import voice.core.transcript.isTranscriptFor
import java.io.File
import java.util.UUID

/** An audio file the listener picked, with the transcript and PDF material picked for it. */
data class PickedBook(
  val audio: Uri,
  val transcripts: List<Uri>,
  val pdfs: List<Uri>,
)

/**
 * Takes in what the listener picked together with an audio file. A single picked file gives the app no access to the
 * rest of its folder, so the transcript and PDFs beside it can only be read when they are picked too. A transcript
 * (`.json` in any common shape, or `.srt`) is copied into the app's storage and the book skips cloud transcription;
 * PDFs are copied into the book's folder for Claude to study.
 */
@Inject
class TranscriptImporter(
  private val context: Context,
  private val bookFiles: BookFiles,
  private val transcriptRepository: TranscriptRepository,
  private val stores: CompanionStores,
) {

  suspend fun sort(uris: List<Uri>): List<PickedBook> = withContext(Dispatchers.IO) {
    val byName = uris.associateBy { uri -> displayName(uri) ?: uri.lastPathSegment.orEmpty() }
    sortPicked(byName.keys.toList()).map { picked ->
      PickedBook(
        audio = byName.getValue(picked.audio),
        transcripts = picked.transcripts.map(byName::getValue),
        pdfs = picked.pdfs.map(byName::getValue),
      )
    }
  }

  /** Copies in what came with [book]: the first readable transcript, and every PDF. */
  suspend fun import(book: PickedBook) {
    val bookId = BookId(book.audio)
    val transcribed = book.transcripts.any { importTranscript(book.audio, it) }
    if (transcribed) {
      stores.setup.update(bookId) { it.copy(transcription = it.transcription.copy(state = StepState.Done, error = null)) }
    }
    val files = book.pdfs.mapNotNull { copyPdf(bookId, it) }
    if (files.isNotEmpty()) {
      stores.materials.update(bookId) { it.copy(files = it.files + files, linked = false) }
      stores.setup.update(bookId) { it.copy(materials = StepState.NotStarted, materialsError = null) }
    }
  }

  private suspend fun importTranscript(audio: Uri, transcript: Uri): Boolean {
    val chapterId = ChapterId(audio)
    val cues = withContext(Dispatchers.IO) { readCues(transcript) }
    if (cues.isNullOrEmpty()) return false
    withContext(Dispatchers.IO) {
      val target = bookFiles.transcriptFile(chapterId)
      val pending = File(target.parentFile, "${target.name}.tmp")
      pending.writeText(TranscriptParser.encode(cues))
      if (!pending.renameTo(target)) {
        val _ = target.delete()
        check(pending.renameTo(target)) { "Could not save $target" }
      }
    }
    transcriptRepository.invalidate(chapterId)
    return true
  }

  private fun readCues(uri: Uri): List<TranscriptCue>? = try {
    val srt = displayName(uri).orEmpty().endsWith(".srt", ignoreCase = true)
    context.contentResolver.openInputStream(uri)?.use { stream ->
      if (srt) SrtParser.parse(stream.bufferedReader().readText()) else TranscriptParser.parse(stream)
    }
  } catch (e: Exception) {
    Logger.w(e, "The picked file $uri is not a transcript Earwig can read")
    null
  }

  private suspend fun copyPdf(bookId: BookId, pdf: Uri): MaterialFile? = withContext(Dispatchers.IO) {
    try {
      val id = UUID.randomUUID().toString()
      val path = "materials/$id.pdf"
      val target = File(bookFiles.bookDir(bookId), path).apply { parentFile?.mkdirs() }
      val copied = context.contentResolver.openInputStream(pdf)?.use { input -> target.outputStream().use { input.copyTo(it) } }
      if (copied == null || copied == 0L) return@withContext null
      MaterialFile(id = id, name = displayName(pdf) ?: "Companion PDF", path = path)
    } catch (e: Exception) {
      Logger.w(e, "Could not copy the PDF $pdf")
      null
    }
  }

  private fun displayName(uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }
  }.getOrNull()
}

internal data class PickedNames(
  val audio: String,
  val transcripts: List<String> = emptyList(),
  val pdfs: List<String> = emptyList(),
)

/**
 * Sorts picked file names into books: each audio file takes the transcripts named after it (`Book.json`,
 * `Book.m4b.json`, `Book.srt`) and the PDFs whose names start with its name. With a single audio file, every
 * transcript and PDF picked belongs to it, whatever their names.
 */
internal fun sortPicked(names: List<String>): List<PickedNames> {
  val transcripts = names.filter { it.endsWith(".json", ignoreCase = true) || it.endsWith(".srt", ignoreCase = true) }
  val pdfs = names.filter { it.endsWith(".pdf", ignoreCase = true) }
  val audio = names - transcripts.toSet() - pdfs.toSet()
  if (audio.size == 1) return listOf(PickedNames(audio.single(), transcripts.sortedByDescending { isTranscriptFor(it, audio.single()) }, pdfs))
  return audio.map { file ->
    val base = file.substringBeforeLast('.')
    PickedNames(
      audio = file,
      transcripts = transcripts.filter { isTranscriptFor(it, file) },
      pdfs = pdfs.filter { it.startsWith(base, ignoreCase = true) },
    )
  }
}
