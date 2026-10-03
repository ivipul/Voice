package voice.features.playbackScreen.view.holocard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import voice.core.strings.R
import voice.core.xray.card.ComposedPlayerCard
import voice.core.xray.card.PlayerCardSlots

private val FoilColors = listOf(
  Color(0xFFFF4FA8),
  Color(0xFFFFD24A),
  Color(0xFF4DFFB0),
  Color(0xFF45C8FF),
  Color(0xFFA77BFF),
  Color(0xFFFF4FA8),
)

private const val MaxAbilities = 3

@Composable
internal fun HoloCardBack(
  card: ComposedPlayerCard,
  description: String,
  plain: Boolean,
  tilt: HoloTiltState,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  val palette = remember(card.floor) { floorPalette(card.floor) }
  Box(
    modifier = modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(u.dp(5f)))
      .drawBehind { drawBackBackground(palette, u) }
      .holoFoil(tilt, mirrored = true),
  ) {
    Emblem(tilt = tilt, palette = palette, u = u)
    if (plain) {
      Heading(text = stringResource(R.string.holo_card_about), top = 38f, palette = palette, u = u)
      CardText(
        text = description,
        size = u.sp(3.4f),
        color = InkColor,
        weight = FontWeight.Normal,
        align = TextAlign.Start,
        maxLines = 12,
        modifier = Modifier.cardRect(u, 7f, 44.5f, 86f, 62f),
      )
    } else {
      Heading(text = stringResource(R.string.holo_card_abilities), top = 38f, palette = palette, u = u)
      Abilities(abilities = card.abilities.take(MaxAbilities), palette = palette, u = u)
      Heading(text = stringResource(R.string.holo_card_loadout), top = 72f, palette = palette, u = u)
      Loadout(loadout = card.loadout, palette = palette, u = u)
    }
    Callout(description = card.look?.description, u = u, modifier = Modifier.align(Alignment.BottomCenter))
  }
}

private fun DrawScope.drawBackBackground(
  palette: FloorPalette,
  u: CardUnit,
) {
  drawRect(
    brush = cssLinearGradient(
      170f,
      size,
      0f to Color(0xFF252A30),
      0.55f to Color(0xFF121519),
      1f to Color(0xFF1C2025),
    ),
  )
  val circuit = Color.White.copy(alpha = 0.04f)
  var y = u.px(9f)
  while (y < size.height) {
    drawLine(circuit, Offset(0f, y), Offset(size.width, y), u.px(0.4f))
    y += u.px(9f)
  }
  drawLine(palette.accent.copy(alpha = 0.17f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), u.px(1f))
  drawRoundRect(
    color = mix(palette.accent, Color(0xFF6B1A2A), 0.55f),
    topLeft = Offset(u.px(2.4f), u.px(2.4f)),
    size = Size(size.width - u.px(4.8f), size.height - u.px(4.8f)),
    cornerRadius = CornerRadius(u.px(3.6f)),
    style = Stroke(width = u.px(0.6f)),
  )
}

@Composable
private fun Emblem(
  tilt: HoloTiltState,
  palette: FloorPalette,
  u: CardUnit,
) {
  Canvas(modifier = Modifier.cardRect(u, 35f, 5.5f, 30f, 30f)) {
    val center = Offset(size.width / 2f, size.height / 2f)
    rotate(degrees = (1f - tilt.pointerX) * 360f, pivot = center) {
      drawCircle(brush = Brush.sweepGradient(FoilColors, center), radius = size.minDimension / 2f)
    }
    drawCircle(Color(0xFF0B1116), radius = size.minDimension / 2f - u.px(1.6f))
    drawCircle(
      brush = Brush.radialGradient(listOf(palette.accent.copy(alpha = 0.18f), Color.Transparent), center = center),
      radius = size.minDimension / 2f - u.px(1.6f),
    )
    val icon = u.px(17f)
    val iconTop = Offset(center.x - icon / 2f, center.y - icon / 2f)
    val gradient = Brush.linearGradient(
      listOf(Color(0xFF9FF3FF), Color(0xFFFFB3E6), Color(0xFFD6FFA8)),
      start = iconTop,
      end = iconTop + Offset(icon, icon),
    )
    val hexagon = hexagonPath(icon, icon, inset = icon * 0.1f)
    translate(iconTop.x, iconTop.y) { drawPath(hexagon, gradient, style = Stroke(width = icon * 0.05f)) }
    val crown = Path().apply {
      moveTo(icon * 0.3f, icon * 0.62f)
      lineTo(icon * 0.4f, icon * 0.34f)
      lineTo(icon * 0.5f, icon * 0.52f)
      lineTo(icon * 0.6f, icon * 0.34f)
      lineTo(icon * 0.7f, icon * 0.62f)
      close()
    }
    translate(iconTop.x, iconTop.y) { drawPath(crown, gradient) }
  }
}

