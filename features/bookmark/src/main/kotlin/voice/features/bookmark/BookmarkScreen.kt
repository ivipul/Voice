package voice.features.bookmark

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.ui.FrameCardViewer
import voice.core.ui.InventoryContent
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.strings.R as StringsR

private enum class InventoryTab(val labelRes: Int) {
  Snips(StringsR.string.bookmark_tab_snips),
  Extras(StringsR.string.bookmark_tab_extras),
}

@ContributesTo(AppScope::class)
interface Graph {
  val bookmarkViewModelFactory: BookmarkViewModel.Factory
}

@ContributesTo(AppScope::class)
interface BookmarkProvider {

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
private fun Inventory(
  bookId: BookId,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<Graph>().bookmarkViewModelFactory.create(bookId)
  }
  val viewState = viewModel.viewState()
  CrawlTheme(bookStyle = viewState.bookStyle) {
    Inventory(
      viewState = viewState,
      onSnipClick = {
        viewModel.onSnipClick(it)
        onClose()
      },
      onSnipDelete = viewModel::onSnipDelete,
      // The sheet stays open, so closing the Explorable comes back to this tab.
      onExtraClick = viewModel::onExtraClick,
      onMoreExtrasClick = {
        viewModel.onMoreExtrasClick()
        onClose()
      },
      modifier = modifier,
    )
  }
}

@Composable
internal fun Inventory(
  viewState: BookmarkViewState,
  onSnipClick: (SnipCardViewState) -> Unit,
  onSnipDelete: (SnipCardViewState) -> Unit,
  onExtraClick: (ExtraCardViewState) -> Unit,
  onMoreExtrasClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by rememberSaveable { mutableStateOf(InventoryTab.Snips) }
  var viewedFrame by remember { mutableStateOf<SnipCardViewState?>(null) }
  val palette = crawlPaletteOf(viewState.bookStyle)

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
      InventoryTab.Extras -> ExtrasShelf(
        extras = viewState.extras,
        palette = palette,
        onClick = onExtraClick,
        onMoreClick = onMoreExtrasClick,
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
