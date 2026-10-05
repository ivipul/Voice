package voice.features.playbackScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.crawlPaletteOf
import voice.core.ui.crawl.hardShadow
import voice.core.ui.icons.VoiceIcons
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

private val SpeedRange = 0.5F..3F
private const val SPEED_STEPS = 24
private val QuickSpeeds = listOf(0.8F, 1F, 1.1F, 1.2F, 1.3F, 1.5F, 2F)
private val SheetShape = RoundedCornerShape(24.dp)

/** The playback speed as a card at the bottom: the speed big in the book's display face, a slider and quick picks. */
@Composable
internal fun SpeedSheet(
  speed: Float,
  edition: CrawlEdition?,
  onSpeedChange: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = crawlPaletteOf(edition)
  val type = edition?.chapterType ?: CrawlDisplayType.Default
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = SheetValue.Hidden,
      enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    ),
    shape = RectangleShape,
    containerColor = Color.Transparent,
    contentColor = palette.content,
    tonalElevation = 0.dp,
    dragHandle = null,
  ) {
    Column(
      modifier = Modifier
        .padding(10.dp)
        .hardShadow(color = palette.shadow, shape = SheetShape, x = (-6).dp, y = 6.dp)
        .clip(SheetShape)
        .background(palette.background)
        .border(width = 1.5.dp, color = palette.content, shape = SheetShape)
        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(id = StringsR.string.playback_speed_title).uppercase(),
          style = CrawlType.label,
          modifier = Modifier.weight(1F),
        )
        IconButton(onClick = onDismiss) {
          Icon(
            imageVector = VoiceIcons.Close,
            contentDescription = stringResource(id = StringsR.string.common_action_close),
          )
        }
      }
      Column(
        modifier = Modifier.padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(id = StringsR.string.playback_speed_value, speedLabel(speed)),
          style = TextStyle(fontFamily = type.fontFamily, fontWeight = type.fontWeight, fontSize = 44.sp),
          color = palette.highlight,
        )
        Slider(
          value = speed.coerceIn(SpeedRange),
          onValueChange = { onSpeedChange(snapSpeed(it)) },
          valueRange = SpeedRange,
          steps = SPEED_STEPS,
          colors = SliderDefaults.colors(
            thumbColor = palette.highlight,
            activeTrackColor = palette.highlight,
            inactiveTrackColor = palette.content.copy(alpha = 0.2F),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
          ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          QuickSpeeds.forEach { quickSpeed ->
            QuickSpeed(
              speed = quickSpeed,
              selected = abs(quickSpeed - speed) < 0.01F,
              palette = palette,
              onClick = { onSpeedChange(quickSpeed) },
              modifier = Modifier.weight(1F),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QuickSpeed(
  speed: Float,
  selected: Boolean,
  palette: CrawlPalette,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .height(30.dp)
      .clip(CircleShape)
      .then(
        if (selected) {
          Modifier.background(palette.highlight)
        } else {
          Modifier.border(width = 1.5.dp, color = palette.content, shape = CircleShape)
        },
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = speedLabel(speed),
      style = CrawlType.pill,
      color = if (selected) palette.onHighlight else palette.content,
      maxLines = 1,
    )
  }
}

/** "1.0" or "1.25": one decimal unless the speed needs two. */
internal fun speedLabel(
  speed: Float,
  locale: Locale = Locale.getDefault(),
): String = DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(locale)).format(speed)

/** The slider's value on its 0.1 grid, without the float noise of its steps. */
internal fun snapSpeed(value: Float): Float = (value * 10).roundToInt() / 10F
