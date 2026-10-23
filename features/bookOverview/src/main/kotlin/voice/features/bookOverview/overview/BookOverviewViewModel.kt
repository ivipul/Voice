package voice.features.bookOverview.overview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.common.comparator.sortedNaturally
import voice.core.companion.BookLook
import voice.core.companion.CompanionStores
import voice.core.companion.setup.TranscriptImporter
import voice.core.data.Book
import voice.core.data.BookComparator
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.KioskModeDemoData
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.FolderPickerMovedDialogShownStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.LibraryUniformFontStore
import voice.core.featureflag.ExperimentalPlaybackPersistenceQualifier
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.FolderPickerInSettingsFeatureFlagQualifier
import voice.core.featureflag.KioskModeFeatureFlagQualifier
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.scanner.MediaScanTrigger
import voice.core.search.BookSearch
import voice.core.ui.GridCount
import voice.core.ui.crawl.toStyle
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.search.BookSearchViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Instant

@SingleIn(BookOverviewScope::class)
@Inject
class BookOverviewViewModel(
  private val repo: BookRepository,
  private val mediaScanner: MediaScanTrigger,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  @CurrentBookStore
  private val currentBookStoreDataStore: DataStore<BookId?>,
  @FolderPickerMovedDialogShownStore
  private val folderPickerMovedDialogShownStore: DataStore<Boolean>,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  @LibraryUniformFontStore
  private val libraryUniformFontStore: DataStore<Boolean>,
  private val gridCount: GridCount,
  private val navigator: Navigator,
  private val appInfoProvider: AppInfoProvider,
  private val recentBookSearchDao: RecentBookSearchDao,
  private val search: BookSearch,
  private val contentRepo: BookContentRepo,
  private val deviceHasStoragePermissionBug: DeviceHasStoragePermissionBug,
  @FolderPickerInSettingsFeatureFlagQualifier
  private val folderPickerInSettingsFeatureFlag: FeatureFlag<Boolean>,
  @ExperimentalPlaybackPersistenceQualifier
  private val experimentalPlaybackPersistenceFeatureFlag: FeatureFlag<Boolean>,
  @KioskModeFeatureFlagQualifier
  private val kioskModeFeatureFlag: FeatureFlag<Boolean>,
  private val stores: CompanionStores,
  private val audiobookFolders: AudiobookFolders,
  private val transcriptImporter: TranscriptImporter,
  dispatcherProvider: DispatcherProvider,
) {

  private val scope = MainScope(dispatcherProvider)
  private var searchActive by mutableStateOf(false)
  private var query by mutableStateOf("")
  private var dialog by mutableStateOf<BookOverviewViewState.Dialog?>(null)

  fun attach() {
    mediaScanner.scan()
  }

  @Composable
  internal fun state(): BookOverviewViewState {
    val kioskMode = remember { kioskModeFeatureFlag.get() }
    if (kioskMode) return kioskModeState()

    val playState = remember { playStateManager.playStateFlow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value
    val hasStoragePermissionBug = remember { deviceHasStoragePermissionBug.hasBug }
      .collectAsState().value
    val books = remember { repo.flow() }
      .collectAsState(initial = emptyList()).value
    val currentBookId = remember { currentBookStoreDataStore.data }
      .collectAsState(initial = null).value
    val scannerActive = remember { mediaScanner.scannerActive }
      .collectAsState(initial = false).value
    val folderPickerMovedDialogShown = remember { folderPickerMovedDialogShownStore.data }
      .collectAsState(initial = false).value
    val uniformFont = remember { libraryUniformFontStore.data }
      .collectAsState(initial = false).value
    val gridMode = remember { gridModeStore.data }
      .collectAsState(initial = null).value
      ?: return BookOverviewViewState.Loading
    val bookIds = books.map { it.id }
    val looks = remember(bookIds) { looksOf(bookIds) }.collectAsState(initial = emptyMap()).value

    val noBooks = !scannerActive && books.isEmpty()

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
        BookOverviewLayoutMode.Grid
      } else {
        BookOverviewLayoutMode.List
      }
    }

    val bookSearchViewState = bookSearchViewState(layoutMode)
    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState: State<LivePlaybackState?> = if (experimentalPlaybackPersistence && currentBookId != null) {
      remember(currentBookId) {
        playerController.livePlaybackStateFlow(currentBookId)
      }.collectAsState(null)
    } else {
      remember { mutableStateOf(null) }
    }

    return BookOverviewViewState(
      layoutMode = layoutMode,
      books = mapOf(
        BookOverviewCategory.CURRENT to books
          .sortedWith(BookComparator.ByName)
          .associate { book ->
            book.id to book.itemViewState(
              currentBookId = currentBookId,
              look = looks[book.id],
              livePlaybackState = { livePlaybackState.value },
            )
          },
      ),
      playButtonState = if (playState == PlayStateManager.PlayState.Playing) {
        BookOverviewViewState.PlayButtonState.Playing
      } else {
        BookOverviewViewState.PlayButtonState.Paused
      }.takeIf { currentBookId != null },
      uniformFont = uniformFont,
      activeBookStyle = currentBookId?.let { looks[it] }?.toStyle(),
      showAddBookHint = if (hasStoragePermissionBug) {
        false
      } else {
        noBooks
      },
      showSearchIcon = books.isNotEmpty(),
      isLoading = scannerActive,
      searchActive = searchActive,
      searchViewState = bookSearchViewState,
      showStoragePermissionBugCard = hasStoragePermissionBug,
      showFolderPickerIcon = !folderPickerInSettingsFeatureFlag.get() &&
        !folderPickerMovedDialogShown &&
        appInfoProvider.installTime < FolderPickerMigrationInstallTimeCutoff,
      dialog = dialog,
    )
  }

  @Composable
  private fun bookSearchViewState(layoutMode: BookOverviewLayoutMode): BookSearchViewState {
    return if (searchActive) {
      val recentBookSearch = remember {
        recentBookSearchDao.recentBookSearches()
      }.collectAsState(initial = emptyList()).value.reversed()
      var searchBooks by remember {
        mutableStateOf(emptyList<BookOverviewItemViewState>())
      }
      LaunchedEffect(query) {
        searchBooks = search.search(query).map { it.toItemViewState() }
      }
      val suggestedAuthors: List<String> by produceState(initialValue = emptyList()) {
        value = contentRepo.all()
          .filter { it.isActive }
          .mapNotNull { it.author }
          .toSet()
          .sortedNaturally()
      }

      val bookSearchViewState = if (query.isNotBlank()) {
        BookSearchViewState.SearchResults(
          query = query,
          books = searchBooks,
          layoutMode = layoutMode,
        )
      } else {
        BookSearchViewState.EmptySearch(
          recentQueries = recentBookSearch,
          suggestedAuthors = suggestedAuthors,
          query = query,
        )
      }
      bookSearchViewState
    } else {
      BookSearchViewState.EmptySearch(
        recentQueries = emptyList(),
        suggestedAuthors = emptyList(),
        query = query,
      )
    }
  }

  private fun kioskModeState(): BookOverviewViewState {
    return BookOverviewViewState(
      layoutMode = BookOverviewLayoutMode.List,
      books = mapOf(
        BookOverviewCategory.CURRENT to KioskModeDemoData.demoAudiobooks.associate { book ->
          book.id to mutableStateOf(
            BookOverviewItemViewState(
              name = book.title,
              author = book.author,
              cover = book.coverUrl,
              progress = book.progress / 100F,
              id = book.id,
              remainingTime = book.remaining,
            ),
          )
        },
      ),
      playButtonState = BookOverviewViewState.PlayButtonState.Paused,
      showAddBookHint = false,
      showSearchIcon = true,
      isLoading = false,
      searchActive = false,
      searchViewState = BookSearchViewState.EmptySearch(
        recentQueries = emptyList(),
        suggestedAuthors = KioskModeDemoData.demoAudiobooks.map { it.author },
        query = "",
      ),
      showStoragePermissionBugCard = false,
      showFolderPickerIcon = false,
      dialog = null,
    )
  }

  fun onSettingsClick() {
    navigator.goTo(Destination.Settings)
  }

  /** A book file picked with the library's add button: it joins the library and its guided setup opens. */
  /**
   * Audio files, each optionally picked with its transcript (which then skips cloud transcription) and companion
   * PDFs (which Claude studies for Extras).
   */
  fun onFilesPicked(uris: List<Uri>) {
    scope.launch {
      val picked = transcriptImporter.sort(uris)
      if (picked.isEmpty()) return@launch
      picked.forEach { book ->
        audiobookFolders.add(book.audio, FolderType.SingleFile)
        transcriptImporter.import(book)
      }
      navigator.goTo(Destination.BookSetup(BookId(picked.first().audio), autoStart = true))
      // The folder list is saved in the background, so the scan waits until it holds the new files.
      val _ = withTimeoutOrNull(5.seconds) {
        audiobookFolders.all().first { folders ->
          val saved = folders.values.flatten().map { it.uri }.toSet()
          picked.all { it.audio in saved }
        }
      }
      mediaScanner.scan(restartIfScanning = true)
    }
  }

  private fun looksOf(ids: List<BookId>): Flow<Map<BookId, BookLook>> {
    if (ids.isEmpty()) return flowOf(emptyMap())
    return combine(ids.map { id -> stores.look.flow(id) }) { looks -> ids.zip(looks).toMap() }
  }

  fun onBookClick(id: BookId) {
    navigator.goTo(Destination.Playback(id))
  }

  fun onBookFolderClick() {
    dialog = BookOverviewViewState.Dialog.FolderPickerMovedToSettings
  }

  fun onFolderPickerMovedDialogDismiss() {
    dialog = null
    scope.launch {
      folderPickerMovedDialogShownStore.updateData { true }
    }
  }

  fun onSearchActiveChange(active: Boolean) {
    if (active && !searchActive) {
      query = ""
    }
    this.searchActive = active
  }

  fun onSearchQueryChange(query: String) {
    this.query = query
  }

  fun onSearchBookClick(id: BookId) {
    val query = query.trim()
    if (query.isNotBlank()) {
      scope.launch {
        recentBookSearchDao.add(query)
      }
    }
    searchActive = false
    navigator.goTo(Destination.Playback(id))
  }

  /** Starts or pauses the current book, and opens the player when it starts. */
  fun onPlayButtonClick(playing: Boolean) {
    playerController.playPause()
    if (!playing) {
      scope.launch {
        currentBookStoreDataStore.data.first()?.let { navigator.goTo(Destination.Playback(it)) }
      }
    }
  }

  fun onPermissionBugCardClick() {
    if (Build.VERSION.SDK_INT >= 30) {
      navigator.goTo(
        Destination.Activity(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:com.android.externalstorage".toUri()),
        ),
      )
    }
  }
}

private val FolderPickerMigrationInstallTimeCutoff = Instant.parse("2026-06-17T00:00:00Z")

@Composable
private fun Book.itemViewState(
  currentBookId: BookId?,
  look: BookLook?,
  livePlaybackState: () -> LivePlaybackState?,
): State<BookOverviewItemViewState> {
  if (id != currentBookId) {
    return rememberUpdatedState(toItemViewState(look))
  }
  val currentPlaybackState by rememberUpdatedState(livePlaybackState)
  return remember(this, currentBookId, look) {
    derivedStateOf {
      val livePlayback = currentPlaybackState()
      if (livePlayback != null) {
        overlay(livePlayback)
      } else {
        this
      }.toItemViewState(look)
    }
  }
}
