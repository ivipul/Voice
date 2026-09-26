package voice.core.transcript

import android.net.Uri
import voice.core.documentfile.CachedDocumentFile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptSourceTest {

  @Test
  fun `finds transcript json matching the audio file basename`() {
    val audioFile = fakeFile("Book 1.m4b")
    val transcriptFile = fakeFile("Book 1.json")
    val folder = fakeFolder(listOf(audioFile, transcriptFile, fakeFile("Book 2.json")))

    assertEquals(expected = transcriptFile, actual = folder.transcriptFor(audioFile))
  }

  @Test
  fun `returns null when no matching transcript exists`() {
    val audioFile = fakeFile("Book 1.m4b")
    val folder = fakeFolder(listOf(audioFile, fakeFile("Book 2.json")))

    assertNull(folder.transcriptFor(audioFile))
  }

  private fun fakeFile(name: String): CachedDocumentFile = FakeDocumentFile(name = name, isFile = true)

  private fun fakeFolder(children: List<CachedDocumentFile>): CachedDocumentFile =
    FakeDocumentFile(name = "folder", isFile = false, children = children)

  private data class FakeDocumentFile(
    override val name: String?,
    override val isFile: Boolean,
    override val children: List<CachedDocumentFile> = emptyList(),
  ) : CachedDocumentFile {
    override val isDirectory: Boolean get() = !isFile
    override val length: Long get() = 0
    override val lastModified: Long get() = 0
    override val uri: Uri get() = error("not used by transcriptFor")
  }
}
