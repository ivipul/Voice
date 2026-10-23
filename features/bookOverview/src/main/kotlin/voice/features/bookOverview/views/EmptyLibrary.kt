package voice.features.bookOverview.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.DefaultPalette
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

/** The library before the first book: one line on what to pick and a button to pick it. */
@Composable
internal fun EmptyLibrary(
  onAddBookClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 40.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(StringsR.string.library_empty_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Text(
      text = stringResource(StringsR.string.library_empty_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Button(
      onClick = onAddBookClick,
      shape = RoundedCornerShape(16.dp),
      colors = ButtonDefaults.buttonColors(containerColor = DefaultPalette.highlight, contentColor = DefaultPalette.onHighlight),
      modifier = Modifier.padding(top = 12.dp),
    ) {
      Icon(imageVector = VoiceIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Text(
        text = stringResource(StringsR.string.library_empty_add),
        style = CrawlType.button,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}
