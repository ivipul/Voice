package voice.features.playbackScreen.view.holocard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import voice.core.logging.api.Logger

/**
 * The look-to-look morph of the Crawl explorer (web/crawl/js/gl.js), ported to AGSL: the loader's holo foil
 * dissolves into the image through a noise threshold, both layers are nudged by the noise, and a glowing,
 * rainbow-tinted front runs along the dissolve edge. `reveal` goes 0..1; [time] animates the foil.
 */
private const val RevealShaderSource = """
uniform shader image;
uniform float2 size;
uniform float time;
uniform float reveal;
uniform float hasImage;
$FoilShaderFunctions
float hash21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + float2(1.0, 0.0));
  float c = hash21(i + float2(0.0, 1.0));
  float d = hash21(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 5; i++) {
    v += a * vnoise(p);
    p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03 + float2(1.7, 9.2);
    a *= 0.5;
  }
  return v;
}

float3 loader(float2 fragCoord) {
  float2 uv = fragCoord / size;
  float2 light = float2(0.5 + 0.5 * sin(time * 1.3), 0.5 + 0.5 * sin(time * 0.9 + 1.0));
  float strength = 0.55 + 0.45 * sin(time * 5.2);
  float diagonal = uv.x * 0.9 + uv.y * 0.5;
  float3 wash = 0.5 + 0.5 * cos(6.2831 * (diagonal * 1.1 - time * 0.2 + float3(0.0, 0.33, 0.67)));
  return float3(0.06, 0.06, 0.09) + wash * (0.14 + 0.12 * strength) + foilColor(fragCoord, size, light, strength) * 1.6;
}

half4 main(float2 fragCoord) {
  if (hasImage < 0.5) {
    return half4(half3(loader(fragCoord)), 1.0);
  }
  float2 uv = fragCoord / size;
  float t = reveal;
  float env = sin(3.14159 * t);
  float w = 0.2;
  float tp = t * (1.0 + 2.0 * w) - w;
  float str = 0.16 * env;
  float2 n = float2(fbm(uv * 3.0 + time * 0.2), fbm(uv * 3.0 + float2(17.3, 5.1) - time * 0.17)) - 0.5;
  float2 lift = float2(0.0, 0.07 * env);
  float2 posA = (uv + n * str * t * 1.4 + lift * t) * size;
  float2 posB = clamp((uv - n * str * (1.0 - t) * 1.4) * size, float2(0.0), size - 1.0);
  float3 a = loader(posA);
  half4 b = image.eval(posB);
  float th = fbm(uv * 5.0 + float2(3.1, 8.7));
  float k = 1.0 - smoothstep(tp - w, tp + w, th);
  float3 c = mix(a, float3(b.rgb), k);
  float front = pow(clamp(1.0 - abs(th - tp) / w, 0.0, 1.0), 1.6) * env;
  float3 accent = 0.5 + 0.5 * cos(6.2831 * (th * 1.7 + time * 0.25 + float3(0.0, 0.33, 0.67)));
  c = mix(c, accent, front * 0.55) + accent * front * 0.25;
  return half4(half3(c), 1.0);
}
"""

private val EmptyBitmap: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

/**
 * Draws the pulsing holo foil while [bitmap] is null, then the ink-bleed reveal into it as [reveal] goes 0..1.
 * An AGSL shader on API 33+; below that, a plain foil gradient that crossfades to the image.
 */
internal fun Modifier.holoReveal(
  bitmap: Bitmap?,
  reveal: () -> Float,
  time: () -> Float,
): Modifier = drawWithCache {
  val shader = if (Build.VERSION.SDK_INT >= 33) createRevealShader(bitmap, size) else null
  val brush = shader?.let(::ShaderBrush)
  val image = bitmap?.asImageBitmap()
  onDrawBehind {
    if (shader != null && brush != null) {
      shader.setFloatUniform("size", size.width, size.height)
      shader.setFloatUniform("time", time())
      shader.setFloatUniform("reveal", reveal())
      shader.setFloatUniform("hasImage", if (bitmap != null) 1f else 0f)
      drawRect(brush = brush)
    } else {
      drawFoilGradient(time())
      if (image != null) {
        drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = reveal())
      }
    }
  }
}

@RequiresApi(33)
private fun createRevealShader(bitmap: Bitmap?, size: Size): RuntimeShader? = try {
  RuntimeShader(RevealShaderSource).apply {
    val source = bitmap ?: EmptyBitmap
    val bitmapShader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
      setLocalMatrix(
        Matrix().apply { setScale(size.width / source.width, size.height / source.height) },
      )
    }
    setInputShader("image", bitmapShader)
  }
} catch (e: Exception) {
  Logger.w(e, "Could not compile the holo reveal shader, falling back to a crossfade")
  null
}

private fun DrawScope.drawFoilGradient(time: Float) {
  drawRect(Color(0xFF0D0D12))
  val sweep = (time / 2.4f) % 1f
  val pulse = 0.55f + 0.4f * kotlin.math.sin(time * 5.2f)
  drawRect(
    brush = Brush.linearGradient(
      colors = RainbowColors + RainbowColors.first(),
      start = Offset(size.width * (sweep - 1f), 0f),
      end = Offset(size.width * sweep, size.height * 0.6f),
      tileMode = TileMode.Repeated,
    ),
    alpha = pulse * 0.6f,
  )
}
