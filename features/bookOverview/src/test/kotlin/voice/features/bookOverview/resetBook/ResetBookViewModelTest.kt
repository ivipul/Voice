package voice.features.bookOverview.resetBook

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import voice.core.companion.work.BookReset
import voice.core.data.BookId
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.DocumentFileWithUri
import voice.core.data.folders.FolderType
import voice.core.scanner.MediaScanTrigger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ResetBookViewModelTest {

  private val singleFileBook = BookId("content://books/document/good-energy.m4b")
  private val folderBook = BookId("content://books/tree/audiobooks/document/other")
  private val folders = MutableStateFlow(
    mapOf(FolderType.SingleFile to listOf(DocumentFileWithUri(mockk(), singleFileBook.toUri()))),
  )
  private val audiobookFolders = mockk<AudiobookFolders> {
    every { all() } returns folders
    every { remove(any(), any()) } answers { folders.value = emptyMap() }
  }
  private val bookReset = mockk<BookReset> { coEvery { reset(any(), any()) } just Runs }
  private val mediaScanTrigger = mockk<MediaScanTrigger> { every { scan(any()) } just Runs }
  private val navigator = mockk<Navigator> { every { goTo(any()) } just Runs }
  private val viewModel = ResetBookViewModel(bookReset, audiobookFolders, mediaScanTrigger, navigator)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun onlyASingleFileBookCanBeRemovedFromTheLibrary() = runTest {
    assertEquals(listOf(BottomSheetItem.ResetSetup, BottomSheetItem.RemoveFromLibrary), viewModel.items(singleFileBook))
    assertEquals(listOf(BottomSheetItem.ResetSetup), viewModel.items(folderBook))
  }

  @Test
  fun resetClearsTheSetupAndOpensItAgain() = runTest {
    viewModel.onItemClick(singleFileBook, BottomSheetItem.ResetSetup)
    assertEquals(ResetBookViewState(singleFileBook, removeFromLibrary = false, keepFiles = true), viewModel.state.value)

    viewModel.onConfirm()

    assertNull(viewModel.state.value)
    coVerify { bookReset.reset(singleFileBook, keepFiles = true) }
    verify { navigator.goTo(Destination.BookSetup(singleFileBook, autoStart = true)) }
    verify(exactly = 0) { audiobookFolders.remove(any(), any()) }
  }

  @Test
  fun removeClearsTheSetupAndTakesTheFileOffTheLibrary() = runTest {
    viewModel.onItemClick(singleFileBook, BottomSheetItem.RemoveFromLibrary)
    viewModel.onKeepFilesChange(false)

    viewModel.onConfirm()

    coVerify { bookReset.reset(singleFileBook, keepFiles = false) }
    verify { audiobookFolders.remove(singleFileBook.toUri(), FolderType.SingleFile) }
    verify { mediaScanTrigger.scan(restartIfScanning = true) }
    verify(exactly = 0) { navigator.goTo(any()) }
  }
}
