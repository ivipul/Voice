package voice.navigation

import androidx.compose.material3.ModalBottomSheetProperties
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

object BottomSheetNav {
  object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>

  /** Present when the screen draws its own handle and card, so the sheet adds no container, handle or shape. */
  object BareKey : NavMetadataKey<Boolean>

  fun bottomSheet(
    modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    bare: Boolean = false,
  ) = metadata {
    put(BottomSheetKey, modalBottomSheetProperties)
    if (bare) put(BareKey, true)
  }
}
