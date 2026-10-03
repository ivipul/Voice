package voice.features.playbackScreen.view.holocard

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import voice.core.strings.R
import voice.core.xray.card.composeAt
import voice.features.playbackScreen.BookPlayDialogViewState

private val MaxCardWidth = 380.dp
private const val ScrimAlpha = 0.78f
private const val FlipFrontDegrees = 0f
private const val FlipBackDegrees = 180f
private const val DismissDragDp = 90
private const val DismissFlickDpPerSecond = 1600
private const val TapTimeoutMillis = 500L

/**
 * Centered holo card over a dark scrim. Tap the card to flip it, drag to tilt it. Tap outside, press back or
 * swipe down to dismiss.
 */
@Composable
internal fun HoloCardDialog(
  dialogState: BookPlayDialogViewState.XRayCardDialog,
  positionMs: Long,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    val view = LocalView.current
    (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

    val reducedMotion = remember { animationsDisabled(view.context) }
    val density = LocalDensity.current
    val dismissDragPx = with(density) { DismissDragDp.dp.toPx() }
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = ScrimAlpha))
        .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
        .pointerInput(Unit) {
          var dragged = 0f
          detectVerticalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = { if (dragged > dismissDragPx) onDismiss() },
            onDragCancel = { dragged = 0f },
            onVerticalDrag = { _, dy -> dragged += dy },
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      val cardWidth = minOf(maxWidth * 0.86f, maxHeight * 0.74f * 5f / 7f, MaxCardWidth)
      HoloCard(
        dialogState = dialogState,
        positionMs = positionMs,
        width = cardWidth,
        reducedMotion = reducedMotion,
        onDismiss = onDismiss,
      )
    }
  }
}

@Composable
private fun HoloCard(
  dialogState: BookPlayDialogViewState.XRayCardDialog,
  positionMs: Long,
  width: Dp,
  reducedMotion: Boolean,
  onDismiss: () -> Unit,
) {
  val density = LocalDensity.current
  val u = remember(width, density) { CardUnit(with(density) { width.toPx() } / 100f, density) }
  val height = with(density) { u.dp(CardHeightUnits) }
  val tilt = remember(reducedMotion) { HoloTiltState(reducedMotion) }
  val flip = remember { Animatable(FlipFrontDegrees) }
  val appear = remember { Animatable(if (reducedMotion) 1f else 0f) }
  var showingBack by remember { mutableStateOf(false) }
  val card = remember(dialogState.data, positionMs) { dialogState.data.composeAt(positionMs) }
  val lookImage = card.look?.image?.let(dialogState.imageUris::get)

  DeviceTiltEffect(tilt = tilt, enabled = !reducedMotion)
  LaunchedEffect(tilt) {
    var lastNanos = 0L
    while (true) {
      withFrameNanos { now ->
        val delta = if (lastNanos == 0L) 0f else (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        tilt.step(timeSeconds = now / 1_000_000_000f, deltaSeconds = delta)
      }
    }
  }
  LaunchedEffect(showingBack) {
    flip.animateTo(
      targetValue = if (showingBack) FlipBackDegrees else FlipFrontDegrees,
      animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 140f),
    )
  }
  LaunchedEffect(Unit) {
    appear.animateTo(1f, if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
  }

  val description = stringResource(R.string.holo_card_description, dialogState.name)
  val flipLabel = stringResource(R.string.holo_card_flip)
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Box(
      modifier = Modifier
        .size(width, height)
        .semantics {
          contentDescription = description
          onClick(label = flipLabel) {
            showingBack = !showingBack
            true
          }
        }
        .cardGestures(
          tilt = tilt,
          onTap = { showingBack = !showingBack },
          onFlickDown = onDismiss,
        ),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            val scale = 0.9f + 0.1f * appear.value
            scaleX = scale
            scaleY = scale
            alpha = appear.value.coerceIn(0f, 1f)
            rotationX = tilt.rotationX
            rotationY = tilt.rotationY + flip.value
            cameraDistance = 14f * density.density
            compositingStrategy = CompositingStrategy.Offscreen
          },
      ) {
        val angle = ((flip.value % 360f) + 360f) % 360f
        if (angle < 90f || angle > 270f) {
          HoloCardFront(
            name = dialogState.name,
            description = dialogState.description,
            plain = dialogState.plain,
            card = card,
            lookImage = lookImage,
            portrait = dialogState.portrait?.toImageModel(),
            animate = !reducedMotion,
            tilt = tilt,
            u = u,
          )
        } else {
          HoloCardBack(
            card = card,
            description = dialogState.description,
            plain = dialogState.plain,
            tilt = tilt,
            u = u,
            modifier = Modifier.graphicsLayer { rotationY = 180f },
          )
        }
      }
    }
    Text(
      text = stringResource(if (showingBack) R.string.holo_card_hint_back else R.string.holo_card_hint_front),
      style = MaterialTheme.typography.labelMedium,
      color = Color.White.copy(alpha = 0.6f),
    )
  }
}

private fun Modifier.cardGestures(
  tilt: HoloTiltState,
  onTap: () -> Unit,
  onFlickDown: () -> Unit,
): Modifier = pointerInput(tilt) {
  val flickPx = DismissFlickDpPerSecond.dp.toPx()
  val dragPx = DismissDragDp.dp.toPx()
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    down.consume()
    val start = down.position
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    tilt.onPointer(down.position.x / size.width, down.position.y / size.height)
    var moved = false
    var lastUptime = down.uptimeMillis
    var last = start
    do {
      val event = awaitPointerEvent()
      val change = event.changes.firstOrNull() ?: break
      last = change.position
      lastUptime = change.uptimeMillis
      if ((last - start).getDistance() > viewConfiguration.touchSlop) moved = true
      tracker.addPosition(change.uptimeMillis, last)
      tilt.onPointer(last.x / size.width, last.y / size.height)
      change.consume()
    } while (event.changes.any { it.pressed })
    tilt.onRelease()
    when {
      !moved && lastUptime - down.uptimeMillis < TapTimeoutMillis -> onTap()
      last.y - start.y > dragPx && tracker.calculateVelocity().y > flickPx -> onFlickDown()
    }
  }
}

private fun animationsDisabled(context: Context): Boolean = try {
  Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) {
  false
}
