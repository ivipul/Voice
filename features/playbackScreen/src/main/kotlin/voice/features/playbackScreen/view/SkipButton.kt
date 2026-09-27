package voice.features.playbackScreen.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * A circular "skip N seconds" control (the familiar podcast-app rewind/forward icon): a
 * near-full circle with a gap and an arrowhead showing the direction, and the actual seek
 * amount overlaid in the center rather than a plain static icon.
 */
@Composable
internal fun SkipButton(
  forward: Boolean,
  seconds: Int,
  onClick: () -> Unit,
) {
  val color = LocalContentColor.current
  val contentDescription = stringResource(
    id = if (forward) R.string.playback_action_fast_forward else R.string.playback_action_rewind,
  )
  Box(
    modifier = Modifier
      .semantics { this.contentDescription = contentDescription }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false),
        onClick = onClick,
      )
      .size(48.dp),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        // Only the arc+arrowhead are mirrored for "forward" - the digits below are drawn
        // separately (as a sibling Text, not inside this Canvas) so they never flip.
        .scale(scaleX = if (forward) -1f else 1f, scaleY = 1f),
    ) {
      val strokeWidthPx = 2.dp.toPx()
      val diameter = size.minDimension - strokeWidthPx * 3
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val radius = diameter / 2f
      val center = Offset(size.width / 2f, size.height / 2f)

      // 0 deg = 3 o'clock, positive sweep = clockwise. Gap sits roughly at the top.
      val startAngle = -20f
      val sweepAngle = 280f

      drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = Size(diameter, diameter),
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
      )

      // Arrowhead at the arc's starting point, pointing counter to the sweep direction, so
      // it reads as "this is where the motion originates from".
      val startRad = Math.toRadians(startAngle.toDouble())
      val tip = Offset(
        x = center.x + radius * cos(startRad).toFloat(),
        y = center.y + radius * sin(startRad).toFloat(),
      )
      val tangentRad = Math.toRadians((startAngle - 90).toDouble())
      val perpRad = tangentRad + Math.PI / 2
      val arrowLength = strokeWidthPx * 3.2f
      val arrowHalfWidth = strokeWidthPx * 1.8f
      val baseCenter = Offset(
        x = tip.x - arrowLength * cos(tangentRad).toFloat(),
        y = tip.y - arrowLength * sin(tangentRad).toFloat(),
      )
      val base1 = Offset(
        x = baseCenter.x + arrowHalfWidth * cos(perpRad).toFloat(),
        y = baseCenter.y + arrowHalfWidth * sin(perpRad).toFloat(),
      )
      val base2 = Offset(
        x = baseCenter.x - arrowHalfWidth * cos(perpRad).toFloat(),
        y = baseCenter.y - arrowHalfWidth * sin(perpRad).toFloat(),
      )
      drawPath(
        path = Path().apply {
          moveTo(tip.x, tip.y)
          lineTo(base1.x, base1.y)
          lineTo(base2.x, base2.y)
          close()
        },
        color = color,
      )
    }
    Text(
      text = seconds.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = color,
    )
  }
}
