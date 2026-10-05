package voice.features.playbackScreen.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.icons.VoiceIcons

/** The chapter in the cover's display face, big and in the title color, with the time at its baseline. */
@Composable
internal fun ChapterLine(
  chapterName: String?,
  time: String,
  palette: CrawlPalette,
  type: CrawlDisplayType,
  onChapterClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier
        .weight(1F)
        .clickable(enabled = chapterName != null, onClick = onChapterClick),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicText(
        text = chapterName?.let { if (type.uppercase) it.uppercase() else it }.orEmpty(),
        modifier = Modifier.weight(1F, fill = false),
        style = TextStyle(
          color = palette.highlight,
          fontFamily = type.fontFamily,
          fontWeight = type.fontWeight,
          fontSize = type.fontSize,
          lineHeight = 1.05.em,
          letterSpacing = 0.01.em,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = type.fontSize, stepSize = 2.sp),
      )
      if (chapterName != null) {
        Icon(
          imageVector = VoiceIcons.ExpandMore,
          contentDescription = null,
          tint = palette.highlight,
          modifier = Modifier.size(28.dp),
        )
      }
    }
    Text(
      text = time,
      style = CrawlType.time,
      color = palette.content,
      maxLines = 1,
      modifier = Modifier.padding(bottom = 4.dp),
    )
  }
}
