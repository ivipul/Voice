package voice.features.bookSetup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import voice.core.companion.LookSource
import voice.core.ui.crawl.ChapterTypes
import voice.core.ui.crawl.CrawlPalette
import voice.core.strings.R as StringsR

private val LookSource.labelRes: Int
  get() = when (this) {
    LookSource.Default -> StringsR.string.setup_look_default
    LookSource.Cover -> StringsR.string.setup_look_cover
    LookSource.Generated -> StringsR.string.setup_look_generated
  }

/** The book's colors and chapter-name face: the app's own, the cover's, or a cover Earwig draws. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LookSection(
  look: LookViewState,
  canDrawCovers: Boolean,
  palette: CrawlPalette,
  onSourceClick: (LookSource) -> Unit,
  onGenerate: (String) -> Unit,
  onFontClick: (String) -> Unit,
) {
  var prompt by rememberSaveable(look.extraPrompt) { mutableStateOf(look.extraPrompt) }
  SetupCard(palette) {
    SectionLabel(stringResource(StringsR.string.setup_look_label), palette)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      LookSource.entries.forEach { source ->
        val available = when (source) {
          LookSource.Default -> true
          LookSource.Cover -> look.hasCover
          LookSource.Generated -> canDrawCovers
        }
        FilterChip(
          selected = look.source == source,
          enabled = available && !look.working,
          onClick = { onSourceClick(source) },
          label = { Text(text = stringResource(source.labelRes)) },
        )
      }
    }
    if (!canDrawCovers) {
      Text(text = stringResource(StringsR.string.setup_look_needs_ideogram), style = MaterialTheme.typography.bodySmall)
    } else {
      OutlinedTextField(
        value = prompt,
        onValueChange = { prompt = it },
        enabled = !look.working,
        label = { Text(text = stringResource(StringsR.string.setup_look_prompt_label)) },
        placeholder = { Text(text = stringResource(StringsR.string.setup_look_prompt_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
      )
      Button(onClick = { onGenerate(prompt) }, enabled = !look.working) {
        Text(
          text = stringResource(
            if (look.source == LookSource.Generated) StringsR.string.setup_look_regenerate else StringsR.string.setup_look_generate,
          ),
        )
      }
    }
    if (look.working) Working(stringResource(StringsR.string.setup_look_working))
    look.error?.let { ErrorText(stringResource(StringsR.string.setup_look_failed, it)) }
    Text(text = stringResource(StringsR.string.setup_look_font), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ChapterTypes.forEach { (key, type) ->
        val selected = key == look.displayFont
        Text(
          text = if (type.uppercase) "AA" else "Aa",
          style = TextStyle(fontFamily = type.fontFamily, fontWeight = type.fontWeight, fontSize = 26.sp),
          color = if (selected) palette.onHighlight else palette.content,
          modifier = Modifier
            .border(width = 2.dp, color = palette.content, shape = RoundedCornerShape(12.dp))
            .then(
              if (selected) {
                Modifier.background(palette.highlight, RoundedCornerShape(12.dp))
              } else {
                Modifier
              },
            )
            .clickable { onFontClick(key) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        )
      }
    }
  }
}
