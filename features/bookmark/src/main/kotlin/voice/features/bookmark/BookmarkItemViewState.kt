package voice.features.bookmark

import voice.core.data.Bookmark
import voice.core.strips.AvailableStrip

data class BookmarkItemViewState(
  val title: String,
  val subtitle: String,
  val id: Bookmark.Id,
  val showSleepIcon: Boolean,
)

data class BookmarkViewState(
  val bookmarks: List<BookmarkItemViewState>,
  val shouldScrollTo: Bookmark.Id?,
  val dialogViewState: BookmarkDialogViewState,
  val strips: List<AvailableStrip> = emptyList(),
  val activeStrip: AvailableStrip? = null,
)

sealed interface BookmarkDialogViewState {
  data object None : BookmarkDialogViewState
  data object AddBookmark : BookmarkDialogViewState
  data class EditBookmark(
    val id: Bookmark.Id,
    val title: String?,
  ) : BookmarkDialogViewState
}
