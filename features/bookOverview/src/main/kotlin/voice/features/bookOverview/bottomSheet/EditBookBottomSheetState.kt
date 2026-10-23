package voice.features.bookOverview.bottomSheet

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

internal data class EditBookBottomSheetState(val items: List<BottomSheetItem>)

enum class BottomSheetItem(
  @StringRes val titleRes: Int,
  val icon: ImageVector,
) {
  BookSetup(StringsR.string.playback_option_book_setup, VoiceIcons.AutoAwesome),
  ResetSetup(StringsR.string.book_reset_setup, VoiceIcons.History),
  Title(StringsR.string.book_edit_name_label, VoiceIcons.Title),
  InternetCover(StringsR.string.book_edit_cover_internet, VoiceIcons.Download),
  FileCover(StringsR.string.book_edit_cover_file, VoiceIcons.Image),
  RemoveFromLibrary(StringsR.string.book_remove_from_library, VoiceIcons.Remove),
  DeleteBook(StringsR.string.book_delete_bottom_sheet_title, VoiceIcons.Delete),
  BookCategoryMarkAsNotStarted(StringsR.string.book_category_action_mark_not_started, VoiceIcons.HourglassEmpty),
  BookCategoryMarkAsCurrent(StringsR.string.book_category_action_mark_current, VoiceIcons.NotStarted),
  BookCategoryMarkAsCompleted(StringsR.string.book_category_action_mark_completed, VoiceIcons.Done),
}
