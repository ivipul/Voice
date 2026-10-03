package voice.features.playbackScreen.view.holocard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import voice.core.strings.R
import voice.core.xray.card.CardStat
import voice.core.xray.card.ComposedPlayerCard
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private val FoilColors = listOf(
  Color(0xFFFF4FA8),
  Color(0xFFFFD24A),
  Color(0xFF4DFFB0),
  Color(0xFF45C8FF),
  Color(0xFFA77BFF),
  Color(0xFFFF4FA8),
)

private const val WindowLeft = 5f
private const val WindowTop = 21f
private const val WindowWidth = 90f
private const val WindowHeight = 78f
private const val PanelTopEdge = 107f
private const val FigureLeft = 14f
private const val FigureTop = 17f
private const val FigureWidth = 72f
private const val FigureHeight = 87f

@Composable
internal fun HoloCardFront(
  name: String,
  description: String,
  plain: Boolean,
  card: ComposedPlayerCard,
  lookImage: String?,
  portrait: String?,
  animate: Boolean,
  tilt: HoloTiltState,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  val palette = remember(card.floor, card.id) { floorPalette(card.floor, card.id) }
  Box(
    modifier = modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(u.dp(5f)))
      .drawBehind { drawFrontBackground(palette, u) }
      .holoFoil(tilt),
  ) {
    val portraitOnly = lookImage == null && portrait != null
    ArtWindow(
      palette = palette,
      showHalo = !portraitOnly,
      tilt = tilt,
      u = u,
      modifier = Modifier.cardRect(u, WindowLeft, WindowTop, WindowWidth, WindowHeight),
    )
    Art(name = name, image = lookImage ?: portrait, portraitOnly = portraitOnly, animate = animate, palette = palette, tilt = tilt, u = u)
    Banner(name = name, epithet = card.epithet, wide = plain, palette = palette, tilt = tilt, u = u)
    if (!plain) {
      Badge(
        label = stringResource(R.string.holo_card_level),
        value = card.level?.toString(),
        caption = null,
        palette = palette,
        u = u,
        modifier = Modifier.cardRect(u, 3.5f, 3.5f, 17f, 17f),
      )
      Badge(
        label = stringResource(R.string.holo_card_floor),
        value = card.floor?.toString(),
        caption = card.floorName,
        palette = palette,
        u = u,
        modifier = Modifier.cardRect(u, 79.5f, 3.5f, 17f, 17f),
      )
    }
    if (plain) {
      AboutPanel(race = card.race, description = description, u = u)
    } else {
      StatPanel(card = card, palette = palette, animate = animate, u = u)
    }
  }
}

private fun DrawScope.drawFrontBackground(
  palette: FloorPalette,
  u: CardUnit,
) {
  val dark = Color(0xFF0A1418)
  drawRect(
    brush = cssLinearGradient(
      160f,
      size,
      0f to mix(palette.accent, dark, 0.4f),
      0.38f to dark,
      1f to mix(palette.accentDark, dark, 0.55f),
    ),
  )
  drawRoundRect(
    color = palette.accent.copy(alpha = 0.65f),
    topLeft = Offset(u.px(2f), u.px(2f)),
    size = Size(size.width - u.px(4f), size.height - u.px(4f)),
    cornerRadius = CornerRadius(u.px(3.6f)),
    style = Stroke(width = u.px(0.5f)),
  )
}

@Composable
private fun ArtWindow(
  palette: FloorPalette,
  showHalo: Boolean,
  tilt: HoloTiltState,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier.clip(RoundedCornerShape(u.dp(3f)))) {
    drawRect(brush = Brush.verticalGradient(listOf(palette.backdropTop, palette.backdropBottom)))
    drawPattern(palette.pattern, u, shiftX = -tilt.offsetX * u.px(7f), shiftY = tilt.offsetY * u.px(7f))
    drawGlow(palette, u, shiftX = -tilt.offsetX * u.px(4f), shiftY = tilt.offsetY * u.px(4f))
    if (showHalo) drawHalo(palette, tilt, u)
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
        startY = size.height - u.px(18f),
        endY = size.height,
      ),
    )
    drawRoundRect(
      color = Color.White.copy(alpha = 0.12f),
      cornerRadius = CornerRadius(u.px(3f)),
      style = Stroke(width = u.px(0.6f)),
    )
  }
}

