package voice.features.playbackScreen.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import voice.features.playbackScreen.ChatInputViewState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import voice.core.ui.crawl.CrawlPalette
import kotlin.math.abs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import voice.core.copilot.CoPilotMessage
import voice.core.data.ChapterId
import voice.core.playback.speech.SpokenMessage
import voice.core.strings.R
import voice.core.ui.FrameCardViewer
import voice.core.ui.formatTime
import voice.core.ui.icons.VoiceIcons
import voice.core.ui.rememberPlayIconPainter

private val FRAME_BUBBLE_MAX_WIDTH = 280.dp

/**
 * The System AI chat as a sheet over the player, built like the Inventory's: it slides up from the bottom edge
 * under a header with a handle bar. Dragging the header down, tapping it, tapping outside or going back closes it.
 */
@Composable
internal fun SystemAiChatSheet(
  visible: Boolean,
  palette: CrawlPalette,
  messages: List<CoPilotMessage>,
  isThinking: Boolean,
  input: ChatInputViewState,
  onInputChange: (String) -> Unit,
  onInputTap: () -> Unit,
  onSend: () -> Unit,
  onCancelAutoSend: () -> Unit,
  onMicClick: () -> Unit,
  spokenMessage: SpokenMessage?,
  onMessagePlayClick: (CoPilotMessage) -> Unit,
  onDismiss: () -> Unit,
  onSeekToSnip: (ChapterId, Long) -> Unit,
  snipLocationLabel: (ChapterId, Long) -> String?,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  var progress by remember { mutableFloatStateOf(0F) }
  var settling by remember { mutableStateOf<Job?>(null) }
  val scope = rememberCoroutineScope()

  fun settle(target: Float) {
    settling?.cancel()
    settling = scope.launch {
      animate(initialValue = progress, targetValue = target) { value, _ -> progress = value }
    }
  }
  LaunchedEffect(visible) { settle(if (visible) 1F else 0F) }
  val showSheet by remember { derivedStateOf { progress > 0F } }
  if (!visible && !showSheet) return
  BackHandler(enabled = visible, onBack = onDismiss)

  var viewedFrame by remember { mutableStateOf<CoPilotMessage?>(null) }
  viewedFrame?.imagePath?.let { path ->
    FrameCardViewer(
      imagePath = path,
      contentDescription = viewedFrame?.text.orEmpty(),
      onDismiss = { viewedFrame = null },
    )
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val sheetHeight = maxHeight - statusBar - SheetTopGap
    val rangePx = with(density) { sheetHeight.toPx() }.coerceAtLeast(1F)
    var dragStartProgress by remember { mutableFloatStateOf(1F) }
    val dragState = rememberDraggableState { delta ->
      progress = (progress - delta / rangePx).coerceIn(0F, 1F)
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = progress }
        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
        .clickable(interactionSource = null, indication = null, onClick = onDismiss),
    )
    Surface(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(sheetHeight)
        .graphicsLayer { translationY = (1F - progress) * rangePx },
      shape = SheetShape,
      color = palette.background,
      contentColor = palette.content,
    ) {
      Column {
        SheetHeader(
          icon = VoiceIcons.Mic,
          title = stringResource(id = R.string.copilot_feed_title),
          count = null,
          palette = palette,
          progress = { progress },
          modifier = Modifier
            .clickable(role = Role.Button, onClick = onDismiss)
            .draggable(
              state = dragState,
              orientation = Orientation.Vertical,
              onDragStarted = {
                settling?.cancel()
                dragStartProgress = progress
              },
              onDragStopped = { velocity ->
                val movedPx = (progress - dragStartProgress) * rangePx
                val downward = when {
                  abs(movedPx) > with(density) { SettleThreshold.toPx() } -> movedPx < 0F
                  else -> velocity > SETTLE_VELOCITY_PX
                }
                if (downward) onDismiss() else settle(1F)
              },
            ),
        )
        ChatMessages(
          messages = messages,
          isThinking = isThinking,
          spokenMessage = spokenMessage,
          onMessagePlayClick = onMessagePlayClick,
          onSeekToSnip = onSeekToSnip,
          snipLocationLabel = snipLocationLabel,
          onOpenFrame = { viewedFrame = it },
          modifier = Modifier
            .weight(1F)
            .fillMaxWidth(),
        )
        ChatInputBar(
          state = input,
          isThinking = isThinking,
          onTextChange = onInputChange,
          onInputTap = onInputTap,
          onSend = onSend,
          onCancelAutoSend = onCancelAutoSend,
          onMicClick = onMicClick,
        )
      }
    }
  }
}

