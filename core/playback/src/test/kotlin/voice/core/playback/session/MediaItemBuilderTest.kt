package voice.core.playback.session

import androidx.media3.common.MediaMetadata
import voice.core.data.BookId
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaItemBuilderTest {

  @Test
  fun `chapter item shows chapter as title and book as subtitle and album`() {
    val item = MediaItem(
      title = "Chapter 3",
      mediaId = MediaId.Book(BookId("book")),
      isPlayable = true,
      browsable = false,
      album = "The Book",
      subtitle = "The Book",
      artist = "The Author",
      durationMs = 3_365_000,
      mediaType = MediaType.AudioBookChapter,
    )

    val metadata = item.mediaMetadata
    assertEquals(expected = "Chapter 3", actual = metadata.title)
    assertEquals(expected = "The Book", actual = metadata.subtitle)
    assertEquals(expected = "The Book", actual = metadata.albumTitle)
    assertEquals(expected = "The Author", actual = metadata.artist)
    assertEquals(expected = 3_365_000L, actual = metadata.durationMs)
    assertEquals(expected = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER, actual = metadata.mediaType)
  }
}