@Composable
private fun Heading(
  text: String,
  top: Float,
  palette: FloorPalette,
  u: CardUnit,
) {
  CardText(
    text = text.uppercase(),
    size = u.sp(3.1f),
    minSize = u.sp(2f),
    color = palette.accent,
    letterSpacingEm = 0.2f,
    align = TextAlign.Start,
    modifier = Modifier
      .cardRect(u, 7f, top, 86f, 4.5f),
  )
}

@Composable
private fun Abilities(
  abilities: List<ComposedPlayerCard.Ability>,
  palette: FloorPalette,
  u: CardUnit,
) {
  Column(
    modifier = Modifier.cardRect(u, 7f, 44.5f, 86f, 26f),
    verticalArrangement = Arrangement.spacedBy(u.dp(1.6f)),
  ) {
    if (abilities.isEmpty()) {
      CardText(
        text = stringResource(R.string.holo_card_no_abilities),
        size = u.sp(3.2f),
        color = InkDimColor,
        weight = FontWeight.Normal,
        align = TextAlign.Start,
      )
    }
    abilities.forEach { ability ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(2.4f))) {
        Box(
          modifier = Modifier
            .size(u.dp(7f))
            .clip(RoundedCornerShape(u.dp(1.6f)))
            .background(Color(0xFF1B232A))
            .border(u.dp(0.4f), palette.accent.copy(alpha = 0.6f), RoundedCornerShape(u.dp(1.6f))),
          contentAlignment = Alignment.Center,
        ) {
          CardText(
            text = ability.name.take(1).uppercase(),
            size = u.sp(4f),
            color = palette.accent,
            fontFamily = DisplayFont,
            weight = FontWeight.Black,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          CardText(
            text = ability.name,
            size = u.sp(3.5f),
            minSize = u.sp(2.4f),
            color = InkColor,
            align = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
          )
          ability.rank?.let {
            CardText(
              text = stringResource(R.string.holo_card_rank, it),
              size = u.sp(2.9f),
              color = InkDimColor,
              weight = FontWeight.Normal,
              align = TextAlign.Start,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun slotLabel(slot: String): String = stringResource(
  when (slot) {
    PlayerCardSlots.HEAD -> R.string.holo_card_slot_head
    PlayerCardSlots.NECK -> R.string.holo_card_slot_neck
    PlayerCardSlots.CHEST -> R.string.holo_card_slot_chest
    PlayerCardSlots.HANDS -> R.string.holo_card_slot_hands
    PlayerCardSlots.RING -> R.string.holo_card_slot_ring
    PlayerCardSlots.LEGS -> R.string.holo_card_slot_legs
    PlayerCardSlots.FEET -> R.string.holo_card_slot_feet
    else -> R.string.holo_card_slot_weapon
  },
)

@Composable
private fun Loadout(
  loadout: List<ComposedPlayerCard.GearItem>,
  palette: FloorPalette,
  u: CardUnit,
) {
  val bySlot = remember(loadout) { loadout.associateBy { it.slot.trim().lowercase() } }
  Column(
    modifier = Modifier.cardRect(u, 7f, 78.5f, 86f, 30f),
    verticalArrangement = Arrangement.spacedBy(u.dp(2f)),
  ) {
    PlayerCardSlots.ordered.chunked(4).forEach { rowSlots ->
      Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u.dp(2f))) {
        rowSlots.forEach { slot ->
          Slot(label = slotLabel(slot), item = bySlot[slot]?.name, palette = palette, u = u, modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun Slot(
  label: String,
  item: String?,
  palette: FloorPalette,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(u.dp(1.8f))
  val equipped = item != null
  Column(
    modifier = modifier
      .fillMaxSize()
      .clip(shape)
      .background(if (equipped) mix(palette.accent, Color(0xFF0F1519), 0.14f) else Color(0xFF12181D))
      .border(u.dp(0.4f), if (equipped) palette.accent else Color(0xFF3A444D), shape)
      .padding(u.dp(0.8f)),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CardText(text = label.uppercase(), size = u.sp(2f), minSize = u.sp(1.4f), color = InkDimColor, letterSpacingEm = 0.1f)
    CardText(
      text = item ?: "–",
      size = u.sp(2.4f),
      minSize = u.sp(1.5f),
      color = if (equipped) InkColor else InkDimColor,
      weight = FontWeight.Medium,
      maxLines = 3,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun Callout(
  description: String?,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(u.dp(2f))
  Column(
    modifier = modifier
      .padding(start = u.dp(6f), end = u.dp(6f), bottom = u.dp(5f))
      .fillMaxWidth()
      .clip(shape)
      .background(Color(0xFF0E1317))
      .border(u.dp(0.4f), Color(0xFF3A444D), shape)
      .padding(horizontal = u.dp(3f), vertical = u.dp(2.2f)),
  ) {
    CardText(
      text = stringResource(R.string.holo_card_current_look).uppercase(),
      size = u.sp(3.1f),
      color = InkColor,
      letterSpacingEm = 0.1f,
      align = TextAlign.Start,
      modifier = Modifier.fillMaxWidth(),
    )
    if (description != null) {
      CardText(
        text = description,
        size = u.sp(2.7f),
        color = InkDimColor,
        weight = FontWeight.Normal,
        align = TextAlign.Start,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
