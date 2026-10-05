package voice.features.bookmark

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import voice.core.ui.InventoryContent
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.crawl.crawlPaletteOf
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
  fun stripStoryNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.StripStory> { key ->
    NavEntry(key) {
      StripStoryScreen(key)
    }
  }

  @Provides
  fun inventoryContent(): InventoryContent = object : InventoryContent {
    @Composable
    override fun Content(
      bookId: BookId,
      onClose: () -> Unit,
      modifier: Modifier,
    ) {
      Inventory(bookId = bookId, onClose = onClose, modifier = modifier)
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
private fun Inventory(
  bookId: BookId,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<Graph>().bookmarkViewModelFactory.create(bookId)
  }
  val viewState = viewModel.viewState()
  CrawlTheme(edition = viewState.edition) {
    Inventory(
      viewState = viewState,
      onSnipClick = {
        viewModel.onSnipClick(it)
        onClose()
      },
      onSnipDelete = viewModel::onSnipDelete,
      onStripClick = viewModel::onStripClick,
      modifier = modifier,
    )
  }
}

@Composable
internal fun Inventory(
  viewState: BookmarkViewState,
  onSnipClick: (SnipCardViewState) -> Unit,
  onSnipDelete: (SnipCardViewState) -> Unit,
  onStripClick: (AvailableStrip) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf(InventoryTab.Snips) }
  var viewedFrame by remember { mutableStateOf<SnipCardViewState?>(null) }
  val palette = crawlPaletteOf(viewState.edition)

  Column(modifier = modifier) {
    SecondaryTabRow(
      selectedTabIndex = selectedTab.ordinal,
      containerColor = Color.Transparent,
    ) {
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
        onDelete = onSnipDelete,
      )
      InventoryTab.Strips -> StripGallery(
        strips = viewState.strips,
        lockedStrips = viewState.lockedStrips,
        palette = palette,
        onClick = onStripClick,
      )
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
