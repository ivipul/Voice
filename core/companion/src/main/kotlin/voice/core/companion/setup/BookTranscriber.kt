package voice.core.companion.setup

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import voice.core.ai.DeepgramClient
import voice.core.ai.MissingApiKeyException
import voice.core.ai.SpokenLine
import voice.core.companion.CompanionStores
import voice.core.companion.StepState
import voice.core.companion.TranscriptionState
import voice.core.companion.companionJson
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.BookRepository
import voice.core.data.toUri
import voice.core.logging.api.Logger
import voice.core.transcript.BookFiles
import voice.core.transcript.TranscriptCue
import voice.core.transcript.TranscriptRepository
import java.io.File

/**
 * Transcribes every audio file of a book with Deepgram, a few parts at a time. Each finished part is saved as a
 * checkpoint, so a stopped run picks up where it was, and the transcript is rewritten after every part, so Ask and
 * Recap work on the parts already done while the rest is still running.
 */
@Inject
class BookTranscriber(
  private val bookRepository: BookRepository,
  private val chunker: AudioChunker,
  private val deepgram: DeepgramClient,
  private val bookFiles: BookFiles,
  private val transcriptRepository: TranscriptRepository,
  private val stores: CompanionStores,
) {

  private data class FileJob(
    val chapterId: ChapterId,
    val plan: ChunkPlan,
    val workDir: File,
  )

  /** Runs to the end or throws; the setup state says how far it got either way. */
  suspend fun transcribe(bookId: BookId) {
    val book = checkNotNull(bookRepository.get(bookId)) { "Book $bookId is not in the library" }
    if (!deepgram.isConfigured()) throw MissingApiKeyException("Deepgram")
    setState(bookId) { it.copy(state = StepState.Running, error = null) }
    try {
      val jobs = withContext(Dispatchers.IO) {
        book.chapters.map { chapter ->
          FileJob(chapter.id, chunker.plan(chapter.id.toUri()), bookFiles.transcriptionWorkDir(chapter.id))
        }
      }
      val total = jobs.sumOf { it.plan.parts.size }
      val done = jobs.sumOf { job -> job.plan.parts.count { partFile(job.workDir, it).isFile } }
      setState(bookId) {
        it.copy(partsDone = done, partsTotal = total, filesTotal = jobs.size, filesDone = jobs.count { job -> job.isComplete() })
      }
      val progressLock = Mutex()
      var partsDone = done
      val permits = Semaphore(PARALLEL_PARTS)
      jobs.forEach { job ->
        coroutineScope {
          job.plan.parts.filterNot { partFile(job.workDir, it).isFile }.map { part ->
            async {
              permits.withPermit { transcribePart(job, part) }
              progressLock.withLock {
                partsDone++
                writeTranscript(job)
                setState(bookId) { it.copy(partsDone = partsDone) }
              }
            }
          }.awaitAll()
        }
        writeTranscript(job)
        setState(bookId) { it.copy(filesDone = jobs.count { other -> other.isComplete() }) }
      }
      setState(bookId) { it.copy(state = StepState.Done, partsDone = total) }
    } catch (e: CancellationException) {
      setState(bookId) { it.copy(state = StepState.NotStarted) }
      throw e
    } catch (e: Exception) {
      Logger.w(e, "Transcription failed for $bookId")
      setState(bookId) { it.copy(state = StepState.Failed, error = e.message ?: e.javaClass.simpleName) }
      throw e
    }
  }

  private suspend fun transcribePart(job: FileJob, part: AudioPart) {
    val temp = File(job.workDir, "part-${part.index}.audio")
    try {
      val written = withContext(Dispatchers.IO) { chunker.write(job.chapterId.toUri(), job.plan, part, temp) }
      val lines = deepgram.transcribe(written.file, written.mimeType)
      val cues = lines.toCues(offsetMs = written.startMs)
      withContext(Dispatchers.IO) {
        val target = partFile(job.workDir, part)
        val pending = File(job.workDir, "${target.name}.tmp")
        pending.writeText(companionJson.encodeToString(CueList, cues))
        check(pending.renameTo(target)) { "Could not save $target" }
      }
    } finally {
      temp.delete()
    }
  }

  /** Joins the finished parts of [job] into its transcript, in time order, and has the next lookup read it again. */
  private suspend fun writeTranscript(job: FileJob) {
    withContext(Dispatchers.IO) {
      val cues = job.plan.parts
        .map { partFile(job.workDir, it) }
        .filter { it.isFile }
        .flatMap { companionJson.decodeFromString(CueList, it.readText()) }
        .let(::mergeCues)
      val target = bookFiles.transcriptFile(job.chapterId)
      val pending = File(target.parentFile, "${target.name}.tmp")
      pending.writeText(companionJson.encodeToString(CueList, cues))
      if (!pending.renameTo(target)) {
        target.delete()
        check(pending.renameTo(target)) { "Could not save $target" }
      }
    }
    transcriptRepository.invalidate(job.chapterId)
  }

  private fun FileJob.isComplete(): Boolean = plan.parts.all { partFile(workDir, it).isFile }

  private fun partFile(workDir: File, part: AudioPart) = File(workDir, "part-${part.index}.json")

  private suspend fun setState(bookId: BookId, transform: (TranscriptionState) -> TranscriptionState) {
    stores.setup.update(bookId) { it.copy(transcription = transform(it.transcription)) }
  }

  private companion object {
    const val PARALLEL_PARTS = 3
    val CueList = ListSerializer(TranscriptCue.serializer())
  }
}

internal fun List<SpokenLine>.toCues(offsetMs: Long): List<TranscriptCue> = map {
  TranscriptCue(timestampStartMs = it.startMs + offsetMs, timestampEndMs = it.endMs + offsetMs, text = it.text)
}

/**
 * Sorts cues by time and drops the doubles where two parts overlap: a part starts at the AAC or MP3 frame before its
 * cut, so its first line can repeat the last line of the part before.
 */
internal fun mergeCues(cues: List<TranscriptCue>): List<TranscriptCue> {
  val sorted = cues.sortedBy { it.timestampStartMs }
  val merged = ArrayList<TranscriptCue>(sorted.size)
  for (cue in sorted) {
    val last = merged.lastOrNull()
    if (last != null && cue.timestampStartMs < last.timestampEndMs && cue.text == last.text) continue
    merged += cue
  }
  return merged
}
