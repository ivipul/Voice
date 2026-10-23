package voice.core.companion.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptPairingTest {

  @Test
  fun eachAudioFileGetsTheTranscriptsAndPdfsNamedAfterIt() {
    val books = sortPicked(listOf("Book 1.m4b", "Book 2.mp3", "book 1.JSON", "Book 2.m4b.srt", "Book 1 Workbook.pdf", "notes.json"))

    assertEquals(
      listOf(
        PickedNames("Book 1.m4b", transcripts = listOf("book 1.JSON"), pdfs = listOf("Book 1 Workbook.pdf")),
        PickedNames("Book 2.mp3", transcripts = emptyList(), pdfs = emptyList()),
      ),
      books,
    )
  }

  @Test
  fun openAudiblesNamesWithTheAudioExtensionKeptMatch() {
    val books = sortPicked(listOf("Good Energy.m4b", "Good Energy.m4b.json", "Good Energy.pdf", "Other.m4b"))

    assertEquals(listOf("Good Energy.m4b.json"), books.first().transcripts)
    assertEquals(listOf("Good Energy.pdf"), books.first().pdfs)
  }

  @Test
  fun aSingleAudioFileTakesEverythingPickedWithItTheMatchingTranscriptFirst() {
    val books = sortPicked(listOf("other.json", "audiobook.m4b", "audiobook.srt", "Handout.pdf"))

    assertEquals(listOf(PickedNames("audiobook.m4b", listOf("audiobook.srt", "other.json"), listOf("Handout.pdf"))), books)
  }

  @Test
  fun transcriptsAndPdfsAloneAreNoBook() {
    assertEquals(emptyList(), sortPicked(listOf("Book.json", "Book.pdf")))
  }
}
