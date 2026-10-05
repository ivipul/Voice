package voice.features.playbackScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.copilot.snipTextOf
import voice.features.playbackScreen.view.HoloImageLoader
import voice.core.strings.R as StringsR

@Composable
internal fun SnipBottomSheet(
  state: SnipSheetViewState,
  frame: SnipFrameViewState,
  onDismiss: () -> Unit,
) {
  val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 2 / 3
  ModalBottomSheet(
    onDismissRequest = onDismiss,
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
          text = stringResource(StringsR.string.copilot_snip_sheet_title),
          style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.size(16.dp))
        if (frame != SnipFrameViewState.None) {
          BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val imageWidth = snipFrameWidthDp(maxWidth.value, LocalConfiguration.current.screenHeightDp).dp
            HoloImageLoader(
              imagePath = (frame as? SnipFrameViewState.Image)?.path,
              contentDescription = null,
              modifier = Modifier.width(imageWidth),
            )
          }
          Spacer(modifier = Modifier.size(16.dp))
        }
        when (state) {
          is SnipSheetViewState.Loading -> {
            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text(
                text = stringResource(StringsR.string.copilot_snip_sheet_loading),
                style = MaterialTheme.typography.bodyLarge,
              )
            }
          }
          is SnipSheetViewState.Ready -> {
            val snip = snipTextOf(state.text)
            snip.title?.let { title ->
              Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
              text = snip.text,
              style = MaterialTheme.typography.bodyLarge,
            )
          }
        }
      }
    },
  )
}
