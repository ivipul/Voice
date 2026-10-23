package voice.features.bookOverview.resetBook

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import voice.core.companion.work.BookReset
import voice.core.data.BookId
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.scanner.MediaScanTrigger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Duration.Companion.seconds

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class ResetBookViewModel(
  private val bookReset: BookReset,
  private val audiobookFolders: AudiobookFolders,
  private val mediaScanTrigger: MediaScanTrigger,
  private val navigator: Navigator,
) : BottomSheetItemViewModel {

  private val scope = MainScope()

  private val _state = mutableStateOf<ResetBookViewState?>(null)
  internal val state: State<ResetBookViewState?> get() = _state

  override suspend fun items(bookId: BookId): List<BottomSheetItem> = buildList {
    add(BottomSheetItem.ResetSetup)
    // A book from a folder can only leave with its whole folder, which Settings handles.
    if (isSingleFile(bookId)) add(BottomSheetItem.RemoveFromLibrary)
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    val remove = when (item) {
      BottomSheetItem.ResetSetup -> false
      BottomSheetItem.RemoveFromLibrary -> true
      else -> return
    }
    _state.value = ResetBookViewState(id = bookId, removeFromLibrary = remove, keepFiles = true)
  }

  internal fun onDismiss() {
    _state.value = null
  }

  internal fun onKeepFilesChange(keep: Boolean) {
    _state.value = _state.value?.copy(keepFiles = keep)
  }

  internal fun onConfirm() {
    val state = _state.value ?: return
    _state.value = null
    scope.launch {
      bookReset.reset(state.id, keepFiles = state.keepFiles)
      if (state.removeFromLibrary) {
        removeFromLibrary(state.id)
      } else {
        navigator.goTo(Destination.BookSetup(state.id, autoStart = true))
      }
    }
  }

  private suspend fun removeFromLibrary(bookId: BookId) {
    val uri = bookId.toUri()
    audiobookFolders.remove(uri, FolderType.SingleFile)
    // The folder list is saved in the background, so the scan waits until the file has left it.
    val _ = withTimeoutOrNull(5.seconds) {
      audiobookFolders.all().first { folders -> folders[FolderType.SingleFile].orEmpty().none { it.uri == uri } }
    }
    mediaScanTrigger.scan(restartIfScanning = true)
  }

  private suspend fun isSingleFile(bookId: BookId): Boolean {
    val uri = bookId.toUri()
    return audiobookFolders.all().first()[FolderType.SingleFile].orEmpty().any { it.uri == uri }
  }
}

data class ResetBookViewState(
  val id: BookId,
  val removeFromLibrary: Boolean,
  val keepFiles: Boolean,
)
