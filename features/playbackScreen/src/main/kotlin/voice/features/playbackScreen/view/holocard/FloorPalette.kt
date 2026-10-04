package voice.features.playbackScreen.view.holocard

import androidx.compose.ui.graphics.Color

internal enum class BackdropPattern { HexLattice, Dots, Stripes, Rings }

internal data class FloorPalette(
  val backdropTop: Color,
  val backdropBottom: Color,
  val accent: Color,
  val accentDark: Color,
  val pattern: BackdropPattern,
)

private fun palette(
  backdropTop: Long,
  backdropBottom: Long,
  accent: Long,
  accentDark: Long,
  pattern: BackdropPattern,
) = FloorPalette(Color(backdropTop), Color(backdropBottom), Color(accent), Color(accentDark), pattern)

private val palettes = listOf(
  palette(0xFF0D5A5A, 0xFF072A33, 0xFF2FE0C0, 0xFF0F7A73, BackdropPattern.HexLattice),
  palette(0xFF6A2A52, 0xFF2A0F26, 0xFFFF6FB5, 0xFF8A2F66, BackdropPattern.Dots),
  palette(0xFF6B4A10, 0xFF2A1A06, 0xFFFFC247, 0xFF8A5A12, BackdropPattern.Stripes),
  palette(0xFF1C3F7A, 0xFF0B1730, 0xFF5EA4FF, 0xFF2A4F9A, BackdropPattern.Rings),
  palette(0xFF4A2A7A, 0xFF1A0F2E, 0xFFB48BFF, 0xFF5B3B9A, BackdropPattern.HexLattice),
  palette(0xFF3D6A1C, 0xFF15260A, 0xFF9BE84D, 0xFF4A8A22, BackdropPattern.Dots),
  palette(0xFF7A1C2A, 0xFF2A0A10, 0xFFFF5A6E, 0xFF9A2A3A, BackdropPattern.Stripes),
  palette(0xFF1C6A7A, 0xFF0A2A33, 0xFF6EE7FF, 0xFF2A8A9A, BackdropPattern.Rings),
  palette(0xFF7A3A10, 0xFF2A1406, 0xFFFF9A47, 0xFF9A4A12, BackdropPattern.HexLattice),
  palette(0xFF4A4A2A, 0xFF1A1A0E, 0xFFE8D77A, 0xFF8A8A3A, BackdropPattern.Dots),
)

/**
 * Floors cycle through the palettes. A card with no floor (a monster, a place) gets a palette from [seed] so
 * different entities do not all look alike.
 */
internal fun floorPalette(
  floor: Int?,
  seed: String,
): FloorPalette {
  if (floor == null || floor < 1) return palettes[Math.floorMod(seed.hashCode(), palettes.size)]
  return palettes[(floor - 1) % palettes.size]
}
