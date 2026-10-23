package voice.core.companion.look

import voice.core.companion.StoredPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Picks a book's colors from its cover pixels (0xAARRGGBB, rows of [width]), so the player reads as one surface with
 * the cover: the background is the color around the cover's edges (its bottom edge counts most, since that is where
 * the cover fades into the page), the text is the cover color that reads best on it, and the highlight is the most
 * vivid other cover color that stands out from it, with a second one of another hue as the accent.
 */
fun paletteFromPixels(pixels: IntArray, width: Int): StoredPalette {
  val colors = colorShares(pixels.indices.map { it to 1 }, pixels)
  if (colors.isEmpty() || width <= 0) return DEFAULT
  val background = (colorShares(edgeWeights(pixels.size, width), pixels).maxByOrNull { it.second } ?: colors.maxBy { it.second }).first
  val content = textColor(colors, background)
  val highlight = vividColor(colors, background, unlike = null) ?: content
  val accent = vividColor(colors, background, unlike = highlight) ?: content
  return StoredPalette(
    background = background,
    content = content,
    highlight = highlight,
    onHighlight = readableOn(highlight, listOf(background, content)),
    accent = accent,
    onAccent = readableOn(accent, listOf(background, content)),
    shadow = mix(background, INK, 0.3),
  )
}

/** Each color of the cover with its share of the weighted pixels, from coarse buckets so noise doesn't split them. */
private fun colorShares(weights: List<Pair<Int, Int>>, pixels: IntArray): List<Pair<Int, Double>> {
  val buckets = HashMap<Int, Bucket>()
  weights.forEach { (index, weight) ->
    val pixel = pixels[index]
    if ((pixel ushr 24) < OPAQUE) return@forEach
    val key = ((pixel shr 20) and 0xF shl 8) or ((pixel shr 12) and 0xF shl 4) or ((pixel shr 4) and 0xF)
    buckets.getOrPut(key) { Bucket() }.add(pixel, weight)
  }
  val total = buckets.values.sumOf { it.count }.toDouble()
  return buckets.values.filter { it.count > 0 }.map { it.average() to it.count / total }
}

/** The pixels along the cover's border, with the bottom rows weighted three times as much as the other edges. */
private fun edgeWeights(size: Int, width: Int): List<Pair<Int, Int>> {
  val height = size / width
  val band = max(1, width / 12)
  return (0 until size).mapNotNull { index ->
    val x = index % width
    val y = index / width
    when {
      y >= height - band -> index to 3
      y < band || x < band || x >= width - band -> index to 1
      else -> null
    }
  }
}

private fun textColor(colors: List<Pair<Int, Double>>, background: Int): Int {
  val best = colors.filter { it.second > 0.01 }.maxByOrNull { contrast(it.first, background) }?.first
  if (best != null && contrast(best, background) >= 4.5) return best
  val tinted = mix(background, if (lightness(background) > 0.5) INK else WHITE, 0.88)
  return if (contrast(tinted, background) >= 4.5) tinted else readableOn(background, emptyList())
}

/**
 * The most vivid cover color that stands out from [background] and differs in hue from [unlike]. A near-white,
 * near-black or grey background has no hue worth avoiding.
 */
private fun vividColor(colors: List<Pair<Int, Double>>, background: Int, unlike: Int?): Int? {
  val backgroundHasHue = saturation(background) >= 0.15 && lightness(background) in 0.12..0.85
  return colors
    .filter { (color, share) ->
      share > 0.004 && saturation(color) > 0.3 && lightness(color) in 0.2..0.85 && contrast(color, background) >= 1.6 &&
        (!backgroundHasHue || hueDistance(color, background) >= 30.0) && (unlike == null || hueDistance(color, unlike) >= 30.0)
    }
    .maxByOrNull { (color, share) -> saturation(color) * share.pow(0.3) * min(contrast(color, background), 2.5) }
    ?.first
}

/** The first of [preferred] that reads on [color], else white or ink, whichever reads better. */
private fun readableOn(color: Int, preferred: List<Int>): Int =
  preferred.firstOrNull { contrast(it, color) >= 4.5 } ?: listOf(WHITE, INK).maxBy { contrast(it, color) }

private class Bucket {
  var count = 0
  var r = 0L
  var g = 0L
  var b = 0L

  fun add(pixel: Int, weight: Int) {
    count += weight
    r += ((pixel shr 16) and 0xFF) * weight
    g += ((pixel shr 8) and 0xFF) * weight
    b += (pixel and 0xFF) * weight
  }

  fun average(): Int = rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
}

private const val OPAQUE = 0x80
private const val WHITE = 0xFFFFFFFF.toInt()
private const val INK = 0xFF17171C.toInt()

private const val NAVY = 0xFF0B0D45.toInt()

private val DEFAULT = StoredPalette(
  background = 0xFFE4E5FC.toInt(),
  content = NAVY,
  highlight = 0xFF3238C2.toInt(),
  onHighlight = 0xFFF4F5FF.toInt(),
  accent = NAVY,
  onAccent = 0xFFF4F5FF.toInt(),
  shadow = 0xFFABAEEC.toInt(),
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

internal fun hue(color: Int): Double {
  val (r, g, b) = channels(color)
  val hi = max(r, max(g, b))
  val lo = min(r, min(g, b))
  if (hi == lo) return 0.0
  val d = (hi - lo).toDouble()
  val h = when (hi) {
    r -> ((g - b) / d).mod(6.0)
    g -> (b - r) / d + 2
    else -> (r - g) / d + 4
  }
  return h * 60
}

private fun hueDistance(a: Int, b: Int): Double = abs(hue(a) - hue(b)).let { min(it, 360 - it) }

internal fun mix(from: Int, to: Int, amount: Double): Int {
  val (r1, g1, b1) = channels(from)
  val (r2, g2, b2) = channels(to)
  fun blend(a: Int, b: Int) = (a + (b - a) * amount).toInt().coerceIn(0, 255)
  return rgb(blend(r1, r2), blend(g1, g2), blend(b1, b2))
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
