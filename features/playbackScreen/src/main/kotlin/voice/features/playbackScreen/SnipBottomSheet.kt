package voice.features.playbackScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import voice.core.copilot.snipTextOf
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.crawl.hardShadow
import voice.core.ui.icons.VoiceIcons
import voice.features.playbackScreen.view.SnipFrameLoader
import voice.core.strings.R as StringsR

private val SheetShape = RoundedCornerShape(24.dp)
private val FrameShape = RoundedCornerShape(16.dp)

/** The snipped moment as a card at the bottom, drawn like the speed sheet: the book's colors, hard shadow and display face. */
@Composable
internal fun SnipBottomSheet(
  state: SnipSheetViewState,
  frame: SnipFrameViewState,
  edition: CrawlEdition?,
  onDismiss: () -> Unit,
) {
  val palette = crawlPaletteOf(edition)
  val type = edition?.chapterType ?: CrawlDisplayType.Default
  val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 2 / 3
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
    shape = RectangleShape,
    containerColor = Color.Transparent,
    contentColor = palette.content,
    tonalElevation = 0.dp,
    dragHandle = null,
    content = {
      Column(
        modifier = Modifier
          .padding(10.dp)
          .hardShadow(color = palette.shadow, shape = SheetShape, x = (-6).dp, y = 6.dp)
          .clip(SheetShape)
          .background(palette.background)
          .border(width = 1.5.dp, color = palette.content, shape = SheetShape)
          .heightIn(max = maxHeight)
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 20.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(StringsR.string.copilot_snip_sheet_title).uppercase(),
            style = CrawlType.label,
            modifier = Modifier.weight(1F),
          )
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = VoiceIcons.Close,
              contentDescription = stringResource(StringsR.string.common_action_close),
            )
          }
        }
        Column(modifier = Modifier.padding(end = 12.dp)) {
          Spacer(modifier = Modifier.size(6.dp))
          if (frame != SnipFrameViewState.None) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              val imageWidth = snipFrameWidthDp(maxWidth.value, LocalConfiguration.current.screenHeightDp).dp
              SnipFrameLoader(
                imagePath = (frame as? SnipFrameViewState.Image)?.path,
                contentDescription = null,
                modifier = Modifier
                  .width(imageWidth)
                  .hardShadow(color = palette.shadow, shape = FrameShape, x = 4.dp, y = 4.dp)
                  .border(width = 1.5.dp, color = palette.content, shape = FrameShape),
                shape = FrameShape,
              )
            }
            Spacer(modifier = Modifier.size(20.dp))
          }
          when (state) {
            is SnipSheetViewState.Loading -> {
              Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = palette.highlight,
                  strokeWidth = 2.dp,
                )
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
                  style = TextStyle(fontFamily = type.fontFamily, fontWeight = type.fontWeight, fontSize = 32.sp, lineHeight = 36.sp),
                  color = palette.highlight,
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
      }
    },
  )
}
