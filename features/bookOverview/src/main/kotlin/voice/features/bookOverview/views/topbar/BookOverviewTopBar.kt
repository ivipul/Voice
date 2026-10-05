package voice.features.bookOverview.views.topbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import voice.core.strings.R as StringsR
import voice.core.ui.VoiceTheme
import voice.core.ui.crawl.CrawlType
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.overview.BookOverviewViewState
import voice.features.bookOverview.search.BookSearchViewState
import voice.features.bookOverview.views.BookFolderIcon
import voice.features.bookOverview.views.SettingsIcon
import kotlin.time.Duration.Companion.seconds
import voice.core.strings.R as StringsR

/** The library's title in the author-name face of the cards, with the folder and settings buttons to its right. */
@Composable
internal fun BookOverviewTopBar(
  viewState: BookOverviewViewState,
  onBookFolderClick: () -> Unit,
  onSettingsClick: () -> Unit,
) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(start = 24.dp, end = 12.dp, top = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        modifier = Modifier.weight(1F),
        text = stringResource(StringsR.string.library_title),
        style = CrawlType.label.copy(fontSize = 28.sp, letterSpacing = 0.02.em),
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (viewState.showFolderPickerIcon) {
        BookFolderIcon(withHint = viewState.showAddBookHint, onClick = onBookFolderClick)
      }
      SettingsIcon(onSettingsClick)
    }
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(viewState.isLoading) {
      if (viewState.isLoading) {
        delay(3.seconds)
      }
      showLoading = viewState.isLoading
    }
    if (showLoading) {
      LinearProgressIndicator(
        Modifier
          .padding(top = 12.dp)
          .fillMaxWidth(),
      )
    }
  }
}

@Composable
@Preview
private fun BookOverviewTopBarPreview() {
  VoiceTheme {
    BookOverviewTopBar(
      viewState = BookOverviewViewState(
        books = emptyMap(),
        layoutMode = BookOverviewLayoutMode.List,
        playButtonState = BookOverviewViewState.PlayButtonState.Paused,
        showAddBookHint = true,
        showSearchIcon = true,
        isLoading = true,
        searchActive = true,
        searchViewState = BookSearchViewState.EmptySearch(
          suggestedAuthors = listOf(),
          recentQueries = listOf(),
          query = "",
        ),
        showStoragePermissionBugCard = false,
        showFolderPickerIcon = true,
        dialog = null,
      ),
      onBookFolderClick = {},
      onSettingsClick = {},
    )
  }
}
