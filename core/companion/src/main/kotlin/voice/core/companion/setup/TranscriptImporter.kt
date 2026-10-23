package voice.core.companion.setup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.companion.CompanionStores
import voice.core.companion.StepState
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.logging.api.Logger
import voice.core.transcript.BookFiles
import voice.core.transcript.TranscriptParser
import voice.core.transcript.TranscriptRepository
import java.io.File

/**
 * Takes in a transcript the listener picked together with an audio file. A single picked file gives the app no
 * access to the rest of its folder, so the `.json` beside it can only be read when it is picked too. It is copied
 * into the app's own storage, which lets the book skip cloud transcription.
 */
@Inject
class TranscriptImporter(
  private val context: Context,
  private val bookFiles: BookFiles,
  private val transcriptRepository: TranscriptRepository,
  private val stores: CompanionStores,
) {

  /** The picked audio files, each with the transcript picked for it, if any. */
  suspend fun sort(uris: List<Uri>): List<Pair<Uri, Uri?>> = withContext(Dispatchers.IO) {
    val byName = uris.associateBy { uri -> displayName(uri) ?: uri.lastPathSegment.orEmpty() }
    pairTranscripts(byName.keys.toList()).map { (audio, transcript) ->
      byName.getValue(audio) to transcript?.let(byName::getValue)
    }
  }

  /** Copies [transcript] in as the transcript of the single-file book [audio]; false if it isn't one. */
  suspend fun import(audio: Uri, transcript: Uri): Boolean {
    val chapterId = ChapterId(audio)
    val imported = withContext(Dispatchers.IO) {
      try {
        val cues = context.contentResolver.openInputStream(transcript)?.use { TranscriptParser.parse(it) }
        if (cues.isNullOrEmpty()) return@withContext false
        val target = bookFiles.transcriptFile(chapterId)
        val pending = File(target.parentFile, "${target.name}.tmp")
        context.contentResolver.openInputStream(transcript)?.use { input -> pending.outputStream().use { input.copyTo(it) } }
        if (!pending.renameTo(target)) {
          val _ = target.delete()
          check(pending.renameTo(target)) { "Could not save $target" }
        }
        true
      } catch (e: Exception) {
        Logger.w(e, "The picked file $transcript is not a transcript Earwig can read")
        false
      }
    }
    if (imported) {
      transcriptRepository.invalidate(chapterId)
      stores.setup.update(BookId(audio)) { it.copy(transcription = it.transcription.copy(state = StepState.Done, error = null)) }
    }
    return imported
  }

  private fun displayName(uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }
  }.getOrNull()
}

/**
 * Pairs each audio file name with the `.json` of the same name. When one audio file and one transcript were picked,
 * they belong together whatever their names.
 */
internal fun pairTranscripts(names: List<String>): List<Pair<String, String?>> {
  val (transcripts, audio) = names.partition { it.endsWith(".json", ignoreCase = true) }
  if (audio.size == 1 && transcripts.size == 1) return listOf(audio.single() to transcripts.single())
  return audio.map { file ->
    file to transcripts.firstOrNull { it.substringBeforeLast('.').equals(file.substringBeforeLast('.'), ignoreCase = true) }
  }
}
