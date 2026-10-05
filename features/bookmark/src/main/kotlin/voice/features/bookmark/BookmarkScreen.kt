package voice.features.bookmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.strips.AvailableStrip
import voice.core.strips.StripRepository
import voice.core.ui.FrameCardViewer
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.icons.VoiceIcons
import voice.features.bookmark.strips.StripGallery
import voice.features.bookmark.strips.StripViewer
import voice.features.bookmark.strips.StripViewerViewModel
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.core.strings.R as StringsR

private enum class InventoryTab(val labelRes: Int) {
  Snips(StringsR.string.bookmark_tab_snips),
  Strips(StringsR.string.bookmark_tab_strips),
}

@ContributesTo(AppScope::class)
interface Graph {
  val bookmarkViewModelFactory: BookmarkViewModel.Factory
  val stripViewerViewModelFactory: StripViewerViewModel.Factory
  val stripRepository: StripRepository
  val bookRepository: BookRepository
  val navigator: Navigator
}

@ContributesTo(AppScope::class)
interface BookmarkProvider {

  @Provides
  @IntoSet
  fun bookmarkNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Bookmarks> { key ->
    NavEntry(key) {
      BookmarkScreen(bookId = key.bookId)
    }
  }

  @Provides
  @IntoSet
  fun stripStoryNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.StripStory> { key ->
    NavEntry(key) {
      StripStoryScreen(key)
    }
  }
}

@Composable
fun StripStoryScreen(destination: Destination.StripStory) {
  val graph = rootGraphAs<Graph>()
  val strip by produceState<AvailableStrip?>(null, destination) {
    val book = graph.bookRepository.get(destination.bookId) ?: return@produceState
    value = graph.stripRepository.stripsFor(book.content.name).firstOrNull { it.manifest.chapter == destination.chapter }
  }
  strip?.let { loaded ->
    val storyViewModel = remember(destination, loaded) {
      graph.stripViewerViewModelFactory.create(destination.bookId, loaded, destination.startFrameIndex, destination.linkedToBook)
    }
    StripViewer(viewModel = storyViewModel, onClose = graph.navigator::goBack)
  }
}

@Composable
fun BookmarkScreen(bookId: BookId) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<Graph>().bookmarkViewModelFactory.create(bookId)
  }
  val viewState = viewModel.viewState()
  Box {
    // The open strip viewer is black, so the book's colors (and its dark status bar icons) give way while it shows.
    CrawlTheme(edition = viewState.edition.takeIf { viewState.activeStrip == null }) {
      BookmarkScreen(
        viewState = viewState,
        onClose = viewModel::closeScreen,
        onSnipClick = viewModel::onSnipClick,
        onStripClick = viewModel::onStripClick,
      )
    }
    viewState.activeStrip?.let { strip ->
      val storyViewModel = remember(bookId, strip) {
        rootGraphAs<Graph>().stripViewerViewModelFactory.create(bookId, strip, 0, linkedToBook = false)
      }
      StripViewer(viewModel = storyViewModel, onClose = viewModel::onStripClose)
    }
  }
}

@Composable
internal fun BookmarkScreen(
  viewState: BookmarkViewState,
  onClose: () -> Unit,
  onSnipClick: (SnipCardViewState) -> Unit,
  onStripClick: (AvailableStrip) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf(InventoryTab.Snips) }
  var viewedFrame by remember { mutableStateOf<SnipCardViewState?>(null) }
  val palette = crawlPaletteOf(viewState.edition)

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = StringsR.string.inventory_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              imageVector = VoiceIcons.Close,
              contentDescription = stringResource(id = StringsR.string.common_action_close),
            )
          }
        },
      )
    },
  ) { paddingValues ->
    Column(modifier = Modifier.padding(paddingValues)) {
      SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
        InventoryTab.entries.forEach { tab ->
          Tab(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            text = { Text(text = stringResource(id = tab.labelRes)) },
          )
        }
      }
      when (selectedTab) {
        InventoryTab.Snips -> SnipCardList(
          snips = viewState.snips,
          onClick = onSnipClick,
          onImageClick = { viewedFrame = it },
        )
        InventoryTab.Strips -> StripGallery(
          strips = viewState.strips,
          lockedStrips = viewState.lockedStrips,
          palette = palette,
          onClick = onStripClick,
        )
      }
    }
  }

  viewedFrame?.let { snip ->
    snip.imagePath?.let { path ->
      FrameCardViewer(
        imagePath = path,
        contentDescription = snip.title ?: snip.text,
        onDismiss = { viewedFrame = null },
      )
    }
  }
}
