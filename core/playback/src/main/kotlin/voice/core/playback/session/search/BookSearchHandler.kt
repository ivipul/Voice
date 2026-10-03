package voice.core.playback.session.search

import android.provider.MediaStore
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger

@Inject
class BookSearchHandler(
  private val repo: BookRepository,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
) {

  suspend fun handle(search: VoiceSearch): Book? {
    Logger.i("handle $search")
    return when (search.mediaFocus) {
      MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> searchByArtist(search)
      MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE,
      MediaStore.Audio.Media.ENTRY_CONTENT_TYPE,
      -> searchAlbum(search)
      else -> null
    } ?: searchUnstructured(search.query)
  }

  private suspend fun searchAlbum(search: VoiceSearch): Book? {
    if (search.album != null) {
      val foundMatch = findBook {
        val nameMatches = it.content.name.contains(search.album, ignoreCase = true)
        val artistMatches =
          search.artist == null || it.content.author?.contains(search.artist, ignoreCase = true) == true
        nameMatches && artistMatches
      }
      if (foundMatch != null) return foundMatch
    }

    return null
  }

  // Look for anything that might match the query
  private suspend fun searchUnstructured(query: String?): Book? {
    if (!query.isNullOrBlank()) {
      val foundMatch = findBook { it.matches(query) }
      if (foundMatch != null) return foundMatch
    }

    Logger.i("continuing from search without query")
    val currentId = currentBookStore.data.first()
    return findBook { it.content.id == currentId }
  }

  /** Every book whose name, author or a chapter name contains [query]; empty for a blank query. */
  suspend fun searchAll(query: String?): List<Book> {
    if (query.isNullOrBlank()) return emptyList()
    return repo.all().filter { it.matches(query) }
  }

  private fun Book.matches(query: String): Boolean {
    val bookNameMatches = content.name.contains(query, ignoreCase = true)
    val authorMatches = content.author?.contains(query, ignoreCase = true) == true
    val chapterNameMatches = chapters.any { chapter ->
      val chapterName = chapter.name
      chapterName != null && chapterName.contains(query, ignoreCase = true)
    }
    return bookNameMatches || authorMatches || chapterNameMatches
  }

  private suspend fun searchByArtist(search: VoiceSearch): Book? {
    Logger.i("searchByArtist")
    if (search.artist != null) {
      val foundMatch = findBook {
        it.content.author?.contains(search.artist, ignoreCase = true) == true
      }
      if (foundMatch != null) {
        return foundMatch
      }
    }
    return null
  }

  // Play the first book that matches to a selector. Returns if a book is being played
  private suspend inline fun findBook(selector: (Book) -> Boolean): Book? {
    return repo.all().find(selector)
  }
}
