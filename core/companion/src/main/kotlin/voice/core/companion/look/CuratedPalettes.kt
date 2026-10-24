package voice.core.companion.look

import voice.core.companion.StoredPalette
import kotlin.math.cbrt
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** A palette the listener picked by eye, with every color pair already checked for readability. */
internal data class CuratedPalette(
  val name: String,
  val palette: StoredPalette,
)

/**
 * The listener's shortlisted palettes. A book never shows raw cover colors: they snap to the closest of these, and a
 * drawn cover is painted in one of them, so every player stays readable and in the same family.
 */
internal val CURATED_PALETTES = listOf(
  curated("Coral & Cream", background = 0xFC6C44, content = 0x0B0809, highlight = 0xF4F1C2, onHighlight = 0x0B0809, accent = 0x2E2427, onAccent = 0xFC6C44),
  curated("Cream & Amber", background = 0xFDECDB, content = 0x2E3F3D, highlight = 0xDE8945, onHighlight = 0x17171C, accent = 0x3F5A58, onAccent = 0xFDECDB),
  curated("Mustard & Navy", background = 0xE2B13C, content = 0x1E1B2E, highlight = 0x1E3A6E, onHighlight = 0xE2B13C, accent = 0x8C2F1F, onAccent = 0xFFFFFF),
  curated("Terracotta & Sand", background = 0x8A3A22, content = 0xFFF7EE, highlight = 0xF2D49B, onHighlight = 0x8A3A22, accent = 0xE58A5C, onAccent = 0x17171C),
  curated("Forest & Gold", background = 0x2F4B3A, content = 0xF5ECD6, highlight = 0xE3B34A, onHighlight = 0x2F4B3A, accent = 0xD97B5A, onAccent = 0x17171C),
  curated("Navy & Tangerine", background = 0x1F2F55, content = 0xF7EEDD, highlight = 0xF08A3C, onHighlight = 0x1F2F55, accent = 0x8DB6D9, onAccent = 0x1F2F55),
  curated("Peach & Teal", background = 0xF7C9A8, content = 0x2A2523, highlight = 0x1F6F6B, onHighlight = 0xFFFFFF, accent = 0xB84A33, onAccent = 0xFFFFFF),
  curated("Oxblood & Pink", background = 0x6E1F2C, content = 0xFBE6E0, highlight = 0xF2A0B0, onHighlight = 0x6E1F2C, accent = 0xE8C27A, onAccent = 0x6E1F2C),
  curated("Deep Teal & Salmon", background = 0x21414A, content = 0xF4ECDF, highlight = 0xF28C76, onHighlight = 0x21414A, accent = 0xE6C46A, onAccent = 0x21414A),
  curated("Paper & Cobalt", background = 0xF2EEE3, content = 0x1C1C24, highlight = 0x2D4FBF, onHighlight = 0xF2EEE3, accent = 0xBF3F2B, onAccent = 0xF2EEE3),
  curated("Black & Tangerine", background = 0x0E0E0E, content = 0xF2EFEA, highlight = 0xF27A1E, onHighlight = 0x0E0E0E, accent = 0xD9D6D0, onAccent = 0x0E0E0E),
  curated("Khaki & Coral", background = 0x4F402C, content = 0xF4EADB, highlight = 0xEE6A4C, onHighlight = 0x17171C, accent = 0xD9C9A8, onAccent = 0x4F402C),
  curated("Brick & Butter", background = 0xAA4643, content = 0xFFF1E6, highlight = 0xF2C46E, onHighlight = 0x17171C, accent = 0x243D49, onAccent = 0xFFF1E6),
  curated("Tan & Oxblood", background = 0xC49A6C, content = 0x140F0C, highlight = 0x801712, onHighlight = 0xFFFFFF, accent = 0x140F0C, onAccent = 0xC49A6C),
  curated("Apricot & Ink", background = 0xE0A15E, content = 0x0B0909, highlight = 0x0B0909, onHighlight = 0xE0A15E, accent = 0xB5453F, onAccent = 0xFFFFFF),
  curated("White & Saffron", background = 0xFAFAF7, content = 0x120E0E, highlight = 0x120E0E, onHighlight = 0xFAFAF7, accent = 0xF2B233, onAccent = 0x120E0E),
  curated("Sea Glass & Peach", background = 0x638C89, content = 0x0E1615, highlight = 0xFFE4CE, onHighlight = 0x0E1615, accent = 0x1E2B3A, onAccent = 0xFFFFFF),
  curated("Straw & Leaf", background = 0xE8E09A, content = 0x1A1A12, highlight = 0x3E6B35, onHighlight = 0xE8E09A, accent = 0xB8541E, onAccent = 0xFFFFFF),
  curated("Dusty Rose & Navy", background = 0xD9837A, content = 0x120E10, highlight = 0x1B2848, onHighlight = 0xD9837A, accent = 0xFFF0E6, onAccent = 0x120E10),
)

internal fun curatedPalette(name: String): CuratedPalette? = CURATED_PALETTES.firstOrNull { it.name == name }

/**
 * The curated palette that looks most like [colors] picked from a cover. The background weighs double, since it is
 * the surface the cover and the player share; the highlight and accent count whichever way round matches better.
 */
internal fun closestCuratedPalette(colors: StoredPalette): CuratedPalette = CURATED_PALETTES.minBy { curated ->
  val target = curated.palette
  val straight = colorDistance(colors.highlight, target.highlight) + colorDistance(colors.accent, target.accent)
  val swapped = colorDistance(colors.highlight, target.accent) + colorDistance(colors.accent, target.highlight)
  2 * colorDistance(colors.background, target.background) + min(straight, swapped) / 2
}

/** How different two colors look, as the distance between them in CIELAB. */
internal fun colorDistance(a: Int, b: Int): Double {
  val (l1, a1, b1) = lab(a)
  val (l2, a2, b2) = lab(b)
  return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
}

private fun lab(color: Int): Triple<Double, Double, Double> {
  fun linear(channel: Int): Double = (channel / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
  val r = linear((color shr 16) and 0xFF)
  val g = linear((color shr 8) and 0xFF)
  val b = linear(color and 0xFF)
  fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
  val x = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
  val y = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
  val z = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
  return Triple(116 * y - 16, 500 * (x - y), 200 * (y - z))
}

private fun curated(name: String, background: Int, content: Int, highlight: Int, onHighlight: Int, accent: Int, onAccent: Int): CuratedPalette {
  fun opaque(rgb: Int) = rgb or (0xFF shl 24)
  return CuratedPalette(
    name = name,
    palette = StoredPalette(
      background = opaque(background),
      content = opaque(content),
      highlight = opaque(highlight),
      onHighlight = opaque(onHighlight),
      accent = opaque(accent),
      onAccent = opaque(onAccent),
      shadow = mix(opaque(background), opaque(0x17171C), 0.3),
    ),
  )
}
