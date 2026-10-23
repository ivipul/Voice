package voice.features.bookOverview.views

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.ui.crawl.hardShadow
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

private val FabSize = 56.dp
private val IconSize = 24.dp

/** Adds a book file and opens its guided setup. Sized like the play button on the other side, in the same colors. */
@Composable
internal fun AddBookButton(
  onClick: () -> Unit,
  containerColor: Color,
  contentColor: Color,
  shadowColor: Color,
  modifier: Modifier = Modifier,
) {
  FloatingActionButton(
    modifier = modifier
      .size(FabSize)
      .hardShadow(color = shadowColor, shape = CircleShape, x = 3.dp, y = 3.dp),
    onClick = onClick,
    shape = CircleShape,
    containerColor = containerColor,
    contentColor = contentColor,
    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
  ) {
    Icon(
      modifier = Modifier.size(IconSize),
      imageVector = VoiceIcons.Add,
      contentDescription = stringResource(id = StringsR.string.library_add_book),
    )
  }
}
