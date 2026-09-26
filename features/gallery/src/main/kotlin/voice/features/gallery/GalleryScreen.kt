package voice.features.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.ui.VoiceTheme
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.core.strings.R as StringsR

@Composable
fun GalleryScreen(navigator: Navigator) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = StringsR.string.copilot_action_gallery)) },
        navigationIcon = {
          IconButton(onClick = navigator::goBack) {
            Icon(
              imageVector = VoiceIcons.Close,
              contentDescription = stringResource(id = StringsR.string.common_action_close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(id = StringsR.string.copilot_gallery_empty),
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}

@Composable
@Preview
private fun GalleryScreenPreview() {
  VoiceTheme {
    GalleryScreen(navigator = Navigator())
  }
}

@ContributesTo(AppScope::class)
interface GalleryProvider {

  @Provides
  @IntoSet
  fun galleryNavEntryProvider(navigator: Navigator): NavEntryProvider<*> = NavEntryProvider<Destination.Gallery> { key ->
    NavEntry(key) {
      GalleryScreen(navigator = navigator)
    }
  }
}
