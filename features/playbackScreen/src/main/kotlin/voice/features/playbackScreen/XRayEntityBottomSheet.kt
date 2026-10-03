package voice.features.playbackScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun XRayEntityBottomSheet(
  dialogState: BookPlayDialogViewState.XRayEntityDialog,
  viewModel: BookPlayViewModel,
) {
  val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 2 / 3
  ModalBottomSheet(
    onDismissRequest = { viewModel.dismissDialog() },
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
    content = {
      Column(
        modifier = Modifier
          .heightIn(max = maxHeight)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp)
          .padding(bottom = 32.dp),
      ) {
        Text(
          text = dialogState.title,
          style = MaterialTheme.typography.headlineSmall,
        )
        if (dialogState.image != null) {
          Spacer(modifier = Modifier.size(16.dp))
          AsyncImage(
            model = dialogState.image.toImageModel(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 240.dp)
              .clip(RoundedCornerShape(12.dp)),
          )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
          text = dialogState.description,
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    },
  )
}

/**
 * X-Ray entity images are bundled into the app as assets (see `:core:xray`'s `assets/xray/`),
 * referenced by a path relative to that folder. A full `http(s)` URL is still accepted as a
 * fallback for an entity whose image hasn't been bundled yet.
 */
private fun String.toImageModel(): String = if (startsWith("http://") || startsWith("https://")) {
  this
} else {
  "file:///android_asset/xray/$this"
}
