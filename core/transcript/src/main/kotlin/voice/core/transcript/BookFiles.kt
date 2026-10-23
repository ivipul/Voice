package voice.core.transcript

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.data.BookId
import voice.core.data.ChapterId
import java.io.File
import java.security.MessageDigest

/**
 * Where Earwig keeps what it makes for a book, all in the app's private storage: transcripts per audio file, and
 * everything else (profile, ideas, Extras, scene chips, covers) in one folder per book.
 */
@SingleIn(AppScope::class)
@Inject
class BookFiles(private val context: Context) {

  /** The folder for everything Earwig makes for [bookId], created on first use. */
  fun bookDir(bookId: BookId): File = File(context.filesDir, "books/${stableKey(bookId.value)}").apply { mkdirs() }

  /** The finished transcript of one audio file, in the cue format [TranscriptParser] reads. */
  fun transcriptFile(chapterId: ChapterId): File =
    File(context.filesDir, "transcripts/${stableKey(chapterId.value)}.json").apply { parentFile?.mkdirs() }

  /** Scratch space for a transcription in progress: one checkpoint file per finished part. */
  fun transcriptionWorkDir(chapterId: ChapterId): File =
    File(context.filesDir, "transcripts/work/${stableKey(chapterId.value)}").apply { mkdirs() }
}

/** A short file-name-safe key for a URI: the first 16 hex digits of its SHA-1. */
fun stableKey(value: String): String = MessageDigest.getInstance("SHA-1")
  .digest(value.toByteArray())
  .take(8)
  .joinToString("") { "%02x".format(it) }
