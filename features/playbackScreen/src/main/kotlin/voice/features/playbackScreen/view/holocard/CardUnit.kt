package voice.features.playbackScreen.view.holocard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * The card is laid out in units of 1% of its width (the mockup's `cqw`), so every size scales with the card.
 * The card is 100 units wide and [CardHeightUnits] units tall.
 */
@Immutable
internal class CardUnit(
  val pxPerUnit: Float,
  private val density: Density,
) {

  fun px(units: Float): Float = units * pxPerUnit

  fun dp(units: Float): Dp = with(density) { px(units).toDp() }

  fun sp(units: Float): TextUnit = with(density) { px(units).toSp() }
}

internal const val CardHeightUnits = 140f