private fun DrawScope.drawPattern(
  pattern: BackdropPattern,
  u: CardUnit,
  shiftX: Float,
  shiftY: Float,
) {
  val line = Color.White.copy(alpha = 0.05f)
  when (pattern) {
    BackdropPattern.HexLattice -> {
      drawAngledLines(degrees = 60f, spacing = u.px(7f), color = line, strokeWidth = u.px(0.5f), shiftX = shiftX, shiftY = shiftY)
      drawAngledLines(degrees = -60f, spacing = u.px(7f), color = line, strokeWidth = u.px(0.5f), shiftX = shiftX, shiftY = shiftY)
    }
    BackdropPattern.Stripes ->
      drawAngledLines(degrees = 45f, spacing = u.px(5f), color = line, strokeWidth = u.px(1.2f), shiftX = shiftX, shiftY = shiftY)
    BackdropPattern.Dots -> {
      val spacing = u.px(6f)
      val dot = Color.White.copy(alpha = 0.12f)
      val columns = ceil(size.width / spacing).toInt() + 2
      val rows = ceil(size.height / spacing).toInt() + 2
      val offsetX = shiftX.mod(spacing)
      val offsetY = shiftY.mod(spacing)
      for (column in -1..columns) {
        for (row in -1..rows) {
          drawCircle(dot, radius = u.px(0.5f), center = Offset(column * spacing + offsetX, row * spacing + offsetY))
        }
      }
    }
    BackdropPattern.Rings -> {
      val center = Offset(size.width / 2f + shiftX, size.height * 0.46f + shiftY)
      val step = u.px(8f)
      val rings = ceil(size.height / step).toInt() + 3
      for (ring in 1..rings) {
        drawCircle(Color.White.copy(alpha = 0.07f), radius = ring * step, center = center, style = Stroke(width = u.px(0.5f)))
      }
    }
  }
}

private fun DrawScope.drawAngledLines(
  degrees: Float,
  spacing: Float,
  color: Color,
  strokeWidth: Float,
  shiftX: Float,
  shiftY: Float,
) {
  val radians = degrees * PI.toFloat() / 180f
  val direction = Offset(cos(radians), sin(radians))
  val normal = Offset(-direction.y, direction.x)
  val reach = size.width + size.height
  val origin = Offset(size.width / 2f + shiftX, size.height / 2f + shiftY)
  val count = ceil(reach / spacing).toInt()
  for (index in -count..count) {
    val point = origin + normal * (index * spacing)
    drawLine(color, point - direction * reach, point + direction * reach, strokeWidth)
  }
}

private fun DrawScope.drawGlow(
  palette: FloorPalette,
  u: CardUnit,
  shiftX: Float,
  shiftY: Float,
) {
  val center = Offset(size.width / 2f + shiftX, size.height * 0.62f + shiftY)
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(palette.accent.copy(alpha = 0.55f), Color.Transparent),
      center = center,
      radius = u.px(40f),
    ),
    radius = u.px(40f),
    center = center,
  )
}

private fun DrawScope.drawHalo(
  palette: FloorPalette,
  tilt: HoloTiltState,
  u: CardUnit,
) {
  val center = Offset(size.width / 2f - tilt.offsetX * u.px(2.5f), u.px(36f) + tilt.offsetY * u.px(2.5f))
  val radius = u.px(31f)
  drawCircle(mix(palette.accent, Color(0xFF05161A), 0.22f), radius = radius, center = center)
  rotate(degrees = tilt.pointerX * 360f, pivot = center) {
    drawCircle(
      brush = Brush.sweepGradient(FoilColors, center),
      radius = radius + u.px(0.8f),
      center = center,
      style = Stroke(width = u.px(1.6f)),
    )
  }
}

/**
 * The art of the card: a transparent look cut-out standing over the halo, or an X-Ray picture filling the art
 * window, or a monogram in the halo when there is no image (or it fails to load).
 */
@Composable
private fun Art(
  name: String,
  image: String?,
  portraitOnly: Boolean,
  animate: Boolean,
  palette: FloorPalette,
  tilt: HoloTiltState,
  u: CardUnit,
) {
  val bounds = if (portraitOnly) {
    Modifier
      .cardRect(u, WindowLeft, WindowTop, WindowWidth, WindowHeight)
      .clip(RoundedCornerShape(u.dp(3f)))
  } else {
    Modifier
      .cardRect(u, FigureLeft, FigureTop, FigureWidth, FigureHeight)
      .graphicsLayer {
        translationX = tilt.offsetX * u.px(3.2f)
        translationY = -tilt.offsetY * u.px(2f)
        scaleX = 1.02f
        scaleY = 1.02f
      }
  }
  Box(modifier = bounds, contentAlignment = Alignment.BottomCenter) {
    Crossfade(targetState = image, animationSpec = if (animate) tween(400) else snap(), label = "art") { model ->
      if (model == null) {
        Monogram(name = name, palette = palette, u = u)
      } else {
        SubcomposeAsyncImage(
          model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(false).build(),
          contentDescription = null,
          contentScale = if (portraitOnly) ContentScale.Crop else ContentScale.Fit,
          alignment = if (portraitOnly) Alignment.TopCenter else Alignment.BottomCenter,
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              if (portraitOnly) {
                translationX = tilt.offsetX * u.px(3f)
                translationY = -tilt.offsetY * u.px(2f)
                scaleX = 1.06f
                scaleY = 1.06f
              }
            },
          error = { Monogram(name = name, palette = palette, u = u) },
        )
      }
    }
  }
}

