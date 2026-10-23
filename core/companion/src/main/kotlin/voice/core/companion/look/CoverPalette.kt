package voice.core.companion.look

import voice.core.companion.StoredPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Picks a book's colors from its cover pixels (0xAARRGGBB): a light paper background tinted by the cover, a dark ink
 * for text, and the cover's most vivid color as the highlight. Backgrounds always stay light, however dark the cover.
 */
fun paletteFromPixels(pixels: IntArray): StoredPalette {
  val buckets = HashMap<Int, Bucket>()
  pixels.forEach { pixel ->
    if ((pixel ushr 24) < OPAQUE) return@forEach
    val key = ((pixel shr 20) and 0xF shl 8) or ((pixel shr 12) and 0xF shl 4) or ((pixel shr 4) and 0xF)
    buckets.getOrPut(key) { Bucket() }.add(pixel)
  }
  val colors = buckets.values.filter { it.count > 0 }.map { it.average() to it.count }
  if (colors.isEmpty()) return DEFAULT
  val total = colors.sumOf { it.second }.toDouble()

  val vivid = colors
    .filter { (color, count) -> saturation(color) > 0.35 && lightness(color) in 0.25..0.75 && count / total > 0.004 }
    .maxByOrNull { (color, count) -> saturation(color) * (count / total).pow(0.3) }
    ?.first
  val dominant = colors.maxBy { it.second }.first
  val darkest = colors.filter { it.second / total > 0.01 }.minByOrNull { lightness(it.first) }?.first ?: INK

  val highlight = vivid ?: if (saturation(dominant) > 0.2) clampLightness(dominant, 0.3, 0.6) else DEFAULT.highlight
  val background = mix(dominant, WHITE, if (lightness(dominant) > 0.8) 0.35 else 0.82)
  val content = if (lightness(darkest) < 0.2) darkest else mix(darkest, INK, 0.7)
  val onHighlight = if (contrast(highlight, WHITE) >= 3.0) WHITE else content
  return StoredPalette(
    background = background,
    content = content,
    highlight = highlight,
    onHighlight = onHighlight,
    accent = content,
    onAccent = background,
    shadow = mix(background, content, 0.18),
  )
}

private class Bucket {
  var count = 0
  var r = 0L
  var g = 0L
  var b = 0L

  fun add(pixel: Int) {
    count++
    r += (pixel shr 16) and 0xFF
    g += (pixel shr 8) and 0xFF
    b += pixel and 0xFF
  }

  fun average(): Int = rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
}

private const val OPAQUE = 0x80
private const val WHITE = 0xFFFFFFFF.toInt()
private const val INK = 0xFF17171C.toInt()

private val DEFAULT = StoredPalette(
  background = 0xFFEDE4CA.toInt(),
  content = INK,
  highlight = 0xFFD62828.toInt(),
  onHighlight = 0xFFFFF4EE.toInt(),
  accent = INK,
  onAccent = 0xFFFFF4EE.toInt(),
  shadow = 0xFFC2AB98.toInt(),
)

private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

private fun channels(color: Int) = Triple((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)

internal fun lightness(color: Int): Double {
  val (r, g, b) = channels(color)
  return (max(r, max(g, b)) + min(r, min(g, b))) / 510.0
}

internal fun saturation(color: Int): Double {
  val (r, g, b) = channels(color)
  val hi = max(r, max(g, b)) / 255.0
  val lo = min(r, min(g, b)) / 255.0
  val l = (hi + lo) / 2
  if (hi == lo) return 0.0
  return (hi - lo) / (1 - abs(2 * l - 1))
}

internal fun mix(from: Int, to: Int, amount: Double): Int {
  val (r1, g1, b1) = channels(from)
  val (r2, g2, b2) = channels(to)
  fun blend(a: Int, b: Int) = (a + (b - a) * amount).toInt().coerceIn(0, 255)
  return rgb(blend(r1, r2), blend(g1, g2), blend(b1, b2))
}

private fun clampLightness(color: Int, low: Double, high: Double): Int = when {
  lightness(color) < low -> mix(color, WHITE, low - lightness(color))
  lightness(color) > high -> mix(color, INK, lightness(color) - high)
  else -> color
}

private fun luminance(color: Int): Double {
  val (r, g, b) = channels(color)
  fun linear(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

internal fun contrast(a: Int, b: Int): Double {
  val la = luminance(a)
  val lb = luminance(b)
  return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
