package voice.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import voice.core.data.BookId

/** A book's Inventory, the snips and strips: drawn by `:features:bookmark` and shown by the player's Inventory sheet. */
interface InventoryContent {

  /** [onClose] is for when picking something in the Inventory hands over to the player, so the sheet can close. */
  @Composable
  fun Content(
    bookId: BookId,
    onClose: () -> Unit,
    modifier: Modifier,
  )
}