@Composable
private fun Monogram(
  name: String,
  palette: FloorPalette,
  u: CardUnit,
) {
  Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(bottom = u.dp(7f))) {
    CardText(
      text = name.trim().take(1).uppercase(),
      size = u.sp(40f),
      color = palette.accent,
      fontFamily = DisplayFont,
      weight = FontWeight.Black,
    )
  }
}

@Composable
private fun Banner(
  name: String,
  epithet: String?,
  wide: Boolean,
  palette: FloorPalette,
  tilt: HoloTiltState,
  u: CardUnit,
) {
  Column(
    modifier = Modifier
      .cardRect(u, if (wide) 8f else 21f, 5f, if (wide) 84f else 58f, 12f)
      .clip(RoundedCornerShape(u.dp(2.2f)))
      .background(Brush.verticalGradient(listOf(Color(0xD9050E12), Color(0x99050E12))))
      .drawBehind {
        drawRoundRect(
          color = palette.accent.copy(alpha = 0.6f),
          cornerRadius = CornerRadius(u.px(2.2f)),
          style = Stroke(width = u.px(0.5f)),
        )
      }
      .padding(horizontal = u.dp(2f)),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CardText(
      text = name.uppercase(),
      size = u.sp(7.4f),
      minSize = u.sp(3.6f),
      color = Color.White,
      fontFamily = DisplayFont,
      weight = FontWeight.Black,
      letterSpacingEm = 0.08f,
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          val travel = size.width * 1.6f
          val startX = -travel * 0.3f + tilt.pointerX * travel * 0.6f
          drawRect(
            brush = Brush.linearGradient(
              colorStops = arrayOf(
                0f to Color.White,
                0.3f to Color.White,
                0.42f to Color(0xFFFFD9F1),
                0.52f to Color(0xFFB9F3FF),
                0.6f to Color(0xFFD8FFD0),
                0.72f to Color.White,
                1f to Color.White,
              ),
              start = Offset(startX, 0f),
              end = Offset(startX + travel, 0f),
            ),
            blendMode = BlendMode.SrcAtop,
          )
        },
    )
    if (epithet != null) {
      CardText(
        text = epithet.uppercase(),
        size = u.sp(2.9f),
        minSize = u.sp(1.8f),
        color = mix(palette.accent, Color.White, 0.8f),
        letterSpacingEm = 0.22f,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun Badge(
  label: String,
  value: String?,
  caption: String?,
  palette: FloorPalette,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(CircleShape)
      .background(Brush.radialGradient(listOf(Color(0xFF123E42), Color(0xFF071518)), center = Offset.Unspecified))
      .drawBehind {
        drawCircle(
          color = palette.accent,
          radius = size.minDimension / 2f - u.px(0.4f),
          style = Stroke(width = u.px(0.8f)),
        )
      }
      .padding(horizontal = u.dp(1.5f)),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CardText(text = label.uppercase(), size = u.sp(2.3f), minSize = u.sp(1.6f), color = InkDimColor, letterSpacingEm = 0.14f, lineHeightMultiple = 1f)
    CardText(
      text = value ?: "–",
      size = u.sp(8.4f),
      minSize = u.sp(4.5f),
      lineHeightMultiple = 1f,
      color = if (value == null) Color(0xFF3B4A52) else InkColor,
      fontFamily = DisplayFont,
      weight = FontWeight.Black,
    )
    if (caption != null) {
      CardText(text = caption, size = u.sp(2f), minSize = u.sp(1.4f), color = InkDimColor, lineHeightMultiple = 1f)
    }
  }
}

@Composable
private fun StatPanel(
  card: ComposedPlayerCard,
  palette: FloorPalette,
  animate: Boolean,
  u: CardUnit,
) {
  Box(
    modifier = Modifier
      .cardRect(u, 0f, PanelTopEdge - 5f, 100f, CardHeightUnits - PanelTopEdge + 5f)
      .drawBehind { drawPanelShape(u) },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = u.dp(6f), end = u.dp(6f), top = u.dp(6.2f)),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      CardText(
        text = card.race?.uppercase().orEmpty(),
        size = u.sp(3f),
        minSize = u.sp(2f),
        color = InkDimColor,
        letterSpacingEm = 0.14f,
        align = androidx.compose.ui.text.style.TextAlign.Start,
        modifier = Modifier.weight(1f),
      )
      card.crawlerId?.let {
        CardText(
          text = stringResource(R.string.holo_card_crawler_id, it).uppercase(),
          size = u.sp(3f),
          minSize = u.sp(2f),
          color = InkColor,
          letterSpacingEm = 0.1f,
          align = androidx.compose.ui.text.style.TextAlign.End,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = u.dp(3f))
        .padding(bottom = u.dp(3.2f)),
      horizontalArrangement = Arrangement.spacedBy(u.dp(1.2f)),
    ) {
      card.stats.forEach { stat ->
        StatHexagon(stat = stat, palette = palette, animate = animate, u = u, modifier = Modifier.weight(1f))
      }
    }
  }
}

private fun DrawScope.drawPanelShape(u: CardUnit) {
  val bump = u.px(5f)
  val tile = u.px(14f)
  val brush = Brush.verticalGradient(listOf(PanelTop, PanelBottom), startY = bump, endY = size.height)
  drawRect(brush = brush, topLeft = Offset(0f, bump), size = Size(size.width, size.height - bump))
  val tiles = ceil(size.width / tile).toInt()
  for (index in 0 until tiles) {
    drawArc(
      color = PanelTop,
      startAngle = 180f,
      sweepAngle = 180f,
      useCenter = true,
      topLeft = Offset(index * tile, 0f),
      size = Size(tile, bump * 2f),
    )
  }
}

@Composable
private fun StatHexagon(
  stat: ComposedPlayerCard.Stat,
  palette: FloorPalette,
  animate: Boolean,
  u: CardUnit,
  modifier: Modifier = Modifier,
) {
  val locked = stat.locked
  val pop = remember { Animatable(0f) }
  var shownValue by remember { mutableStateOf(stat.value) }
  LaunchedEffect(stat.value) {
    if (animate && stat.value != null && stat.value != shownValue) {
      pop.snapTo(1f)
      pop.animateTo(0f, tween(900))
    }
    shownValue = stat.value
  }
  Column(
    modifier = modifier
      .aspectRatio(1f / 1.1f)
      .graphicsLayer {
        val scale = 1f + 0.14f * sin(pop.value * PI.toFloat())
        scaleX = scale
        scaleY = scale
      }
      .drawBehind {
        drawHexagonFrame(palette, locked, u)
        if (pop.value > 0f) drawPath(hexagonPath(size.width, size.height), Color.White.copy(alpha = 0.45f * pop.value))
      },
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CardText(
      text = stat.stat.abbreviation,
      size = u.sp(2.4f),
      color = if (locked) InkDimColor.copy(alpha = 0.6f) else mix(palette.accent, Color.White, 0.85f),
      letterSpacingEm = 0.12f,
      lineHeightMultiple = 1f,
    )
    CardText(
      text = stat.value?.toString() ?: "–",
      size = u.sp(8f),
      minSize = u.sp(4.5f),
      color = if (locked) Color(0xFF3B4A52) else InkColor,
      fontFamily = DisplayFont,
      weight = FontWeight.Black,
      lineHeightMultiple = 1f,
      modifier = Modifier.padding(horizontal = u.dp(1.5f)),
    )
    CardText(
      text = stat.modifier.orEmpty().takeIf { !locked } ?: " ",
      size = u.sp(2.4f),
      minSize = u.sp(1.6f),
      color = PositiveColor,
      lineHeightMultiple = 1f,
    )
  }
}

private fun DrawScope.drawHexagonFrame(
  palette: FloorPalette,
  locked: Boolean,
  u: CardUnit,
) {
  val outer = hexagonPath(size.width, size.height)
  val borderBrush = if (locked) {
    Brush.verticalGradient(listOf(Color(0xFF3A464D), Color(0xFF232C31)))
  } else {
    Brush.verticalGradient(listOf(mix(palette.accent, Color.White, 0.7f), palette.accentDark))
  }
  drawPath(outer, borderBrush)
  drawPath(hexagonPath(size.width, size.height, inset = u.px(0.7f)), Color(0xFF08141A))
}

@Composable
private fun AboutPanel(
  race: String?,
  description: String,
  u: CardUnit,
) {
  Column(
    modifier = Modifier
      .cardRect(u, 0f, PanelTopEdge - 5f, 100f, CardHeightUnits - PanelTopEdge + 5f)
      .drawBehind { drawPanelShape(u) }
      .padding(start = u.dp(6f), end = u.dp(6f), top = u.dp(6.5f), bottom = u.dp(4f)),
    verticalArrangement = Arrangement.spacedBy(u.dp(1f)),
  ) {
    if (race != null) {
      CardText(
        text = race.uppercase(),
        size = u.sp(3f),
        minSize = u.sp(2f),
        color = InkDimColor,
        letterSpacingEm = 0.14f,
        align = androidx.compose.ui.text.style.TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    CardText(
      text = description,
      size = u.sp(3f),
      color = InkColor,
      weight = FontWeight.Normal,
      align = androidx.compose.ui.text.style.TextAlign.Start,
      maxLines = if (race != null) 5 else 6,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
