package voice.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R as StringsR

/** Confirms resetting a book's Earwig setup, or with [removeFromLibrary] taking the book off the library too. */
@Composable
fun ResetBookDialog(
  removeFromLibrary: Boolean,
  keepFiles: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
  onKeepFilesChange: (Boolean) -> Unit,
) {
  val remove = removeFromLibrary
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(stringResource(if (remove) StringsR.string.book_remove_dialog_title else StringsR.string.book_reset_dialog_title))
    },
    confirmButton = {
      Button(onClick = onConfirm) {
        Text(stringResource(if (remove) StringsR.string.book_remove_dialog_remove else StringsR.string.book_reset_dialog_reset))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.common_dialog_cancel))
      }
    },
    text = {
      Column {
        Text(stringResource(if (remove) StringsR.string.book_remove_dialog_message else StringsR.string.book_reset_dialog_message))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clickable { onKeepFilesChange(!keepFiles) },
        ) {
          Checkbox(checked = keepFiles, onCheckedChange = onKeepFilesChange)
          Text(stringResource(StringsR.string.book_reset_dialog_keep_files))
        }
      }
    },
  )
}