@Composable
private fun ChatMessages(
  messages: List<CoPilotMessage>,
  isThinking: Boolean,
  spokenMessage: SpokenMessage?,
  onMessagePlayClick: (CoPilotMessage) -> Unit,
  onSeekToSnip: (ChapterId, Long) -> Unit,
  snipLocationLabel: (ChapterId, Long) -> String?,
  onOpenFrame: (CoPilotMessage) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (messages.isEmpty()) {
    Box(
      modifier = modifier.padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(id = R.string.copilot_feed_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
      if (messages.isNotEmpty()) {
        listState.animateScrollToItem(messages.lastIndex)
      }
    }
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = modifier,
    ) {
      items(messages, key = { it.id }) { message ->
        ChatBubble(
          message,
          spoken = spokenMessage?.takeIf { it.id == message.id },
          onPlayClick = { onMessagePlayClick(message) },
          onSeekToSnip = onSeekToSnip,
          snipLocationLabel = snipLocationLabel,
          onOpenFrame = { onOpenFrame(message) },
        )
      }
      if (isThinking) {
        item {
          ThinkingBubble()
        }
      }
    }
  }
}

@Composable
private fun ChatBubble(
  message: CoPilotMessage,
  spoken: SpokenMessage?,
  onPlayClick: () -> Unit,
  onSeekToSnip: (ChapterId, Long) -> Unit,
  snipLocationLabel: (ChapterId, Long) -> String?,
  onOpenFrame: () -> Unit,
) {
  val isUser = message.role == CoPilotMessage.Role.User
  val snipChapterId = message.snipChapterId
  val snipPositionInChapterMs = message.snipPositionInChapterMs
  val hasFrame = message.imagePath != null || message.isGeneratingImage
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      },
      modifier = Modifier
        // Never so wide that it pushes the play button off the row.
        .weight(1F, fill = false)
        .widthIn(max = if (hasFrame) FRAME_BUBBLE_MAX_WIDTH else 320.dp),
    ) {
      Column {
        if (hasFrame) {
          SnipFrameLoader(
            imagePath = message.imagePath,
            contentDescription = message.text,
            modifier = if (message.imagePath != null) {
              Modifier.clickable(onClickLabel = stringResource(id = R.string.copilot_feed_frame_open), onClick = onOpenFrame)
            } else {
              Modifier
            },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
          )
        }
        if (snipChapterId != null && snipPositionInChapterMs != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSeekToSnip(snipChapterId, snipPositionInChapterMs) }
              .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = VoiceIcons.Timelapse,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
              text = stringResource(
                id = R.string.copilot_feed_snip_annotation,
                snipLocationLabel(snipChapterId, snipPositionInChapterMs) ?: formatTime(snipPositionInChapterMs),
              ),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Text(
          text = message.text,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    if (!isUser) {
      Spacer(modifier = Modifier.size(8.dp))
      MessagePlayButton(spoken = spoken, onClick = onPlayClick)
    }
  }
}

/**
 * Reads the reply beside it aloud. It shows pause from the tap until the reading ends or is stopped, with a
 * ring around it while the voice is still being prepared.
 */
@Composable
private fun MessagePlayButton(
  spoken: SpokenMessage?,
  onClick: () -> Unit,
) {
  val playing = spoken != null
  Box(
    modifier = Modifier.size(40.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (spoken != null && !spoken.audible) {
      CircularProgressIndicator(
        modifier = Modifier.size(40.dp),
        strokeWidth = 2.dp,
      )
    }
    FilledTonalIconButton(
      onClick = onClick,
      modifier = Modifier.size(36.dp),
      colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      ),
    ) {
      Icon(
        painter = rememberPlayIconPainter(playing = playing),
        contentDescription = stringResource(
          id = if (playing) R.string.copilot_feed_message_stop else R.string.copilot_feed_message_play,
        ),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun ThinkingBubble() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = stringResource(id = R.string.copilot_feed_thinking))
      }
    }
  }
}

