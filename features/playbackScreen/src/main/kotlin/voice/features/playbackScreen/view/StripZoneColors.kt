package voice.features.playbackScreen.view

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** A strip zone's fill: a soft tint of one hue, from [light] to a slightly deeper [deep]. */
internal data class StripZoneColors(
  val light: Color,
  val deep: Color,
)

// Peach, mint, lavender, sky, butter, rose: ordered so neighbouring chapters are far apart in hue.
private val PastelHues = listOf(22f, 150f, 262f, 200f, 48f, 340f)

private const val LightLightness = 0.85f
private const val DeepLightness = 0.72f
private const val PastelSaturation = 0.55f

/** The colours of the strip for [chapter]: the same every time, and different for neighbouring chapters. */
internal fun stripZoneColors(chapter: Int): StripZoneColors {
  val hue = PastelHues[(chapter - 1).mod(PastelHues.size)]
  return StripZoneColors(
    light = hsl(hue, PastelSaturation, LightLightness),
    deep = hsl(hue, PastelSaturation, DeepLightness),
  )
}

private fun hsl(
  hue: Float,
  saturation: Float,
  lightness: Float,
): Color {
  val chroma = (1f - abs(2f * lightness - 1f)) * saturation
  val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
  val match = lightness - chroma / 2f
  val (r, g, b) = when {
    hue < 60f -> Triple(chroma, x, 0f)
    hue < 120f -> Triple(x, chroma, 0f)
    hue < 180f -> Triple(0f, chroma, x)
    hue < 240f -> Triple(0f, x, chroma)
    hue < 300f -> Triple(x, 0f, chroma)
    else -> Triple(chroma, 0f, x)
  }
  return Color(r + match, g + match, b + match)
}
