package voice.features.playbackScreen.view.holocard

import android.graphics.Typeface
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal val DisplayFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
internal val UiFont = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))

internal val InkColor = Color(0xFFEEF3F1)
internal val InkDimColor = Color(0xFF8D99A0)
internal val PositiveColor = Color(0xFF6DFF9C)
internal val PanelTop = Color(0xFF0A171C)
internal val PanelBottom = Color(0xFF050C0F)

/** Places a box at [left]/[top] of the card, sized [width] x [height], all in card units. */
internal fun Modifier.cardRect(
  u: CardUnit,
  left: Float,
  top: Float,
  width: Float,
  height: Float,
): Modifier = offset { IntOffset(u.px(left).roundToInt(), u.px(top).roundToInt()) }
  .size(u.dp(width), u.dp(height))

/** A CSS-style `linear-gradient(<angle>deg, ...)` that spans the whole of the drawn area. */
internal fun cssLinearGradient(
  angleDegrees: Float,
  size: Size,
  vararg stops: Pair<Float, Color>,
): Brush {
  val radians = Math.toRadians(angleDegrees.toDouble())
  val dx = sin(radians).toFloat()
  val dy = -cos(radians).toFloat()
  val halfLength = (abs(size.width * dx) + abs(size.height * dy)) / 2f
  val center = Offset(size.width / 2f, size.height / 2f)
  return Brush.linearGradient(
    colorStops = stops,
    start = Offset(center.x - dx * halfLength, center.y - dy * halfLength),
    end = Offset(center.x + dx * halfLength, center.y + dy * halfLength),
  )
}

internal fun mix(
  a: Color,
  b: Color,
  amountOfA: Float,
): Color = androidx.compose.ui.graphics.lerp(b, a, amountOfA)

/** Pointy-top hexagon filling [width] x [height]. */
internal fun hexagonPath(
  width: Float,
  height: Float,
  inset: Float = 0f,
): Path = Path().apply {
  val left = inset
  val right = width - inset
  val top = inset
  val bottom = height - inset
  val h = bottom - top
  moveTo(width / 2f, top)
  lineTo(right, top + h * 0.25f)
  lineTo(right, top + h * 0.75f)
  lineTo(width / 2f, bottom)
  lineTo(left, top + h * 0.75f)
  lineTo(left, top + h * 0.25f)
  close()
}

/** One line of card text. Shrinks to [minSize] before it ellipsizes, so long names stay on one line. */
@Composable
internal fun CardText(
  text: String,
  size: TextUnit,
  color: Color,
  modifier: Modifier = Modifier,
  minSize: TextUnit = size,
  weight: FontWeight = FontWeight.Bold,
  fontFamily: FontFamily = UiFont,
  letterSpacingEm: Float = 0f,
  align: TextAlign = TextAlign.Center,
  maxLines: Int = 1,
) {
  BasicText(
    text = text,
    modifier = modifier,
    style = TextStyle(
      color = color,
      fontSize = size,
      fontWeight = weight,
      fontFamily = fontFamily,
      letterSpacing = letterSpacingEm.em,
      textAlign = align,
      lineHeight = size * 1.2f,
    ),
    maxLines = maxLines,
    softWrap = maxLines > 1,
    overflow = TextOverflow.Ellipsis,
    autoSize = if (minSize < size) TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = size, stepSize = 0.25.sp) else null,
  )
}
