package voice.features.playbackScreen.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.features.playbackScreen.BookPlayViewState

private val ChipCornerRadius = 999.dp

@Composable
internal fun XRayChipRow(
  chips: List<BookPlayViewState.XRayChipViewState>,
  onChipClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (chips.isEmpty()) return
  LazyRow(
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(chips, key = { it.id }) { chip ->
      XRayChip(label = chip.label, onClick = { onChipClick(chip.id) })
    }
  }
}

@Composable
private fun XRayChip(
  label: String,
  onClick: () -> Unit,
) {
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(ChipCornerRadius),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = MaterialTheme.colorScheme.onSurface,
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
