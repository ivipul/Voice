package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import voice.core.data.BookId
import voice.core.ui.crawl.CrawlEdition
import voice.features.bookOverview.search.BookSearchViewState

@Immutable
data class BookOverviewViewState(
  val books: Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>>,
  val layoutMode: BookOverviewLayoutMode,
  val playButtonState: PlayButtonState?,
  /** The series edition of the book the play button controls; its palette colors the play and Deck buttons. */
  val activeEdition: CrawlEdition? = null,
  /** Set all text on the library in the one header font instead of each book's own. */
  val uniformFont: Boolean = false,
  val showAddBookHint: Boolean,
  val showSearchIcon: Boolean,
  val isLoading: Boolean,
  val searchActive: Boolean,
  val searchViewState: BookSearchViewState,
  val showStoragePermissionBugCard: Boolean,
  val showFolderPickerIcon: Boolean,
  val dialog: Dialog?,
) {

  companion object {
    val Loading = BookOverviewViewState(
      books = mapOf(),
      layoutMode = BookOverviewLayoutMode.List,
      playButtonState = null,
      showAddBookHint = false,
      showSearchIcon = false,
      isLoading = true,
      searchActive = false,
      searchViewState = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = "",
      ),
      showStoragePermissionBugCard = false,
      showFolderPickerIcon = true,
      dialog = null,
    )
  }

  enum class PlayButtonState {
    Playing,
    Paused,
  }

  enum class Dialog {
    FolderPickerMovedToSettings,
  }
}