@Composable
private fun ChatInputBar(
  state: ChatInputViewState,
  isThinking: Boolean,
  onTextChange: (String) -> Unit,
  onInputTap: () -> Unit,
  onSend: () -> Unit,
  onCancelAutoSend: () -> Unit,
  onMicClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      if (interaction is PressInteraction.Press) onInputTap()
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .imePadding()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = state.text,
      onValueChange = onTextChange,
      modifier = Modifier
        .weight(1f),
      placeholder = {
        Text(
          text = stringResource(
            id = if (state.isListening) R.string.copilot_feed_listening else R.string.copilot_feed_input_placeholder,
          ),
        )
      },
      trailingIcon = {
        SendButton(
          autoSendPending = state.autoSendPending,
          enabled = !isThinking && state.text.isNotBlank(),
          onSend = onSend,
          onCancelAutoSend = onCancelAutoSend,
        )
      },
      maxLines = 4,
      enabled = !isThinking,
      shape = RoundedCornerShape(28.dp),
      interactionSource = interactionSource,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { onSend() }),
    )
    Spacer(modifier = Modifier.size(8.dp))
    MicButton(
      isListening = state.isListening,
      enabled = !isThinking,
      onClick = onMicClick,
    )
  }
}

/**
 * The up-arrow send button inside the text box. While a dictated question is about to send itself it
 * turns into an X with a ring that fills over the wait; tapping the X keeps the text and sends nothing.
 */
@Composable
private fun SendButton(
  autoSendPending: Boolean,
  enabled: Boolean,
  onSend: () -> Unit,
  onCancelAutoSend: () -> Unit,
) {
  Box(
    modifier = Modifier
      .padding(end = 4.dp)
      .size(40.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (autoSendPending) {
      val progress = remember { Animatable(0f) }
      LaunchedEffect(Unit) {
        progress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = ChatInputViewState.AUTO_SEND_DELAY_MS.toInt(), easing = LinearEasing),
        )
      }
      CircularProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.size(36.dp),
        strokeWidth = 3.dp,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
      )
      IconButton(onClick = onCancelAutoSend) {
        Icon(
          imageVector = VoiceIcons.Close,
          contentDescription = stringResource(id = R.string.copilot_feed_input_cancel_send),
          modifier = Modifier.size(18.dp),
        )
      }
    } else {
      FilledIconButton(
        enabled = enabled,
        onClick = onSend,
        modifier = Modifier.size(36.dp),
      ) {
        Icon(
          imageVector = VoiceIcons.Send,
          contentDescription = stringResource(id = R.string.copilot_feed_input_send),
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

/** Filled and pulsing while the microphone is on; a muted, struck-through mic once it has stopped. */
@Composable
private fun MicButton(
  isListening: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  Box(
    modifier = Modifier.size(48.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (isListening) {
      val pulse by rememberInfiniteTransition(label = "listening").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1_100), repeatMode = RepeatMode.Restart),
        label = "listeningPulse",
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .graphicsLayer {
            scaleX = 0.8f + 0.5f * pulse
            scaleY = 0.8f + 0.5f * pulse
            alpha = 0.45f * (1f - pulse)
          }
          .background(color = accent, shape = CircleShape),
      )
      FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
      ) {
        Icon(
          imageVector = VoiceIcons.Mic,
          contentDescription = stringResource(id = R.string.copilot_feed_mic_stop),
        )
      }
    } else {
      val strike = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.8f else 0.3f)
      FilledTonalIconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      ) {
        Icon(
          imageVector = VoiceIcons.Mic,
          contentDescription = stringResource(id = R.string.copilot_feed_mic_start),
          modifier = Modifier
            .alpha(0.6f)
            .drawWithContent {
              drawContent()
              drawLine(
                color = strike,
                start = Offset(size.width * 0.12f, size.height * 0.12f),
                end = Offset(size.width * 0.88f, size.height * 0.88f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
              )
            },
        )
      }
    }
  }
}
