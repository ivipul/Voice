package voice.features.playbackScreen.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import voice.core.companion.SceneNote
import voice.core.ui.crawl.CrawlDisplayType
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.hardShadow
import voice.features.playbackScreen.BookPlayDialogViewState
import voice.core.strings.R as StringsR

private val CardShape = RoundedCornerShape(24.dp)

/**
 * A scene chip's card, in the book's own colors and display type: the name, one line on who or what it is, and what
 * the book has said about it so far. Notes appear as playback reaches them, so the card never gets ahead of the story.
 */
@Composable
internal fun SceneCardDialog(
  dialogState: BookPlayDialogViewState.SceneCardDialog,
  positionMs: Long,
  palette: CrawlPalette,
  type: CrawlDisplayType,
  onExtraClick: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val notes = knownNotes(dialogState.notes, positionMs)
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 560.dp)
        .hardShadow(color = palette.shadow, shape = CardShape, x = 6.dp, y = 6.dp)
        .clip(CardShape)
        .background(palette.background)
        .border(width = 2.dp, color = palette.content, shape = CardShape)
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(if (dialogState.fiction) StringsR.string.scene_card_character else StringsR.string.scene_card_topic).uppercase(),
        style = CrawlType.label,
        color = palette.onHighlight,
        modifier = Modifier
          .background(color = palette.highlight, shape = CircleShape)
          .padding(horizontal = 10.dp, vertical = 5.dp),
      )
      Text(
        text = if (type.uppercase) dialogState.name.uppercase() else dialogState.name,
        style = TextStyle(
          fontFamily = type.fontFamily,
          fontWeight = type.fontWeight,
          fontSize = minOf(type.fontSize.value, 38F).sp,
          lineHeight = minOf(type.fontSize.value, 38F).sp * 1.05,
        ),
        color = palette.content,
      )
      if (dialogState.summary.isNotBlank()) {
        Text(text = dialogState.summary, style = MaterialTheme.typography.bodyLarge, color = palette.content)
      }
      if (notes.isNotEmpty()) {
        Text(
          text = stringResource(StringsR.string.scene_card_so_far).uppercase(),
          style = CrawlType.label,
          color = palette.content.copy(alpha = 0.7F),
          modifier = Modifier.padding(top = 4.dp),
        )
        notes.forEach { note ->
          Row(verticalAlignment = Alignment.Top) {
            Box(
              modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(color = palette.highlight, shape = CircleShape),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = note, style = MaterialTheme.typography.bodyMedium, color = palette.content)
          }
        }
      }
      val extraId = dialogState.extraId
      if (extraId != null) {
        Button(
          onClick = { onExtraClick(extraId) },
          colors = ButtonDefaults.buttonColors(containerColor = palette.highlight, contentColor = palette.onHighlight),
          modifier = Modifier.padding(top = 8.dp),
        ) {
          Text(text = stringResource(StringsR.string.scene_card_open_extra, dialogState.extraTitle.orEmpty()))
        }
      }
    }
  }
}

internal fun knownNotes(notes: List<SceneNote>, positionMs: Long): List<String> =
  notes.filter { it.fromMs <= positionMs }.map { it.text }
