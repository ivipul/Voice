package voice.features.playbackScreen.view.holocard

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import voice.core.logging.api.Logger

internal val RainbowColors = listOf(
  Color(0xFFFF4FA8),
  Color(0xFFFFD24A),
  Color(0xFF4DFFB0),
  Color(0xFF45C8FF),
  Color(0xFFA77BFF),
)

/** Where the shimmer band and the glare sit follows the light, which moves with the tilt. */
private const val FoilShaderSource = """
uniform float2 size;
uniform float2 light;
uniform float strength;

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

half4 main(float2 fragCoord) {
  float2 uv = fragCoord / size;
  float diagonal = uv.x * 0.9 + uv.y * 0.5;
  float center = 0.15 + light.x * 0.8 + light.y * 0.2;
  float band = 1.0 - smoothstep(0.0, 0.3, abs(diagonal - center));
  band = band * band;
  float hue = diagonal * 1.7 - light.x * 1.3;
  float3 rainbow = 0.5 + 0.5 * cos(6.2831 * (hue + float3(0.0, 0.33, 0.67)));
  float amount = 0.1 + strength * 0.35;
  float3 color = rainbow * band * amount;

  float cellSize = size.x * 0.022;
  float2 cellPos = fragCoord / cellSize;
  float2 cell = floor(cellPos);
  float n = hash(cell);
  float2 jitter = float2(hash(cell + 7.1), hash(cell + 13.7)) - 0.5;
  float dotRadius = length(fract(cellPos) - 0.5 - jitter * 0.5);
  float twinkle = 0.5 + 0.5 * sin(n * 60.0 + light.x * 14.0 + light.y * 9.0);
  float sparkle = step(0.9, n) * (1.0 - smoothstep(0.0, 0.14, dotRadius)) * twinkle;
  color += float3(sparkle * (0.12 + band * 0.7 * (0.3 + strength)));

  float glare = 1.0 - smoothstep(0.0, 0.7, distance(uv, light));
  color += float3(glare * 0.06);
  return half4(half3(color), 1.0);
}
"""

/**
 * Foil shimmer over the card that follows the tilt: an AGSL shader on API 33+, a plain gradient sheen below.
 * [mirrored] flips the light for the back face, which is seen from the other side.
 */
internal fun Modifier.holoFoil(
  tilt: HoloTiltState,
  mirrored: Boolean = false,
): Modifier = drawWithCache {
  val shader = if (Build.VERSION.SDK_INT >= 33) createFoilShader() else null
  val brush = shader?.let(::ShaderBrush)
  onDrawWithContent {
    drawContent()
    val lightX = if (mirrored) 1f - tilt.pointerX else tilt.pointerX
    if (shader != null && brush != null) {
      shader.setFloatUniform("size", size.width, size.height)
      shader.setFloatUniform("light", lightX, tilt.pointerY)
      shader.setFloatUniform("strength", tilt.strength)
      drawRect(brush = brush, blendMode = BlendMode.Plus)
    } else {
      drawSheen(lightX = lightX, lightY = tilt.pointerY, strength = tilt.strength)
    }
  }
}

@RequiresApi(33)
private fun createFoilShader(): RuntimeShader? = try {
  RuntimeShader(FoilShaderSource)
} catch (e: Exception) {
  Logger.w(e, "Could not compile the holo foil shader, falling back to a gradient sheen")
  null
}

private fun ContentDrawScope.drawSheen(
  lightX: Float,
  lightY: Float,
  strength: Float,
) {
  val startX = -size.width + lightX * size.width * 1.8f
  val sheen = Brush.linearGradient(
    colors = listOf(Color.Transparent) + RainbowColors.map { it.copy(alpha = 0.5f) } + Color.Transparent,
    start = Offset(startX, 0f),
    end = Offset(startX + size.width * 1.3f, size.height),
  )
  drawRect(brush = sheen, alpha = 0.2f + strength * 0.5f, blendMode = BlendMode.Plus)
  val glare = Brush.radialGradient(
    colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
    center = Offset(lightX * size.width, lightY * size.height),
    radius = size.width * 0.7f,
  )
  drawRect(brush = glare, alpha = 0.5f, blendMode = BlendMode.Plus)
}
